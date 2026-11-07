package me.antonschouten.economy.Manager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Transaction.TransactionReason;

public final class GiftManager {

    public static final int GIFT_FEE = 15;

    private GiftManager() {
    }

    public static void giftHelpMessage(Player player) {
        player.sendMessage("");
        player.sendMessage("§8-------------- §6[Economy++ Gifts] §8--------------");
        player.sendMessage("§a/gift <player> <amount> §8- §fGive a player items from your main hand.");
        player.sendMessage("§aEvery gift costs §f$" + GIFT_FEE + " §aand the fee is added to the server bank.");
        player.sendMessage("§fEvery hour§a, the server-bank balance is gifted to a random online player.");
        player.sendMessage("§8-------------- §6[Economy++ Gifts] §8--------------");
    }

    public static void sendGift(Player sender, Player target, int amount) {
        if (sender == null || target == null || amount <= 0) {
            return;
        }
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(Main.prefix + "You can't send a gift to yourself.");
            return;
        }

        ItemStack hand = sender.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            sender.sendMessage(Main.prefix + "Hold an item in your hand.");
            return;
        }
        if (hand.getAmount() < amount) {
            sender.sendMessage(Main.prefix + "You don't have enough items to send this gift.");
            return;
        }

        ItemStack gift = hand.clone();
        gift.setAmount(amount);
        if (!canFit(target, gift)) {
            sender.sendMessage(Main.prefix + "§a" + target.getName() + "§f doesn't have enough inventory space.");
            return;
        }
        if (EconomyManager.getBal(sender) < GIFT_FEE) {
            sender.sendMessage(Main.prefix + "You need §a$" + GIFT_FEE + " §fto send a gift.");
            return;
        }
        if ((long) EconomyManager.getServerbankBalance() + GIFT_FEE > Integer.MAX_VALUE) {
            sender.sendMessage(Main.prefix + "The server bank can't accept the gift fee right now.");
            return;
        }

        // All validations succeeded; mutations can now be applied without partial failure.
        if (!EconomyManager.transferToServerBank(sender, GIFT_FEE, TransactionReason.GIFT_FEE, sender, "recipient=" + target.getUniqueId())) {
            sender.sendMessage(Main.prefix + "You don't have enough money to send this gift.");
            return;
        }

        target.getInventory().addItem(gift);
        if (hand.getAmount() == amount) {
            sender.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - amount);
        }

        String materialName = gift.getType().name().toLowerCase();
        sender.sendMessage(Main.prefix + "You gifted §a" + amount + " " + materialName + " §fto §a" + target.getName() + "§f.");
        target.sendMessage(Main.prefix + "You received §a" + amount + " " + materialName + " §ffrom §a" + sender.getName() + "§f.");
    }

    private static boolean canFit(Player player, ItemStack item) {
        int remaining = item.getAmount();
        int maxStack = item.getMaxStackSize();

        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= maxStack;
            } else if (slot.isSimilar(item)) {
                remaining -= Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }
}
