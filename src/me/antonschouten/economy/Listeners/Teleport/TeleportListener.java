package me.antonschouten.economy.Listeners.Teleport;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import me.antonschouten.economy.Teleport.TeleportManager;

/** Event bridge for the central Economy++ Teleport Core. */
public final class TeleportListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        TeleportManager.handleMovement(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            TeleportManager.handleDamage(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        TeleportManager.recordExternalTeleport(event.getPlayer(), event.getFrom());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        TeleportManager.recordDeath(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TeleportManager.handleQuit(event.getPlayer());
    }
}
