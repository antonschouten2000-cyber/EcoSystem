package me.antonschouten.economy.Commands.Teleport.Home;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.HomeManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class SetHomeCMD implements TabExecutor {

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
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <home>§f.");
            return true;
        }

        String homeName = args[0];
        if (!CommandUtils.isSafeIdentifier(homeName, 32)) {
            player.sendMessage(Main.prefix + "Home names may only contain letters, numbers, §a_ §for §a-§f (max 32 characters).");
            return true;
        }

        List<String> homes = HomeManager.getHomes(player);
        boolean alreadyExists = homes.stream().anyMatch(home -> home.equalsIgnoreCase(homeName));
        if (alreadyExists) {
            player.sendMessage(Main.prefix + "You already have a home with this name.");
            return true;
        }

        int maxHomes = HomeManager.getMaxAmountOfHomes();
        if (maxHomes >= 0 && homes.size() >= maxHomes) {
            player.sendMessage(Main.prefix + "You have reached the maximum number of homes (§a" + maxHomes + "§f).");
            return true;
        }

        HomeManager.setHome(player, homeName);
        player.sendMessage(Main.prefix + "Home §a" + homeName + " §fhas been set.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
