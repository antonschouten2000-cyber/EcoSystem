package me.antonschouten.economy.Commands.Teleport.Spawn;

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

public final class SpawnCMD implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null || !CommandUtils.requirePermission(player, Perms.SPAWN)) {
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }
        if (!TeleportManager.isSpawnEnabled()) {
            player.sendMessage(Main.prefix + "Spawn teleporting is currently disabled.");
            return true;
        }
        Location spawn = TeleportStorage.getSpawn();
        if (spawn == null) {
            player.sendMessage(Main.prefix + "The server spawn has not been set yet.");
            return true;
        }
        TeleportManager.requestTeleport(player, spawn, TeleportReason.SPAWN, "spawn");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
