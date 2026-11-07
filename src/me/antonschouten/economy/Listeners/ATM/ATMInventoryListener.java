package me.antonschouten.economy.Listeners.ATM;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.EcoSystem.EcoSystem;
import me.antonschouten.economy.Listeners.ListenerUtils;
import me.antonschouten.economy.Manager.AtmManager;
import me.antonschouten.economy.Manager.EconomyManager;

public final class ATMInventoryListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!AtmManager.INVENTORY_TITLE.equals(event.getView().getTitle())
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        switch (slot) {
            case 10 -> {
                player.sendMessage(Main.prefix + "Your current balance: §a$" + EconomyManager.getBal(player) + "§f.");
                ListenerUtils.closeInventoryNextTick(player);
            }
            case 13 -> {
                player.sendMessage(Main.prefix + "Type §a/pay <player> <amount>§f.");
                ListenerUtils.closeInventoryNextTick(player);
            }
            case 16 -> {
                synchronized (EcoSystem.createCheque) {
                    if (!EcoSystem.createCheque.contains(player.getUniqueId())) {
                        EcoSystem.createCheque.add(player.getUniqueId());
                    }
                }
                player.sendMessage(Main.prefix + "Type the amount of the cheque in chat, or type §acancel§f.");
                ListenerUtils.closeInventoryNextTick(player);
            }
            case 40 -> {
                player.sendMessage(Main.prefix + "ATM closed.");
                ListenerUtils.closeInventoryNextTick(player);
            }
            default -> { }
        }
    }
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (AtmManager.INVENTORY_TITLE.equals(event.getView().getTitle()) && ListenerUtils.touchesTopInventory(event)) {
            event.setCancelled(true);
        }
    }

}
