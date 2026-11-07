package me.antonschouten.economy.Listeners.Jobs;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Listeners.ListenerUtils;
import me.antonschouten.economy.Manager.JobManager;
import me.antonschouten.economy.Manager.JobProgressionManager;

public final class JobsInfoScreen implements Listener {

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle()) && ListenerUtils.touchesTopInventory(event)) {
            event.setCancelled(true);
        }
    }

    private static final String TITLE = "§fJobs §aInformation";

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle()) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 11 -> runAndClose(player, () -> JobManager.getJobHunterInformation(player));
            case 13 -> runAndClose(player, () -> JobManager.getJobFishermanInformation(player));
            case 15 -> runAndClose(player, () -> JobManager.getJobSmithInformation(player));
            case 19 -> runAndClose(player, () -> JobProgressionManager.sendProgress(player));
            case 21 -> runAndClose(player, () -> JobManager.getJobFarmerInformation(player));
            case 23 -> runAndClose(player, () -> JobManager.getJobMinerInformation(player));
            case 25 -> runAndClose(player, () -> JobManager.getJobWoodcutterInformation(player));
            case 40 -> {
                player.sendMessage(Main.prefix + "Job information screen closed.");
                ListenerUtils.closeInventoryNextTick(player);
            }
            default -> { }
        }
    }

    private static void runAndClose(Player player, Runnable action) {
        action.run();
        ListenerUtils.closeInventoryNextTick(player);
    }
}
