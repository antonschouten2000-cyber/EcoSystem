package me.antonschouten.economy.Listeners;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;

public final class ListenerUtils {

    private ListenerUtils() {
    }

    public static void closeInventoryNextTick(Player player) {
        Main plugin = Main.getInstance();
        if (player == null || plugin == null || !plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, player::closeInventory);
    }

    public static void addOrDrop(Player player, ItemStack... items) {
        if (player == null || items == null) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    public static boolean touchesTopInventory(InventoryDragEvent event) {
        if (event == null) {
            return false;
        }
        int topSize = event.getView().getTopInventory().getSize();
        return event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize);
    }

    public static boolean canFit(Inventory inventory, ItemStack item) {
        if (inventory == null || item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }

        int remaining = item.getAmount();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= item.getMaxStackSize();
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
