package me.antonschouten.economy.Listeners.TradeNPC;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.TradeManager;

public final class TradeNPCListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)
                || Main.traderIdKey == null) {
            return;
        }

        String traderId = villager.getPersistentDataContainer().get(Main.traderIdKey, PersistentDataType.STRING);
        if (traderId == null || traderId.isBlank()) {
            return;
        }

        String canonicalId = TradeManager.findTraderId(traderId);
        if (canonicalId == null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Main.prefix + "This trader is no longer configured.");
            return;
        }

        villager.setRecipes(TradeManager.buildRecipes(canonicalId));
    }
}
