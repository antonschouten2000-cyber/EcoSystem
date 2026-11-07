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

public final class DelWarpCMD implements TabExecutor {
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
        String displayName = TeleportStorage.getWarpDisplayName(args[0]);
        if (displayName == null || !TeleportStorage.deleteWarp(args[0])) {
            player.sendMessage(Main.prefix + "Warp §a" + args[0] + " §fdoes not exist.");
            return true;
        }
        player.sendMessage(Main.prefix + "Warp §a" + displayName + " §fwas deleted.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], TeleportStorage.getWarpNames());
        }
        return List.of();
    }
}
