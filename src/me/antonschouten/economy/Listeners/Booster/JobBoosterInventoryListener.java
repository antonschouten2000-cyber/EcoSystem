package me.antonschouten.economy.Listeners.Booster;

import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Listeners.ListenerUtils;
import me.antonschouten.economy.Manager.BoosterManager;

public final class JobBoosterInventoryListener implements Listener {

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle()) && ListenerUtils.touchesTopInventory(event)) {
            event.setCancelled(true);
        }
    }

    private static final String TITLE = "§a► §fJob booster §a◄";
    private static final int SLOT_X2 = 10;
    private static final int SLOT_X3 = 13;
    private static final int SLOT_X4 = 16;
    private static final int SLOT_STATUS = 39;
    private static final int SLOT_CLOSE = 41;

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle()) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        switch (slot) {
            case SLOT_X2 -> purchase(player, 2, BoosterManager::EnableX2Booster);
            case SLOT_X3 -> purchase(player, 3, BoosterManager::EnableX3Booster);
            case SLOT_X4 -> purchase(player, 4, BoosterManager::EnableX4Booster);
            case SLOT_STATUS -> showStatus(player);
            case SLOT_CLOSE -> {
                player.sendMessage(Main.prefix + "Job booster menu closed.");
                ListenerUtils.closeInventoryNextTick(player);
            }
            default -> { }
        }
    }

    private static void purchase(Player player, int multiplier, Consumer<Player> enabler) {
        int before = BoosterManager.getActiveMultiplier(player);
        if (before > 1) {
            player.sendMessage(Main.prefix + "You already have an active x" + before + " booster.");
            return;
        }

        enabler.accept(player);
        int after = BoosterManager.getActiveMultiplier(player);
        if (after == multiplier) {
            player.sendMessage(Main.prefix + "The §ax" + multiplier + " §fcoin booster is enabled for §a1 §fhour.");
            ListenerUtils.closeInventoryNextTick(player);
        }
    }

    private static void showStatus(Player player) {
        int multiplier = BoosterManager.getActiveMultiplier(player);
        if (multiplier <= 1) {
            player.sendMessage(Main.prefix + "No booster activated.");
        } else {
            player.sendMessage(Main.prefix + "The §ax" + multiplier + " §fbooster is currently activated.");
        }
    }
}
