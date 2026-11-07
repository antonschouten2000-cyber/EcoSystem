package me.antonschouten.economy.Listeners.ATM.Cheque;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;

public final class UseChequeListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        ItemStack item = event.getItem();
        Integer amount = ChequeSecurity.readValue(item);
        if (amount == null) {
            return;
        }

        Player player = event.getPlayer();
        if ((long) EconomyManager.getBal(player) + amount > Integer.MAX_VALUE) {
            event.setCancelled(true);
            player.sendMessage(Main.prefix + "Your balance is too high to deposit this cheque.");
            return;
        }

        event.setCancelled(true);
        EconomyManager.addBal(player, amount, TransactionReason.CHEQUE_REDEEM, player, "cheque=$" + amount);
        consumeOne(player, item);
        player.sendMessage(Main.prefix + "Cheque deposited: §a$" + amount + "§f.");
    }

    private static void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
