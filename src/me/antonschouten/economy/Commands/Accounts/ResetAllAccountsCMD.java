package me.antonschouten.economy.Commands.Accounts;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class ResetAllAccountsCMD implements CommandExecutor {

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandUtils.requirePermission(player, Perms.resetBallAll)) {
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }

        if (!EconomyManager.resetAccounts.contains(player.getUniqueId())) {
            EconomyManager.resetAccounts.add(player.getUniqueId());
        }

        player.sendMessage(Main.prefix + "Are you sure you want to reset §call player accounts§f?");
        player.sendMessage(Main.prefix + "Type §ayes §fto confirm or §cno §fto cancel.");
        return true;
    }
}
