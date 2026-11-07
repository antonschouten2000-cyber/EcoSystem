package me.antonschouten.economy.Listeners.Jobs.Smith.CraftArmor;

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

public final class SmithCraftNetheriteArmor implements Listener {

    private static final Set<Material> ARMOR = EnumSet.of(
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !JobListenerSupport.canEarn(player, "Smith")) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || !ARMOR.contains(result.getType())) {
            return;
        }

        JobManager.UpdateCraftedArmor(player);
        JobListenerSupport.payReward(player, "Jobs.Rewards.Smith.CraftNetheriteArmor");
    }
}
