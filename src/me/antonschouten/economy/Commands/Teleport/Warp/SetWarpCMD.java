package me.antonschouten.economy.Commands.Teleport.Warp;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Teleport.TeleportStorage;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class SetWarpCMD implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null || !CommandUtils.requirePermission(player, Perms.WARP_ADMIN)) {
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <name>§f.");
            return true;
        }
        if (!TeleportStorage.validWarpName(args[0])) {
            player.sendMessage(Main.prefix + "Warp names may contain only letters, numbers, _ and - (max 32 characters).");
            return true;
        }
        TeleportStorage.setWarp(args[0], player.getLocation());
        player.sendMessage(Main.prefix + "Warp §a" + args[0] + " §fwas saved at your current location.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
