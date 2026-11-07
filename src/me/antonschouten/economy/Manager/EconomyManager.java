package me.antonschouten.economy.Manager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.GuildData;
import me.antonschouten.economy.Data.PlayerData;
import me.antonschouten.economy.Transaction.TransactionManager;
import me.antonschouten.economy.Transaction.TransactionParty;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Transaction.TransactionRecord;

public final class EconomyManager {

    private static final PlayerData PLAYER_DATA = PlayerData.getInstance();
    private static final ConfigData CONFIG_DATA = ConfigData.getInstance();
    private static final GuildData GUILD_DATA = GuildData.getInstance();

    /** Thread-safe because confirmation is received from AsyncPlayerChatEvent. */
    public static final Set<UUID> resetAccounts = ConcurrentHashMap.newKeySet();

    private static final long SERVER_BANK_PAYOUT_INTERVAL_TICKS = 72_000L;
    private static BukkitTask serverBankTask;

    private EconomyManager() {
    }

    /* ---------------------------------------------------------
     * PLAYER BALANCES
     * --------------------------------------------------------- */

    public static void setBal(OfflinePlayer target, int amount) {
        setBal(target, amount, TransactionReason.SYSTEM_ADJUSTMENT, null, "legacy-setBal");
    }

    public static void setBal(OfflinePlayer target, int amount, TransactionReason reason,
            CommandSender actor, String context) {
        if (target == null || amount < 0) {
            return;
        }

        int before = getBal(target);
        setBalanceInMemory(target.getUniqueId(), amount);
        DataSaveManager.markPlayerDataDirty();
        logPlayerAdjustment(target, before, amount, reason, actor, context);
    }

    public static void addBal(OfflinePlayer target, int amount) {
        addBal(target, amount, TransactionReason.SYSTEM_ADJUSTMENT, null, "legacy-addBal");
    }

    public static void addBal(OfflinePlayer target, int amount, TransactionReason reason,
            CommandSender actor, String context) {
        if (target == null || amount <= 0) {
            return;
        }

        int before = getBal(target);
        int after = safeAdd(before, amount);
        setBalanceInMemory(target.getUniqueId(), after);
        DataSaveManager.markPlayerDataDirty();

        record(reason, amount,
                TransactionParty.system(sourceName(reason)),
                playerParty(target, before, after),
                actor, context);
    }

    /**
     * Removes money only when the account has enough funds. This method keeps
     * the historic void signature; use {@link #tryWithdraw(OfflinePlayer, int)}
     * when the caller needs to know whether the withdrawal succeeded.
     */
    public static void removeBal(OfflinePlayer target, int amount) {
        tryWithdraw(target, amount, TransactionReason.SYSTEM_ADJUSTMENT, null, "legacy-removeBal");
    }

    public static boolean tryWithdraw(OfflinePlayer target, int amount) {
        return tryWithdraw(target, amount, TransactionReason.SYSTEM_ADJUSTMENT, null, "legacy-withdraw");
    }

    public static boolean tryWithdraw(OfflinePlayer target, int amount, TransactionReason reason,
            CommandSender actor, String context) {
        if (target == null || amount <= 0) {
            return false;
        }

        int before = getBal(target);
        if (before < amount) {
            return false;
        }

        int after = before - amount;
        setBalanceInMemory(target.getUniqueId(), after);
        DataSaveManager.markPlayerDataDirty();

        record(reason, amount,
                playerParty(target, before, after),
                TransactionParty.system(destinationName(reason)),
                actor, context);
        return true;
    }

    public static int getBal(OfflinePlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.max(0, PLAYER_DATA.getData().getInt(balancePath(player.getUniqueId())));
    }

    public static boolean payBal(Player sender, Player receiver, int amount) {
        if (sender == null || receiver == null || amount <= 0) {
            return false;
        }
        if (sender.getUniqueId().equals(receiver.getUniqueId())) {
            return false;
        }

        int senderBefore = getBal(sender);
        if (senderBefore < amount) {
            return false;
        }

        int receiverBefore = getBal(receiver);
        int receiverAfter = safeAdd(receiverBefore, amount);
        int senderAfter = senderBefore - amount;

        setBalanceInMemory(sender.getUniqueId(), senderAfter);
        setBalanceInMemory(receiver.getUniqueId(), receiverAfter);
        DataSaveManager.markPlayerDataDirty();

        record(TransactionReason.PLAYER_PAYMENT, amount,
                playerParty(sender, senderBefore, senderAfter),
                playerParty(receiver, receiverBefore, receiverAfter),
                sender, "pay");
        return true;
    }

