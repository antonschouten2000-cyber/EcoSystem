package me.antonschouten.economy.Commands.Teleport.Tpa;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Teleport.TeleportManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class TpDenyCMD implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null || !CommandUtils.requirePermission(player, Perms.TPA)) {
            return true;
        }
        if (args.length > 1) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " [player]§f.");
            return true;
        }
        TeleportManager.denyRequest(player, args.length == 1 ? args[0] : null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return CommandUtils.complete(args[0], TeleportManager.incomingRequesterNames(player));
        }
        return List.of();
    }
}
