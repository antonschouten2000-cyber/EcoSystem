package me.antonschouten.economy.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import me.antonschouten.economy.Commands.Lootcrate.SetLootcrateBlockCMD;
import me.antonschouten.economy.EcoSystem.EcoSystem;
import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.EconomyManager;
import me.antonschouten.economy.Manager.JobManager;

public final class Listeners implements Listener {

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        SetLootcrateBlockCMD.setCrateBlock.remove(player);
        EconomyManager.resetAccounts.remove(player.getUniqueId());
        JobManager.resetJobs.remove(player.getUniqueId());
        JobListenerSupport.clearPlayer(player.getUniqueId());

        synchronized (EcoSystem.createCheque) {
            EcoSystem.createCheque.remove(player.getUniqueId());
        }
        synchronized (EcoSystem.adminpanelPassword) {
            EcoSystem.adminpanelPassword.remove(player.getUniqueId());
        }
    }
}
