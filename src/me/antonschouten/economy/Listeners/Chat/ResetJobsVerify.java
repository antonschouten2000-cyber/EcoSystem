package me.antonschouten.economy.Listeners.Chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Manager.JobManager;

public final class ResetJobsVerify implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!JobManager.resetJobs.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> handle(player, input));
    }

    private static void handle(Player player, String input) {
        if (!player.isOnline()) {
            JobManager.resetJobs.remove(player.getUniqueId());
            return;
        }
        if (input.equalsIgnoreCase("yes")) {
            JobManager.resetJobs.remove(player.getUniqueId());
            JobManager.resetAllJobs();
            player.sendMessage(Main.prefix + "All player jobs have been reset.");
            return;
        }
        if (input.equalsIgnoreCase("no") || input.equalsIgnoreCase("cancel")) {
            JobManager.resetJobs.remove(player.getUniqueId());
            player.sendMessage(Main.prefix + "Process cancelled.");
            return;
        }
        player.sendMessage(Main.prefix + "Type §ayes §for §ano §fto confirm or cancel the reset.");
    }
}
