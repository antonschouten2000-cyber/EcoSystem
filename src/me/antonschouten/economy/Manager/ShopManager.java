package me.antonschouten.economy.Manager;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ShopData;

public final class ShopManager {

    public static final String SHOP_TITLE = "§fWelcome to the shop";
    public static final Inventory shop = Bukkit.createInventory(null, 54, SHOP_TITLE);

    private static final ShopData DATA = ShopData.getInstance();

    private ShopManager() {
    }

    public static void SendShopHelpMessage(Player player) {
        player.sendMessage("§8-------------- §6[Economy++ Shop] §8--------------");
        player.sendMessage("§a/shop §8- §fOpen the shop GUI.");
        player.sendMessage("§a/shop add <buy_price> §8- §fAdd the items in your hand to the shop.");
        player.sendMessage("§a/shop help §8- §fPrints the help message.");
        player.sendMessage("§8-------------- §6[Economy++ Shop] §8--------------");
    }

    public static void OpenShop(Player player) {
        if (player == null) {
            return;
        }
        loadShopFromData();
        player.openInventory(shop);
    }

    /** Compatibility method retained for old callers. */
    public static void addItems() {
        loadShopFromData();
    }

    public static void loadShopFromData() {
        ItemStack[] content = new ItemStack[shop.getSize()];
        for (int slot = 0; slot < content.length; slot++) {
            ItemStack stored = DATA.getData().getItemStack("Contents" + slot);
            content[slot] = stored == null ? null : stored.clone();
        }
        shop.setContents(content);
    }

    public static void saveShop(Player player, int price) {
        if (player == null || price <= 0) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            player.sendMessage(Main.prefix + "Hold an item in your main hand.");
            return;
        }

        loadShopFromData();

        ItemStack shopItem = hand.clone();
        ItemMeta meta = shopItem.getItemMeta();
        if (meta == null) {
            player.sendMessage(Main.prefix + "This item can't be added to the shop.");
            return;
        }

        meta.setDisplayName("§3" + prettyMaterialName(shopItem.getType()));
        meta.setLore(List.of(
                "§fAmount: §a" + shopItem.getAmount(),
                "§fPrice:",
                "§a" + price
        ));
        shopItem.setItemMeta(meta);

        if (!canFit(shop, shopItem)) {
            player.sendMessage(Main.prefix + "The shop is full.");
            return;
        }

        shop.addItem(shopItem);
        persistShopContents();

        int soldAmount = hand.getAmount();
        Material soldMaterial = hand.getType();
        player.getInventory().setItemInMainHand(null);

        player.sendMessage(Main.prefix + "You added §a" + soldAmount + " "
                + prettyMaterialName(soldMaterial) + " §fto the shop for §a$" + price + "§f.");
    }

    public static void persistShopContents() {
        ItemStack[] contents = shop.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            DATA.getData().set("Contents" + slot, contents[slot]);
        }
        DataSaveManager.markShopDataDirty();
    }

    private static boolean canFit(Inventory inventory, ItemStack item) {
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

    private static String prettyMaterialName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}
