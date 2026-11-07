package me.antonschouten.economy.Listeners.Lootcrate;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.LootcrateManager;

public final class CrateListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block.getType() != Material.END_PORTAL_FRAME
                || (!block.hasMetadata("LootCrateBlock") && !LootcrateBlockRegistry.contains(block))) {
            return;
        }

        Player player = event.getPlayer();
        if (!LootcrateManager.isLootCrate(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(Main.prefix + "Opening lootcrate. Good luck!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        LootcrateManager.UseLootCrate(player);
    }
}
