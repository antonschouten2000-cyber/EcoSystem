package me.antonschouten.economy.Commands.Teleport.Warp;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Teleport.TeleportManager;
import me.antonschouten.economy.Teleport.TeleportReason;
import me.antonschouten.economy.Teleport.TeleportStorage;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class WarpCMD implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null || !CommandUtils.requirePermission(player, Perms.WARP)) {
            return true;
        }
        if (!TeleportManager.isWarpsEnabled()) {
            player.sendMessage(Main.prefix + "Warps are currently disabled.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <warp>§f.");
            return true;
        }

        Location warp = TeleportStorage.getWarp(args[0]);
        String displayName = TeleportStorage.getWarpDisplayName(args[0]);
        if (warp == null || displayName == null) {
            player.sendMessage(Main.prefix + "Warp §a" + args[0] + " §fdoes not exist.");
            return true;
        }
        TeleportManager.requestTeleport(player, warp, TeleportReason.WARP, "warp " + displayName);
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
