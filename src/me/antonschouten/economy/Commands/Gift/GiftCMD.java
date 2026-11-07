package me.antonschouten.economy.Commands.Gift;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Manager.GiftManager;
import me.antonschouten.economy.Utils.CommandUtils;

public final class GiftCMD implements TabExecutor {

    private static final int GIFT_FEE = 15;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandUtils.requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length == 0) {
            GiftManager.giftHelpMessage(player);
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(Main.prefix + "Usage: §a/" + label + " <player> <amount>§f.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Main.prefix + "Player §a" + args[0] + " §fis not online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Main.prefix + "You can't send a gift to yourself.");
            return true;
        }

        Integer amount = CommandUtils.parsePositiveInt(player, args[1], "Amount");
        if (amount == null) {
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage(Main.prefix + "Hold the item you want to gift in your main hand.");
            return true;
        }
        if (item.getAmount() < amount) {
            player.sendMessage(Main.prefix + "You don't have enough of this item in your hand.");
            return true;
        }
        if (EconomyManager.getBal(player) < GIFT_FEE) {
            player.sendMessage(Main.prefix + "You need §a$" + GIFT_FEE + " §fto send a gift.");
            return true;
        }
        if (!CommandUtils.canFit(target, item, amount)) {
            player.sendMessage(Main.prefix + "§a" + target.getName() + "§f doesn't have enough inventory space for this gift.");
            return true;
        }

        GiftManager.sendGift(player, target, amount);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandUtils.complete(args[0], CommandUtils.onlinePlayerNames(sender instanceof Player p ? p : null));
        }
        return List.of();
    }
}
