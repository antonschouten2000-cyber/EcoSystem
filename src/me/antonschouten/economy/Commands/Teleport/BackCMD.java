package me.antonschouten.economy.Commands.Teleport;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Teleport.TeleportManager;
import me.antonschouten.economy.Teleport.TeleportReason;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class BackCMD implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null || !CommandUtils.requirePermission(player, Perms.BACK)) {
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }
        if (!TeleportManager.isBackEnabled()) {
            player.sendMessage(Main.prefix + "/back is currently disabled.");
            return true;
        }
        Location back = TeleportManager.getBackLocation(player);
        if (back == null) {
            player.sendMessage(Main.prefix + "You do not have a previous location to return to.");
            return true;
        }
        TeleportManager.requestTeleport(player, back, TeleportReason.BACK, "your previous location");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
