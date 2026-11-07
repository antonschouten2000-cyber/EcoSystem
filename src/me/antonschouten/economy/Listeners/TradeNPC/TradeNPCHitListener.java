package me.antonschouten.economy.Listeners.TradeNPC;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

import me.antonschouten.economy.Main;

public final class TradeNPCHitListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || Main.traderIdKey == null) {
            return;
        }
        if (villager.getPersistentDataContainer().has(Main.traderIdKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
