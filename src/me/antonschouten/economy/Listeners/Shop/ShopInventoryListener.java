package me.antonschouten.economy.Listeners.Shop;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Listeners.ListenerUtils;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Manager.ShopManager;
import me.antonschouten.economy.Utils.Perms;

public final class ShopInventoryListener implements Listener {

    @SuppressWarnings("deprecation")
	@EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!ShopManager.SHOP_TITLE.equals(event.getView().getTitle())
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            if (!player.hasPermission(Perms.ShopEdit)) {
                player.sendMessage(Main.perms);
                return;
            }
            ShopManager.shop.setItem(rawSlot, null);
            ShopManager.persistShopContents();
            player.sendMessage(Main.prefix + "Item §a" + pretty(clicked.getType()) + " §fhas been removed from the shop.");
            return;
        }

        Integer price = readPrice(clicked);
        if (price == null || price <= 0) {
            player.sendMessage(Main.prefix + "This shop item has an invalid price and can't be purchased.");
            return;
        }

        ItemStack purchased = clicked.clone();
        if (!ListenerUtils.canFit(player.getInventory(), purchased)) {
            player.sendMessage(Main.prefix + "You don't have enough inventory space.");
            return;
        }

        if (!EconomyManager.tryWithdraw(player, price, TransactionReason.SHOP_PURCHASE, player, "item=" + purchased.getType().name() + ";amount=" + purchased.getAmount())) {
            player.sendMessage(Main.prefix + "You don't have enough money.");
            return;
        }

        player.getInventory().addItem(purchased);
        player.sendMessage(Main.prefix + "You bought §a" + purchased.getAmount() + " "
                + pretty(purchased.getType()) + " §ffor §a$" + price + "§f.");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (ShopManager.SHOP_TITLE.equals(event.getView().getTitle()) && ListenerUtils.touchesTopInventory(event)) {
            event.setCancelled(true);
        }
    }

    private static Integer readPrice(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return null;
        }

        for (int index = lore.size() - 1; index >= 0; index--) {
            String stripped = ChatColor.stripColor(lore.get(index));
            if (stripped == null) {
                continue;
            }
            String candidate = stripped.replace("$", "").trim();
            try {
                int value = Integer.parseInt(candidate);
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
                // Try the preceding lore line.
            }
        }
        return null;
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}
