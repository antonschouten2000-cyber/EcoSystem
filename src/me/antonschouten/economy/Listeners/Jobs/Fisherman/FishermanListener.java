package me.antonschouten.economy.Listeners.Jobs.Fisherman;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class FishermanListener implements Listener {

    private static final Set<Material> FISH = EnumSet.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        if (!JobListenerSupport.canEarn(player, "Fisherman")) {
            return;
        }
        if (!(event.getCaught() instanceof Item item) || !FISH.contains(item.getItemStack().getType())) {
            return;
        }

        JobManager.UpdateCatchedFishes(player);
        int reward = JobListenerSupport.payReward(player, "Jobs.Rewards.Fisherman.CatchedFishes");
        if (reward > 0) {
            player.sendMessage(Main.prefix + "You received §a$" + reward + " §ffor catching a fish.");
        }
    }
}
