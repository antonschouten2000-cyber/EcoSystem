package me.antonschouten.economy.Commands.Teleport.Home;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.HomeManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class HomeListCMD implements CommandExecutor {

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandUtils.requirePermission(player, Perms.Home)) {
            return true;
        }
        if (!HomeManager.homesEnabled()) {
            player.sendMessage(Main.prefix + "Homes are currently disabled by the server.");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }

        List<String> homes = HomeManager.getHomes(player);
        if (homes.isEmpty()) {
            player.sendMessage(Main.prefix + "You don't have any homes yet.");
            return true;
        }

        player.sendMessage(Main.prefix + "Your homes (§a" + homes.size() + "§f): §a" + String.join("§f, §a", homes));
        return true;
    }
}
