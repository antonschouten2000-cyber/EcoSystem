package me.antonschouten.economy.Listeners.Jobs.Woodcutter;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class WoodcutterBreakBlocksListener implements Listener {

    private static final Set<Material> AXES = EnumSet.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE, Material.COPPER_AXE);

    private static final Set<Material> LOGS = EnumSet.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG);

    private static final Set<Material> LEAVES = EnumSet.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!JobListenerSupport.canEarn(player, "Woodcutter")) {
            return;
        }

        Material block = event.getBlock().getType();
        Material tool = player.getInventory().getItemInMainHand().getType();

        if (LOGS.contains(block)) {
            if (!AXES.contains(tool)) {
                JobListenerSupport.sendCooldownMessage(player, "woodcutter-axe", "You have to use an axe to earn money.");
                return;
            }
            JobManager.UpdateCuttedLogs(player);
            JobListenerSupport.payReward(player, "Jobs.Rewards.Woodcutter.Cuttedlog");
            return;
        }

        if (LEAVES.contains(block)) {
            if (tool != Material.SHEARS) {
                JobListenerSupport.sendCooldownMessage(player, "woodcutter-shears", "You have to use shears to earn money.");
                return;
            }
            JobManager.UpdateShearsLeaves(player);
            JobListenerSupport.payReward(player, "Jobs.Rewards.Woodcutter.Shearsleaves");
        }
    }
}
