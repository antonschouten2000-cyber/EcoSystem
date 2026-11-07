package me.antonschouten.economy.Listeners.Chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.EcoSystem.EcoSystem;
import me.antonschouten.economy.Extern.AdminPanel;

public final class AdminPanelPassword implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean awaiting;
        synchronized (EcoSystem.adminpanelPassword) {
            awaiting = EcoSystem.adminpanelPassword.contains(player.getUniqueId());
        }
        if (!awaiting) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> handle(player, input));
    }

    private static void handle(Player player, String input) {
        if (!player.isOnline()) {
            clear(player);
            return;
        }
        if (input.equalsIgnoreCase("cancel")) {
            clear(player);
            player.sendMessage(Main.prefix + "Admin panel login cancelled.");
            return;
        }
        if (!input.equals(EcoSystem.getAdminPanelPassword())) {
            player.sendMessage(Main.prefix + "Wrong password. Try again or type §acancel§f.");
            return;
        }

        clear(player);
        AdminPanel.main();
        player.sendMessage(Main.prefix + "Admin panel started.");
    }

    private static void clear(Player player) {
        synchronized (EcoSystem.adminpanelPassword) {
            EcoSystem.adminpanelPassword.remove(player.getUniqueId());
        }
    }
}
