package me.antonschouten.economy.Commands.Shop;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.ShopManager;
import me.antonschouten.economy.Utils.CommandUtils;
import me.antonschouten.economy.Utils.Perms;

public final class ShopCMD implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("help", "add");

    @SuppressWarnings("deprecation")
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length == 0) {
            ShopManager.OpenShop(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            ShopManager.SendShopHelpMessage(player);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            if (!CommandUtils.requirePermission(player, Perms.ShopEdit)) {
                return true;
            }

            Integer price = CommandUtils.parsePositiveInt(player, args[1], "Price");
            if (price == null) {
                return true;
            }

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                player.sendMessage(Main.prefix + "Hold the item you want to add to the shop in your main hand.");
                return true;
            }

            if (ShopManager.shop.firstEmpty() == -1) {
                player.sendMessage(Main.prefix + "The shop is full. Remove an item before adding another one.");
                return true;
            }

            ShopManager.saveShop(player, price);
            return true;
        }

        ShopManager.SendShopHelpMessage(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], SUBCOMMANDS);
        }
        return List.of();
    }
}
