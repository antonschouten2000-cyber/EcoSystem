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

public final class DelHomeCMD implements TabExecutor {

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

        String requested = args[0];
        String actualName = HomeManager.getHomes(player).stream()
                .filter(home -> home.equalsIgnoreCase(requested))
                .findFirst()
                .orElse(null);

        if (actualName == null) {
            player.sendMessage(Main.prefix + "You don't have a home with this name.");
            return true;
        }

        HomeManager.deleteHome(player, actualName);
        player.sendMessage(Main.prefix + "Home §a" + actualName + " §fhas been removed.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return CommandUtils.complete(args[0], HomeManager.getHomes(player));
        }
        return List.of();
    }
}
