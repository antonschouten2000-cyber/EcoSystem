package me.antonschouten.economy.Commands.Guild;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.GuildData;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Manager.GuildManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class GuildCMD implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "delete", "invite", "accept", "kick", "info", "balance", "deposit", "withdraw");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            GuildManager.guildHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "kick" -> handleKick(player, args);
            case "info" -> handleInfo(player, args);
            case "balance" -> handleBalance(player, args);
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            default -> {
                player.sendMessage(Main.prefix + "Unknown subcommand. Use §a/guild help §ffor help.");
                return true;
            }
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/guild create <name>§f.");
            return;
        }
        if (GuildManager.playerHasGuild(player.getName())) {
            player.sendMessage(Main.prefix + "You are already in a guild.");
            return;
        }

        String guildName = args[1];
        if (!CommandUtils.isSafeIdentifier(guildName, 24)) {
            player.sendMessage(Main.prefix + "Guild names may only contain letters, numbers, §a_ §for §a-§f (max 24 characters).");
            return;
        }
        if (findGuildName(guildName) != null) {
            player.sendMessage(Main.prefix + "A guild with this name already exists.");
            return;
        }

        GuildManager.createGuild(guildName, player.getName());
        player.sendMessage(Main.prefix + "You created the guild §a" + guildName + "§f.");
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/guild delete§f.");
            return;
        }

        if (!GuildManager.deleteGuild(player.getName())) {
            player.sendMessage(Main.prefix + "Only a guild leader can delete their guild.");
            return;
        }

        player.sendMessage(Main.prefix + "Your guild has been deleted.");
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/guild invite <player>§f.");
            return;
        }

        String guildName = GuildManager.getGuilNamedByLeader(player.getName());
        if (guildName == null) {
            player.sendMessage(Main.prefix + "Only a guild leader can invite players.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Main.prefix + "Player §a" + args[1] + " §fis not online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Main.prefix + "You can't invite yourself.");
            return;
        }
        if (GuildManager.playerHasGuild(target.getName())) {
            player.sendMessage(Main.prefix + "§a" + target.getName() + " §fis already in a guild.");
            return;
        }

        if (!GuildManager.invitePlayer(guildName, target.getName())) {
            player.sendMessage(Main.prefix + "This player already has a pending guild invite.");
            return;
        }

        player.sendMessage(Main.prefix + "You invited §a" + target.getName() + " §fto your guild.");
        target.sendMessage(Main.prefix + "You have been invited to §a" + guildName + "§f.");
        target.sendMessage(Main.prefix + "Use §a/guild accept §fto join.");
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/guild accept§f.");
            return;
        }
        if (GuildManager.playerHasGuild(player.getName())) {
            player.sendMessage(Main.prefix + "You are already in a guild.");
            return;
        }

        if (!GuildManager.acceptGuildInvite(player.getName())) {
            player.sendMessage(Main.prefix + "You don't have a pending guild invite.");
            return;
        }

        player.sendMessage(Main.prefix + "You joined the guild.");
    }

    private void handleKick(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/guild kick <player>§f.");
            return;
        }
        if (!GuildManager.isGuildLeader(player)) {
            player.sendMessage(Main.prefix + "Only the guild leader can kick players.");
            return;
        }
        if (player.getName().equalsIgnoreCase(args[1])) {
            player.sendMessage(Main.prefix + "You can't kick yourself. Use §a/guild delete §fto delete the guild.");
            return;
        }

        if (!GuildManager.kickMember(player.getName(), args[1])) {
            player.sendMessage(Main.prefix + "This player isn't a member of your guild.");
            return;
        }

        player.sendMessage(Main.prefix + "You kicked §a" + args[1] + " §ffrom the guild.");
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target != null) {
            target.sendMessage(Main.prefix + "You have been removed from your guild by its leader.");
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/guild info <name>§f.");
            return;
        }

        String guildName = findGuildName(args[1]);
        if (guildName == null) {
            player.sendMessage(Main.prefix + "This guild doesn't exist.");
            return;
        }

        GuildManager.guildInformation(player, guildName);
    }

    private void handleBalance(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/guild balance§f.");
            return;
        }

        String guildName = GuildManager.getPlayerGuild(player.getName());
        if (guildName == null) {
            player.sendMessage(Main.prefix + "You're not in a guild.");
            return;
        }

        player.sendMessage(Main.prefix + "Your guild balance is §a$" + EconomyManager.getGuildBalance(guildName) + "§f.");
    }

    private void handleDeposit(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/guild deposit <amount>§f.");
            return;
        }

        String guildName = GuildManager.getPlayerGuild(player.getName());
        if (guildName == null) {
            player.sendMessage(Main.prefix + "You're not in a guild.");
            return;
        }

        Integer amount = CommandUtils.parsePositiveInt(player, args[1], "Amount");
        if (amount == null) {
            return;
        }
        if (EconomyManager.getBal(player) < amount) {
            player.sendMessage(Main.prefix + "You don't have enough money.");
            return;
        }
        if ((long) EconomyManager.getGuildBalance(guildName) + amount > Integer.MAX_VALUE) {
            player.sendMessage(Main.prefix + "The guild balance is too high to accept this deposit.");
            return;
        }

        if (!EconomyManager.depositToGuild(player, guildName, amount)) {
            player.sendMessage(Main.prefix + "The deposit could not be completed.");
            return;
        }
        player.sendMessage(Main.prefix + "You deposited §a$" + amount + " §finto the guild balance.");
    }

    private void handleWithdraw(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/guild withdraw <amount>§f.");
            return;
        }

        String guildName = GuildManager.getPlayerGuild(player.getName());
        if (guildName == null) {
            player.sendMessage(Main.prefix + "You're not in a guild.");
            return;
        }
        if (!GuildManager.isGuildLeader(player)) {
            player.sendMessage(Main.prefix + "Only the guild leader can withdraw money.");
            return;
        }

        Integer amount = CommandUtils.parsePositiveInt(player, args[1], "Amount");
        if (amount == null) {
            return;
        }
        if (EconomyManager.getGuildBalance(guildName) < amount) {
            player.sendMessage(Main.prefix + "The guild doesn't have enough money.");
            return;
        }
        if ((long) EconomyManager.getBal(player) + amount > Integer.MAX_VALUE) {
            player.sendMessage(Main.prefix + "Your balance is too high to receive this amount.");
            return;
        }

        if (!EconomyManager.withdrawFromGuild(guildName, player, amount)) {
            player.sendMessage(Main.prefix + "The withdrawal could not be completed.");
            return;
        }
        player.sendMessage(Main.prefix + "You withdrew §a$" + amount + " §ffrom the guild balance.");
    }

    private String findGuildName(String input) {
        ConfigurationSection section = GuildData.getInstance().getData().getConfigurationSection("Guild");
        if (section == null) {
            return null;
        }

        return section.getKeys(false).stream()
                .filter(name -> name.equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }

    private List<String> guildNames() {
        ConfigurationSection section = GuildData.getInstance().getData().getConfigurationSection("Guild");
        if (section == null) {
            return List.of();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], SUBCOMMANDS);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick")) {
                return CommandUtils.complete(args[1], CommandUtils.onlinePlayerNames(sender instanceof Player p ? p : null));
            }
            if (args[0].equalsIgnoreCase("info")) {
                return CommandUtils.complete(args[1], guildNames());
            }
        }
        return List.of();
    }
}
