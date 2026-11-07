package me.antonschouten.economy.Commands.Lootcrate;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Manager.LootcrateManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class LootcrateCMD implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        int price = EconomyManager.getLootcratePrice();
        if (price <= 0) {
            player.sendMessage(Main.prefix + "Lootcrates are currently unavailable because the configured price is invalid.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <amount>§f.");
            player.sendMessage(Main.prefix + "A lootcrate costs §a$" + price + "§f.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <amount>§f.");
            return true;
        }

        Integer amount = CommandUtils.parsePositiveInt(player, args[0], "Amount");
        if (amount == null) {
            return true;
        }

        int maxPerStack = Material.NETHER_STAR.getMaxStackSize();
        if (amount > maxPerStack) {
            player.sendMessage(Main.prefix + "You can buy at most §a" + maxPerStack + " §flootcrates at once.");
            return true;
        }

        long totalPrice = (long) price * amount;
        if (totalPrice > Integer.MAX_VALUE) {
            player.sendMessage(Main.prefix + "That purchase amount is too large.");
            return true;
        }
        if (EconomyManager.getBal(player) < totalPrice) {
            player.sendMessage(Main.prefix + "You need §a$" + totalPrice + " §fto buy §a" + amount + " §flootcrate(s).");
            return true;
        }
        if (!CommandUtils.canFit(player, new org.bukkit.inventory.ItemStack(Material.NETHER_STAR), amount)) {
            player.sendMessage(Main.prefix + "You don't have enough inventory space for these lootcrates.");
            return true;
        }

        EconomyManager.tryWithdraw(player, (int) totalPrice, TransactionReason.LOOTCRATE_PURCHASE, player, "crates=" + amount);
        LootcrateManager.GiveLootCrate(player, amount);
        player.sendMessage(Main.prefix + "You received §a" + amount + " §flootcrate(s) for §a$" + totalPrice + "§f.");
        return true;
    }
}
