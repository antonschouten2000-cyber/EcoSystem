package me.antonschouten.economy.Commands.Teleport.Home;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.HomeData;
import me.antonschouten.economy.Manager.HomeManager;
import me.antonschouten.economy.Teleport.TeleportManager;
import me.antonschouten.economy.Teleport.TeleportReason;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class HomeCMD implements TabExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }
        if (!CommandUtils.requirePermission(player, Perms.HOME)) {
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

        String path = player.getUniqueId() + ".Homes." + actualName + ".W";
        String worldName = HomeData.getInstance().getData().getString(path);
        if (worldName == null || Bukkit.getWorld(worldName) == null) {
            player.sendMessage(Main.prefix + "This home's world no longer exists. Delete and recreate the home.");
            return true;
        }

        try {
            TeleportManager.requestTeleport(
                    player,
                    HomeManager.getHomeLocation(player, actualName),
                    TeleportReason.HOME,
                    "home " + actualName);
        } catch (RuntimeException exception) {
            Main.instance.getLogger().warning("Could not teleport " + player.getName() + " to home '" + actualName + "': "
                    + exception.getMessage());
            player.sendMessage(Main.prefix + "This home contains invalid location data. Please recreate it.");
        }
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
