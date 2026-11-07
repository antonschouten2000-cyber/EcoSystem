package me.antonschouten.economy.Listeners.Jobs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Manager.BoosterManager;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Manager.JobManager;
import me.antonschouten.economy.Manager.JobProgressionManager;

public final class JobListenerSupport {

    private static final long NOTICE_COOLDOWN_MS = 2_000L;
    private static final Map<String, Long> NOTICE_COOLDOWNS = new ConcurrentHashMap<>();

    private JobListenerSupport() {
    }

    public static boolean canEarn(Player player, String job) {
        if (player == null || job == null) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return false;
        }
        String currentJob = JobManager.getPlayerJob(player);
        return job.equalsIgnoreCase(currentJob);
    }

    public static int payReward(Player player, String rewardPath) {
        return payReward(player, rewardPath, 1);
    }

    public static int payReward(Player player, String rewardPath, int units) {
        if (player == null || rewardPath == null || rewardPath.isBlank() || units <= 0) {
            return 0;
        }

        int base = Math.max(0, ConfigData.getInstance().getData().getInt(rewardPath));
        if (base == 0) {
            return 0;
        }

        String job = JobManager.getPlayerJob(player);
        long boostedReward = (long) base * BoosterManager.getActiveMultiplier(player) * units;
        long requested = JobProgressionManager.applyRewardBonus(player, job, boostedReward);

        int current = EconomyManager.getBal(player);
        long room = (long) Integer.MAX_VALUE - current;
        int payout = room <= 0L ? 0 : (int) Math.min(requested, room);

        if (payout > 0) {
            EconomyManager.addBal(player, payout, TransactionReason.JOB_REWARD, player, "job=" + job + ";reward=" + rewardPath + ";units=" + units);
        }

        JobProgressionManager.recordActivity(player, job, base, units, payout);

        if (requested > room && Main.getInstance() != null) {
            Main.getInstance().getLogger().warning(
                    "Reward for " + player.getName() + " was capped because the balance reached Integer.MAX_VALUE.");
        }
        return payout;
    }

    public static void sendCooldownMessage(Player player, String key, String message) {
        if (player == null || key == null || message == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String cooldownKey = player.getUniqueId() + ":" + key;
        Long previous = NOTICE_COOLDOWNS.put(cooldownKey, now);
        if (previous == null || now - previous >= NOTICE_COOLDOWN_MS) {
            player.sendMessage(Main.prefix + message);
        }
    }

    public static int calculateCraftCount(CraftItemEvent event) {
        if (event == null || !(event.getWhoClicked() instanceof Player player)) {
            return 0;
        }
        if (!event.isShiftClick()) {
            return 1;
        }

        int maxCrafts = Integer.MAX_VALUE;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }
            maxCrafts = Math.min(maxCrafts, ingredient.getAmount());
        }
        if (maxCrafts == Integer.MAX_VALUE || maxCrafts <= 0) {
            return 1;
        }

        ItemStack result = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return 0;
        }

        int capacity = inventoryCapacity(player, result);
        int perCraft = Math.max(1, result.getAmount());
        int capacityCrafts = capacity / perCraft;
        return Math.max(0, Math.min(maxCrafts, capacityCrafts));
    }

    private static int inventoryCapacity(Player player, ItemStack item) {
        int capacity = 0;
        int maxStack = item.getMaxStackSize();
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                capacity += maxStack;
            } else if (slot.isSimilar(item)) {
                capacity += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
        }
        return capacity;
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        String prefix = uuid + ":";
        NOTICE_COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
