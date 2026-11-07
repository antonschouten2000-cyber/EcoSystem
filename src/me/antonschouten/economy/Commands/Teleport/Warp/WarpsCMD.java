package me.antonschouten.economy.Commands.Teleport.Warp;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Teleport.TeleportManager;
import me.antonschouten.economy.Teleport.TeleportStorage;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class WarpsCMD implements TabExecutor {
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
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }
        List<String> warps = TeleportStorage.getWarpNames();
        if (warps.isEmpty()) {
            player.sendMessage(Main.prefix + "No warps have been created yet.");
        } else {
            player.sendMessage(Main.prefix + "Warps: §a" + String.join("§f, §a", warps) + "§f.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