    public static boolean payAll(Player sender, Collection<? extends Player> recipients, int amount) {
        if (sender == null || recipients == null || amount <= 0) {
            return false;
        }

        Map<UUID, Player> uniqueRecipients = new LinkedHashMap<>();
        for (Player recipient : recipients) {
            if (recipient != null && !recipient.getUniqueId().equals(sender.getUniqueId())) {
                uniqueRecipients.put(recipient.getUniqueId(), recipient);
            }
        }
        if (uniqueRecipients.isEmpty()) {
            return false;
        }

        long totalCost = (long) amount * uniqueRecipients.size();
        int senderBefore = getBal(sender);
        if (totalCost > Integer.MAX_VALUE || senderBefore < totalCost) {
            return false;
        }

        Map<UUID, Integer> recipientBefore = new LinkedHashMap<>();
        Map<UUID, Integer> recipientAfter = new LinkedHashMap<>();
        for (Player recipient : uniqueRecipients.values()) {
            int before = getBal(recipient);
            recipientBefore.put(recipient.getUniqueId(), before);
            recipientAfter.put(recipient.getUniqueId(), safeAdd(before, amount));
        }

        int senderAfter = (int) (senderBefore - totalCost);
        setBalanceInMemory(sender.getUniqueId(), senderAfter);
        recipientAfter.forEach(EconomyManager::setBalanceInMemory);
        DataSaveManager.markPlayerDataDirty();

        int runningSenderBalance = senderBefore;
        for (Player recipient : uniqueRecipients.values()) {
            int nextSenderBalance = runningSenderBalance - amount;
            record(TransactionReason.PAY_ALL, amount,
                    playerParty(sender, runningSenderBalance, nextSenderBalance),
                    playerParty(recipient,
                            recipientBefore.get(recipient.getUniqueId()),
                            recipientAfter.get(recipient.getUniqueId())),
                    sender, "payall");
            runningSenderBalance = nextSenderBalance;
        }
        return true;
    }

    public static void resetBal(OfflinePlayer target) {
        resetBal(target, null, "reset-balance");
    }

    public static void resetBal(OfflinePlayer target, CommandSender actor, String context) {
        if (target == null) {
            return;
        }
        int before = getBal(target);
        setBalanceInMemory(target.getUniqueId(), 0);
        DataSaveManager.markPlayerDataDirty();
        record(TransactionReason.ADMIN_RESET, before,
                playerParty(target, before, 0),
                TransactionParty.system("Admin reset"),
                actor, context);
    }

    public static void resetAllAccounts() {
        resetAllAccounts(null);
    }

