package me.antonschouten.economy.Listeners.Jobs.Hunter;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class HunterKillAnimalBowListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!HunterKillAnimalSwordListener.REWARDABLE_ANIMALS.contains(event.getEntityType())) {
            return;
        }
        if (!(event.getDamageSource().getCausingEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getDamageSource().getDirectEntity() instanceof Arrow)) {
            return;
        }
        if (!JobListenerSupport.canEarn(player, "Hunter")) {
            return;
        }

        JobManager.UpdateKilledAnimalsByBow(player);
        JobListenerSupport.payReward(player, "Jobs.Rewards.Hunter.KilledAnimals");
    }
}
