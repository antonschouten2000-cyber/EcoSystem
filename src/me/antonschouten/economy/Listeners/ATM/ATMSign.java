package me.antonschouten.economy.Listeners.ATM;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.AtmManager;
import me.antonschouten.economy.Utils.Perms;

public final class ATMSign implements Listener {

    @SuppressWarnings("deprecation")
	@EventHandler
    public void onSignChange(SignChangeEvent event) {
        String firstLine = event.getLine(0);
        if (firstLine == null || !firstLine.equalsIgnoreCase("[atm]")) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(Perms.signs)) {
            event.setCancelled(true);
            player.sendMessage(Main.perms);
            return;
        }

        event.setLine(0, "§f[ATM]");
        event.setLine(2, "§3Open the ATM");
    }

    @SuppressWarnings("deprecation")
	@EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        if (!"§f[ATM]".equals(sign.getLine(0))) {
            return;
        }

        event.setCancelled(true);
        AtmManager.openATM(event.getPlayer());
    }
}
