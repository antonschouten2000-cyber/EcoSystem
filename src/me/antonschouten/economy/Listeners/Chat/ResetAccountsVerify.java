package me.antonschouten.economy.Listeners.Chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.EconomyManager;

public final class ResetAccountsVerify implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!EconomyManager.resetAccounts.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> handle(player, input));
    }

    private static void handle(Player player, String input) {
        if (!player.isOnline()) {
            EconomyManager.resetAccounts.remove(player.getUniqueId());
            return;
        }
        if (input.equalsIgnoreCase("yes")) {
            EconomyManager.resetAccounts.remove(player.getUniqueId());
            EconomyManager.resetAllAccounts(player);
            player.sendMessage(Main.prefix + "All accounts have been reset.");
            return;
        }
        if (input.equalsIgnoreCase("no") || input.equalsIgnoreCase("cancel")) {
            EconomyManager.resetAccounts.remove(player.getUniqueId());
            player.sendMessage(Main.prefix + "Process cancelled.");
            return;
        }
        player.sendMessage(Main.prefix + "Type §ayes §for §ano §fto confirm or cancel the reset.");
    }
}
