package me.antonschouten.economy.Listeners.Jobs.Smith.CraftTools;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class SmithCraftNetheriteTools implements Listener {

    private static final Set<Material> TOOLS = EnumSet.of(
            Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !JobListenerSupport.canEarn(player, "Smith")) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || !TOOLS.contains(result.getType())) {
            return;
        }

        JobManager.UpdateCraftedTools(player);
        JobListenerSupport.payReward(player, "Jobs.Rewards.Smith.CraftNetheriteTools");
    }
}
