package me.antonschouten.economy.Manager;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AtmManager {

    public static final String INVENTORY_TITLE = "§b► §3ATM §b◄";
    public static final int INVENTORY_SIZE = 45;

    private static final int SLOT_BALANCE = 10;
    private static final int SLOT_PAY = 13;
    private static final int SLOT_CHEQUE = 16;
    private static final int SLOT_CLOSE = 40;

    private AtmManager() {
    }

    public static void openATM(Player player) {
        Objects.requireNonNull(player, "player");

        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        inventory.setItem(SLOT_BALANCE, createItem(Material.PAPER, "§aYour balance"));
        inventory.setItem(SLOT_PAY, createItem(Material.BOOK, "§aPay money"));
        inventory.setItem(SLOT_CHEQUE, createItem(Material.WRITABLE_BOOK, "§aCheque"));
        inventory.setItem(SLOT_CLOSE, createItem(Material.BARRIER, "§cClose ATM"));

        ItemStack glassPane = createItem(Material.CYAN_STAINED_GLASS_PANE, "§f-(*)-");
        for (int slot = 27; slot <= 35; slot++) {
            inventory.setItem(slot, glassPane);
        }

        player.openInventory(inventory);
    }

    private static ItemStack createItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }
}
