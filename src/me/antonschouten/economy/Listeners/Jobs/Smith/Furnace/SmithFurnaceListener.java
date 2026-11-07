package me.antonschouten.economy.Listeners.Jobs.Smith.Furnace;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;

import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class SmithFurnaceListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (!JobListenerSupport.canEarn(player, "Smith")) {
            return;
        }

        int amount = event.getItemAmount();
        if (amount <= 0) {
            return;
        }

        JobManager.UpdateCookedItems(player, amount);
        JobListenerSupport.payReward(player, "Jobs.Rewards.Smith.CookedItem", amount);
    }
}
