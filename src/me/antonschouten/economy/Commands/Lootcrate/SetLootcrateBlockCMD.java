package me.antonschouten.economy.Commands.Lootcrate;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class SetLootcrateBlockCMD implements CommandExecutor {

    public static final Set<Player> setCrateBlock = new HashSet<>();

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandUtils.requirePermission(player, Perms.lootcrateCreate)) {
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + "§f.");
            return true;
        }

        boolean newlyAdded = setCrateBlock.add(player);
        if (!newlyAdded) {
            player.sendMessage(Main.prefix + "You are already selecting a lootcrate block.");
            return true;
        }

        player.sendMessage(Main.prefix + "Right-click an §aEnd Portal Frame §fto set it as the lootcrate block.");
        return true;
    }
}
