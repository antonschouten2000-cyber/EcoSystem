package me.antonschouten.economy.Listeners.Chunk;

import java.util.Objects;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.ChunkManager;

public final class ChunkListeners implements Listener {

    public ChunkListeners(ChunkManager claimManager) {
        Objects.requireNonNull(claimManager, "claimManager");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        protect(event.getPlayer(), event.getBlock().getChunk(), () -> event.setCancelled(true),
                "Unable to build in this chunk.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        protect(event.getPlayer(), event.getBlock().getChunk(), () -> event.setCancelled(true),
                "Unable to break blocks in this chunk.");
    }

    private static void protect(Player player, Chunk chunk, Runnable cancel, String message) {
        if (ChunkManager.canBuild(player, chunk)) {
            return;
        }
        cancel.run();
        player.sendMessage(Main.prefix + message);
    }
}
