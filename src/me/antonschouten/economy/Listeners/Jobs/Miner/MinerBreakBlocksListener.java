package me.antonschouten.economy.Listeners.Jobs.Miner;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class MinerBreakBlocksListener implements Listener {

    private static final Set<Material> PICKAXES = EnumSet.of(
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE, Material.COPPER_PICKAXE);

    private static final Map<Material, OreReward> ORES = new EnumMap<>(Material.class);

    static {
        register(Material.COAL_ORE, "Jobs.Rewards.Miner.CoalOre", JobManager::UpdateMinedCoalOres);
        register(Material.IRON_ORE, "Jobs.Rewards.Miner.IronOre", JobManager::UpdateMinedIronOres);
        register(Material.LAPIS_ORE, "Jobs.Rewards.Miner.LapisOre", JobManager::UpdateMinedLapisOres);
        register(Material.REDSTONE_ORE, "Jobs.Rewards.Miner.RedstoneOre", JobManager::UpdateMinedRedstoneOres);
        register(Material.GOLD_ORE, "Jobs.Rewards.Miner.GoldOre", JobManager::UpdateMinedGoldOres);
        register(Material.DIAMOND_ORE, "Jobs.Rewards.Miner.DiamondOre", JobManager::UpdateMinedDiamondOres);
        register(Material.EMERALD_ORE, "Jobs.Rewards.Miner.EmeraldOre", JobManager::UpdateMinedEmeraldOres);
        register(Material.NETHER_QUARTZ_ORE, "Jobs.Rewards.Miner.QuartzOre", JobManager::UpdateMinedQuartzOres);
        register(Material.COPPER_ORE, "Jobs.Rewards.Miner.CopperOre", JobManager::UpdateMinedCopperOres);
        register(Material.DEEPSLATE_COAL_ORE, "Jobs.Rewards.Miner.DeepSlateCoalOre", JobManager::UpdateMinedDeepSlateCoalOres);
        register(Material.DEEPSLATE_COPPER_ORE, "Jobs.Rewards.Miner.DeepSlateCopperOre", JobManager::UpdateMinedDeepSlateCopperOres);
        register(Material.DEEPSLATE_DIAMOND_ORE, "Jobs.Rewards.Miner.DeepSlateDiamondOre", JobManager::UpdateMinedDeepSlateDiamondOres);
        register(Material.DEEPSLATE_GOLD_ORE, "Jobs.Rewards.Miner.DeepSlateGoldOre", JobManager::UpdateMinedDeepSlateGoldOres);
        register(Material.DEEPSLATE_EMERALD_ORE, "Jobs.Rewards.Miner.DeepSlateEmeraldOre", JobManager::UpdateMinedDeepSlateEmeraldOres);
        register(Material.DEEPSLATE_IRON_ORE, "Jobs.Rewards.Miner.DeepSlateIronOre", JobManager::UpdateMinedDeepSlateIronOres);
        register(Material.DEEPSLATE_REDSTONE_ORE, "Jobs.Rewards.Miner.DeepSlateRedstoneOre", JobManager::UpdateMinedDeepSlateRedstoneOres);
        register(Material.DEEPSLATE_LAPIS_ORE, "Jobs.Rewards.Miner.DeepSlateLapisOre", JobManager::UpdateMinedDeepSlateLapisOres);
        // Keep the historic config/stat key for Nether Gold Ore for backwards compatibility.
        register(Material.NETHER_GOLD_ORE, "Jobs.Rewards.Miner.DeepSlateNetherGoldOre", JobManager::UpdateMinedDeepSlateNetherGoldOres);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!JobListenerSupport.canEarn(player, "Miner")) {
            return;
        }

        OreReward reward = ORES.get(event.getBlock().getType());
        if (reward == null) {
            return;
        }

        if (!PICKAXES.contains(player.getInventory().getItemInMainHand().getType())) {
            JobListenerSupport.sendCooldownMessage(player, "miner-tool", "You have to use a pickaxe to earn money.");
            return;
        }

        reward.statUpdater().accept(player);
        JobListenerSupport.payReward(player, reward.rewardPath());
    }

    private static void register(Material material, String rewardPath, Consumer<Player> statUpdater) {
        ORES.put(material, new OreReward(rewardPath, statUpdater));
    }

    private record OreReward(String rewardPath, Consumer<Player> statUpdater) {
    }
}