    @SuppressWarnings("deprecation")
    public static void resetAllAccounts(CommandSender actor) {
        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> resetAllAccounts(actor));
            }
            return;
        }

        ConfigurationSection playersSection = PLAYER_DATA.getData().getConfigurationSection("Players");
        if (playersSection != null) {
            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    int before = Math.max(0, PLAYER_DATA.getData().getInt(balancePath(uuid)));
                    PLAYER_DATA.getData().set(balancePath(uuid), 0);
                    if (before > 0) {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
                        record(TransactionReason.ADMIN_RESET_ALL, before,
                                playerParty(target, before, 0),
                                TransactionParty.system("Admin reset all"),
                                actor, "reset-all-accounts");
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed legacy UUID nodes.
                }
            }
        }
        DataSaveManager.markPlayerDataDirty();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendTitle("§c>> §fEcoSystem §c<<", "§aYour account has been reset by an admin.");
        }
    }

    /* ---------------------------------------------------------
     * CHEQUES
     * --------------------------------------------------------- */

    public static void CreateCheque(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        ItemStack cheque = new ItemStack(Material.PAPER);
        ItemMeta meta = cheque.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName("§a$" + amount);
        meta.setLore(Arrays.asList(
                "§f– §3Click to add the cheque to your account",
                "§f– §3Worth: §b$" + amount,
                "§f– §4Warning: §cYou can use the cheque only 1 time."
        ));
        cheque.setItemMeta(meta);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(cheque);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    /* ---------------------------------------------------------
     * CONFIG / PRICES
     * --------------------------------------------------------- */

    public static int getLootcratePrice() {
        return Math.max(0, CONFIG_DATA.getData().getInt("Settings.Lootcrate"));
    }

    /* ---------------------------------------------------------
     * SERVER BANK
     * --------------------------------------------------------- */

    public static int getServerbankBalance() {
        return Math.max(0, CONFIG_DATA.getData().getInt("ServerbankBalance"));
    }

    public static void setServerbankBalance(int amount) {
        int before = getServerbankBalance();
        int after = Math.max(0, amount);
        CONFIG_DATA.getData().set("ServerbankBalance", after);
        DataSaveManager.markConfigDataDirty();
        logServerBankAdjustment(before, after, TransactionReason.SYSTEM_ADJUSTMENT, null, "set-server-bank");
    }

    public static void addBalanceToServerbank(int amount) {
        addBalanceToServerbank(amount, TransactionReason.SYSTEM_ADJUSTMENT, null, "add-server-bank");
    }

    public static void addBalanceToServerbank(int amount, TransactionReason reason,
            CommandSender actor, String context) {
        if (amount <= 0) {
            return;
        }
        int before = getServerbankBalance();
        int after = safeAdd(before, amount);
        CONFIG_DATA.getData().set("ServerbankBalance", after);
        DataSaveManager.markConfigDataDirty();
        record(reason, amount,
                TransactionParty.system(sourceName(reason)),
                TransactionParty.serverBank(before, after),
                actor, context);
    }

    public static boolean removeBalanceFromServerbank(int amount) {
        return removeBalanceFromServerbank(amount, TransactionReason.SYSTEM_ADJUSTMENT, null, "remove-server-bank");
    }

    public static boolean removeBalanceFromServerbank(int amount, TransactionReason reason,
            CommandSender actor, String context) {
        int before = getServerbankBalance();
        if (amount <= 0 || before < amount) {
            return false;
        }
        int after = before - amount;
        CONFIG_DATA.getData().set("ServerbankBalance", after);
        DataSaveManager.markConfigDataDirty();
        record(reason, amount,
                TransactionParty.serverBank(before, after),
                TransactionParty.system(destinationName(reason)),
                actor, context);
        return true;
    }

    /** Performs an atomic player -> server-bank transfer and creates one audit record. */
    public static boolean transferToServerBank(OfflinePlayer player, int amount, TransactionReason reason,
            CommandSender actor, String context) {
        if (player == null || amount <= 0) {
            return false;
        }
        int playerBefore = getBal(player);
        int bankBefore = getServerbankBalance();
        if (playerBefore < amount) {
            return false;
        }
        int bankAfter = safeAdd(bankBefore, amount);
        int playerAfter = playerBefore - amount;

        setBalanceInMemory(player.getUniqueId(), playerAfter);
        CONFIG_DATA.getData().set("ServerbankBalance", bankAfter);
        DataSaveManager.markPlayerDataDirty();
        DataSaveManager.markConfigDataDirty();

        record(reason, amount,
                playerParty(player, playerBefore, playerAfter),
                TransactionParty.serverBank(bankBefore, bankAfter),
                actor, context);
        return true;
    }

    public static void giveServerbankBalanceToPlayer() {
        stopServerBankTask();

        Main plugin = Main.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        serverBankTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                EconomyManager::payOutServerBank,
                SERVER_BANK_PAYOUT_INTERVAL_TICKS,
                SERVER_BANK_PAYOUT_INTERVAL_TICKS
        );
    }

    private static void payOutServerBank() {
        int bankBefore = getServerbankBalance();
        if (bankBefore <= 0) {
            return;
        }

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            return;
        }

        Player luckyPlayer = onlinePlayers.get(ThreadLocalRandom.current().nextInt(onlinePlayers.size()));
        int playerBefore = getBal(luckyPlayer);
        int playerAfter;
        try {
            playerAfter = safeAdd(playerBefore, bankBefore);
        } catch (ArithmeticException exception) {
            if (Main.getInstance() != null) {
                Main.getInstance().getLogger().warning("Server-bank payout skipped because the selected account would overflow.");
            }
            return;
        }

        setBalanceInMemory(luckyPlayer.getUniqueId(), playerAfter);
        CONFIG_DATA.getData().set("ServerbankBalance", 0);
        DataSaveManager.markPlayerDataDirty();
        DataSaveManager.markConfigDataDirty();

        record(TransactionReason.SERVER_BANK_PAYOUT, bankBefore,
                TransactionParty.serverBank(bankBefore, 0),
                playerParty(luckyPlayer, playerBefore, playerAfter),
                null, "scheduled-server-bank-payout");

        luckyPlayer.sendMessage(Main.prefix + "You were chosen to receive §a$" + bankBefore + " §ffrom the server bank.");
        Bukkit.broadcastMessage(Main.prefix + "§a" + luckyPlayer.getName() + " §2has received §a$" + bankBefore + " §2from the server bank.");
    }

    /* ---------------------------------------------------------
     * BALTOP
     * --------------------------------------------------------- */

    public static List<UUID> getTopBalances(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        ConfigurationSection playersSection = PLAYER_DATA.getData().getConfigurationSection("Players");
        if (playersSection == null) {
            return Collections.emptyList();
        }

        List<UUID> uuids = new ArrayList<>();
        for (String key : playersSection.getKeys(false)) {
            try {
                uuids.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed records instead of failing /baltop.
            }
        }

        uuids.sort((a, b) -> Integer.compare(
                PLAYER_DATA.getData().getInt(balancePath(b)),
                PLAYER_DATA.getData().getInt(balancePath(a))
        ));

        return new ArrayList<>(uuids.subList(0, Math.min(limit, uuids.size())));
    }

    /* ---------------------------------------------------------
     * GUILD BALANCES
     * --------------------------------------------------------- */

    public static void setGuildBalance(String guild, int amount) {
        if (!validGuild(guild) || amount < 0) {
            return;
        }
        int before = getGuildBalance(guild);
        GUILD_DATA.getData().set(guildBalancePath(guild), amount);
        DataSaveManager.markGuildDataDirty();
        int delta = Math.abs(amount - before);
        if (amount >= before) {
            record(TransactionReason.SYSTEM_ADJUSTMENT, delta,
                    TransactionParty.system("System"),
                    TransactionParty.guild(guild, before, amount),
                    null, "set-guild-balance");
        } else {
            record(TransactionReason.SYSTEM_ADJUSTMENT, delta,
                    TransactionParty.guild(guild, before, amount),
                    TransactionParty.system("System"),
                    null, "set-guild-balance");
        }
    }

    public static void addGuildBalance(String guild, int amount) {
        if (!validGuild(guild) || amount <= 0) {
            return;
        }
        int before = getGuildBalance(guild);
        int after = safeAdd(before, amount);
        GUILD_DATA.getData().set(guildBalancePath(guild), after);
        DataSaveManager.markGuildDataDirty();
        record(TransactionReason.SYSTEM_ADJUSTMENT, amount,
                TransactionParty.system("System"),
                TransactionParty.guild(guild, before, after),
                null, "add-guild-balance");
    }

    public static boolean removeGuildBalance(String guild, int amount) {
        if (!validGuild(guild) || amount <= 0) {
            return false;
        }
        int before = getGuildBalance(guild);
        if (before < amount) {
            return false;
        }
        int after = before - amount;
        GUILD_DATA.getData().set(guildBalancePath(guild), after);
        DataSaveManager.markGuildDataDirty();
        record(TransactionReason.SYSTEM_ADJUSTMENT, amount,
                TransactionParty.guild(guild, before, after),
                TransactionParty.system("System"),
                null, "remove-guild-balance");
        return true;
    }

    public static int getGuildBalance(String guild) {
        if (!validGuild(guild)) {
            return 0;
        }
        return Math.max(0, GUILD_DATA.getData().getInt(guildBalancePath(guild)));
    }

    /** Performs a player -> guild transfer with all validation before mutation. */
    public static boolean depositToGuild(OfflinePlayer player, String guild, int amount) {
        if (player == null || !validGuild(guild) || amount <= 0) {
            return false;
        }

        int playerBefore = getBal(player);
        if (playerBefore < amount) {
            return false;
        }
        int guildBefore = getGuildBalance(guild);
        int guildAfter = safeAdd(guildBefore, amount);
        int playerAfter = playerBefore - amount;

        setBalanceInMemory(player.getUniqueId(), playerAfter);
        GUILD_DATA.getData().set(guildBalancePath(guild), guildAfter);
        DataSaveManager.markPlayerDataDirty();
        DataSaveManager.markGuildDataDirty();

        record(TransactionReason.GUILD_DEPOSIT, amount,
                playerParty(player, playerBefore, playerAfter),
                TransactionParty.guild(guild, guildBefore, guildAfter),
                player instanceof CommandSender sender ? sender : null,
                "guild=" + guild);
        return true;
    }

    /** Performs a guild -> player transfer with all validation before mutation. */
    public static boolean withdrawFromGuild(String guild, OfflinePlayer player, int amount) {
        if (player == null || !validGuild(guild) || amount <= 0) {
            return false;
        }

        int guildBefore = getGuildBalance(guild);
        if (guildBefore < amount) {
            return false;
        }
        int playerBefore = getBal(player);
        int playerAfter = safeAdd(playerBefore, amount);
        int guildAfter = guildBefore - amount;

        GUILD_DATA.getData().set(guildBalancePath(guild), guildAfter);
        setBalanceInMemory(player.getUniqueId(), playerAfter);
        DataSaveManager.markGuildDataDirty();
        DataSaveManager.markPlayerDataDirty();

        record(TransactionReason.GUILD_WITHDRAW, amount,
                TransactionParty.guild(guild, guildBefore, guildAfter),
                playerParty(player, playerBefore, playerAfter),
                player instanceof CommandSender sender ? sender : null,
                "guild=" + guild);
        return true;
    }

    public static void shutdown() {
        stopServerBankTask();
    }

    private static void stopServerBankTask() {
        if (serverBankTask != null) {
            serverBankTask.cancel();
            serverBankTask = null;
        }
    }

    private static void setBalanceInMemory(UUID uuid, int amount) {
        PLAYER_DATA.getData().set(balancePath(uuid), Math.max(0, amount));
    }

    private static TransactionParty playerParty(OfflinePlayer player, int before, int after) {
        String name = player.getName();
        if (name == null || name.isBlank()) {
            name = player.getUniqueId().toString();
        }
        return TransactionParty.player(player.getUniqueId(), name, before, after);
    }

    private static void logPlayerAdjustment(OfflinePlayer target, int before, int after,
            TransactionReason reason, CommandSender actor, String context) {
        int delta = Math.abs(after - before);
        if (after >= before) {
            record(reason, delta,
                    TransactionParty.system(sourceName(reason)),
                    playerParty(target, before, after),
                    actor, context);
        } else {
            record(reason, delta,
                    playerParty(target, before, after),
                    TransactionParty.system(destinationName(reason)),
                    actor, context);
        }
    }

    private static void logServerBankAdjustment(int before, int after,
            TransactionReason reason, CommandSender actor, String context) {
        int delta = Math.abs(after - before);
        if (after >= before) {
            record(reason, delta,
                    TransactionParty.system(sourceName(reason)),
                    TransactionParty.serverBank(before, after),
                    actor, context);
        } else {
            record(reason, delta,
                    TransactionParty.serverBank(before, after),
                    TransactionParty.system(destinationName(reason)),
                    actor, context);
        }
    }

    private static void record(TransactionReason reason, int amount, TransactionParty from,
            TransactionParty to, CommandSender actor, String context) {
        String actorType = "SYSTEM";
        String actorId = "system";
        String actorName = "System";
        if (actor instanceof Player player) {
            actorType = "PLAYER";
            actorId = player.getUniqueId().toString();
            actorName = player.getName();
        } else if (actor != null) {
            actorType = "CONSOLE";
            actorId = "console";
            actorName = actor.getName();
        }

        TransactionManager.record(TransactionRecord.create(
                reason == null ? TransactionReason.SYSTEM_ADJUSTMENT : reason,
                Math.max(0, amount),
                from,
                to,
                actorType,
                actorId,
                actorName,
                context));
    }

    private static String sourceName(TransactionReason reason) {
        if (reason == null) {
            return "System";
        }
        return switch (reason) {
            case JOB_REWARD -> "Jobs";
            case SALARY -> "Salary";
            case FIRST_JOIN_REWARD, JOIN_REWARD -> "Join rewards";
            case CHEQUE_REDEEM -> "Cheque";
            default -> "System";
        };
    }

    private static String destinationName(TransactionReason reason) {
        if (reason == null) {
            return "System";
        }
        return switch (reason) {
            case SHOP_PURCHASE -> "Server Shop";
            case LOOTCRATE_PURCHASE -> "Lootcrates";
            case BOOSTER_PURCHASE -> "Job Boosters";
            case CHUNK_CLAIM -> "Chunk Claims";
            case REPAIR -> "Repair";
            case JOB_CHANGE -> "Job Change";
            case CHEQUE_CREATE -> "Cheque";
            default -> "System";
        };
    }

    private static String balancePath(UUID uuid) {
        return "Players." + uuid + ".Bal";
    }

    private static String guildBalancePath(String guild) {
        return "Guild." + guild + ".Balance";
    }

    private static boolean validGuild(String guild) {
        return guild != null && !guild.isBlank();
    }

    private static int safeAdd(int current, int amount) {
        long result = (long) current + amount;
        if (result > Integer.MAX_VALUE || result < 0) {
            throw new ArithmeticException("Economy balance overflow");
        }
        return (int) result;
    }
}
