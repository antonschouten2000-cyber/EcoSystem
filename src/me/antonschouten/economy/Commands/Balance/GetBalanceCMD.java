package me.antonschouten.economy.Commands.Balance;

import java.util.List;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class GetBalanceCMD implements TabExecutor {

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Main.prefix + "Usage: §a/" + label + " <player>§f.");
                return true;
            }

            sender.sendMessage(Main.prefix + "Your current balance: §a$" + EconomyManager.getBal(player) + "§f.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Main.prefix + "Usage: §a/" + label + " [player]§f.");
            return true;
        }
        if (!CommandUtils.requirePermission(sender, Perms.getBalTarget)) {
            return true;
        }

        OfflinePlayer target = CommandUtils.findKnownPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Main.prefix + "Player §a" + args[0] + " §fwas not found.");
            return true;
        }

        sender.sendMessage(Main.prefix + "Current balance of §a" + CommandUtils.playerName(target) + " §fis §a$"
                + EconomyManager.getBal(target) + "§f.");
        return true;
    }

    @SuppressWarnings("deprecation")
	@Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(Perms.getBalTarget)) {
            return CommandUtils.complete(args[0], CommandUtils.onlinePlayerNames(null));
        }
        return List.of();
    }
}
