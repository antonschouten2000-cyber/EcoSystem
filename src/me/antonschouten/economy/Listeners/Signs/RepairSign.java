package me.antonschouten.economy.Listeners.Signs;

import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Utils.Perms;

public final class RepairSign implements Listener {

    @SuppressWarnings("deprecation")
	@EventHandler
    public void onSignChange(SignChangeEvent event) {
        String firstLine = event.getLine(0);
        if (firstLine == null || !firstLine.equalsIgnoreCase("[repair]")) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(Perms.signs)) {
            event.setCancelled(true);
            player.sendMessage(Main.perms);
            return;
        }

        event.setLine(0, "§f[Repair]");
        event.setLine(2, "§3Repair item");
    }

    @SuppressWarnings("deprecation")
	@EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        if (!"§f[Repair]".equals(sign.getLine(0)) || !"§3Repair item".equals(sign.getLine(2))) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(Main.prefix + "Hold a repairable item in your main hand.");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            player.sendMessage(Main.prefix + "This item can't be repaired.");
            return;
        }
        if (damageable.getDamage() <= 0) {
            player.sendMessage(Main.prefix + "This item is already fully repaired.");
            return;
        }

        int cost = Math.max(0, ConfigData.getInstance().getData().getInt("Settings.Signs.CostRepairSign"));
        if (cost > 0 && !EconomyManager.tryWithdraw(player, cost, TransactionReason.REPAIR, player, "item=" + item.getType().name())) {
            player.sendMessage(Main.prefix + "You don't have enough money to repair this item.");
            return;
        }

        damageable.setDamage(0);
        item.setItemMeta(meta);
        player.sendMessage(Main.prefix + "Your §a" + pretty(item.getType()) + " §fhas been repaired"
                + (cost > 0 ? " for §a$" + cost + "§f." : "."));
    }

    private static String pretty(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}
