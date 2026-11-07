package me.antonschouten.economy.Listeners.Lootcrate;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Commands.Lootcrate.SetLootcrateBlockCMD;

public final class SetLootcrateBlockListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.END_PORTAL_FRAME) {
            return;
        }

        Player player = event.getPlayer();
        if (!SetLootcrateBlockCMD.setCrateBlock.contains(player)) {
            return;
        }

        event.setCancelled(true);
        Block block = event.getClickedBlock();
        block.setMetadata("LootCrateBlock", new FixedMetadataValue(Main.getInstance(), true));
        LootcrateBlockRegistry.add(block);
        SetLootcrateBlockCMD.setCrateBlock.remove(player);

        player.sendMessage(Main.prefix + "Lootcrate block set at §a"
                + block.getX() + "§f, §a" + block.getY() + "§f, §a" + block.getZ() + "§f.");
    }
}
