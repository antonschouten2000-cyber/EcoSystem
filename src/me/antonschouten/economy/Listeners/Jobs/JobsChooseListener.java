package me.antonschouten.economy.Listeners.Jobs;

import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Listeners.ListenerUtils;
import me.antonschouten.economy.Manager.JobManager;

public final class JobsChooseListener implements Listener {

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle()) && ListenerUtils.touchesTopInventory(event)) {
            event.setCancelled(true);
        }
    }

    private static final String TITLE = "§fJobs §aChoose a job!";

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle()) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        String currentJob = JobManager.getPlayerJob(player);
        if (currentJob != null && !currentJob.isBlank() && !currentJob.equalsIgnoreCase("Unemployed")) {
            player.sendMessage(Main.prefix + "You already have the job §a" + currentJob + "§f.");
            ListenerUtils.closeInventoryNextTick(player);
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> choose(player, "Farmer", JobManager::SetJobFarmer);
            case 13 -> choose(player, "Miner", JobManager::SetJobMiner);
            case 16 -> choose(player, "Woodcutter", JobManager::SetJobWoodcutter);
            case 20 -> choose(player, "Smith", JobManager::SetJobSmith);
            case 22 -> choose(player, "Hunter", JobManager::SetJobHunter);
            case 24 -> choose(player, "Fisherman", JobManager::SetJobFisherman);
            default -> { }
        }
    }

    private static void choose(Player player, String job, Consumer<Player> setter) {
        setter.accept(player);
        player.sendMessage(Main.prefix + "You have chosen the job §a" + job + "§f.");
        ListenerUtils.closeInventoryNextTick(player);
    }
}
