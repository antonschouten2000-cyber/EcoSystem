package me.antonschouten.economy.Listeners.Jobs.Farmer;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Listeners.ListenerUtils;
import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.ChunkManager;
import me.antonschouten.economy.Manager.JobManager;

public final class FarmerListener implements Listener {

    private static final Map<Material, Integer> HOE_LEVELS = new EnumMap<>(Material.class);
    private static final Map<Material, HarvestSpec> CROPS = new EnumMap<>(Material.class);

    static {
        HOE_LEVELS.put(Material.WOODEN_HOE, 1);
        HOE_LEVELS.put(Material.STONE_HOE, 2);
        HOE_LEVELS.put(Material.IRON_HOE, 3);
        HOE_LEVELS.put(Material.COPPER_HOE, 4);
        HOE_LEVELS.put(Material.GOLDEN_HOE, 4);
        HOE_LEVELS.put(Material.DIAMOND_HOE, 5);
        HOE_LEVELS.put(Material.NETHERITE_HOE, 6);

        register(Material.MELON, Material.MELON_SLICE, Material.MELON_SEEDS,
                "Jobs.Rewards.Farmer.Melon", JobManager::UpdateHarvestMelon, false);
        register(Material.PUMPKIN, Material.PUMPKIN, Material.PUMPKIN_SEEDS,
                "Jobs.Rewards.Farmer.Pumpkin", JobManager::UpdateHarvestPumpkin, false);
        register(Material.WHEAT, Material.WHEAT, Material.WHEAT_SEEDS,
                "Jobs.Rewards.Farmer.Wheat", JobManager::UpdateHarvestWheat, true);
        register(Material.BEETROOTS, Material.BEETROOT, Material.BEETROOT_SEEDS,
                "Jobs.Rewards.Farmer.Beetroot", JobManager::UpdateHarvestBeetroot, true);
        register(Material.CARROTS, Material.CARROT, null,
                "Jobs.Rewards.Farmer.Carrots", JobManager::UpdateHarvestCarrots, true);
        register(Material.POTATOES, Material.POTATO, null,
                "Jobs.Rewards.Farmer.Potato", JobManager::UpdateHarvestPotatos, true);
        register(Material.TORCHFLOWER, Material.TORCHFLOWER, Material.TORCHFLOWER_SEEDS,
                "Jobs.Rewards.Farmer.Torchflower", JobManager::UpdateHarvestTorchflower, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!JobListenerSupport.canEarn(player, "Farmer")) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        HarvestSpec spec = CROPS.get(block.getType());
        if (spec == null) {
            return;
        }

        if (!ChunkManager.canBuild(player, block.getChunk())) {
            event.setCancelled(true);
            player.sendMessage(Main.prefix + "Unable to harvest in this chunk.");
            return;
        }

        Integer level = HOE_LEVELS.get(player.getInventory().getItemInMainHand().getType());
        if (level == null) {
            event.setCancelled(true);
            JobListenerSupport.sendCooldownMessage(player, "farmer-hoe", "You have to use a hoe to harvest for your job.");
            return;
        }

        if (spec.requiresMaturity() && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        event.setCancelled(true);
        block.setType(Material.AIR, false);

        ListenerUtils.addOrDrop(player, new ItemStack(spec.produce(), level));
        if (spec.seeds() != null) {
            ListenerUtils.addOrDrop(player, new ItemStack(spec.seeds(), level + 1));
        }

        spec.statUpdater().accept(player);
        JobListenerSupport.payReward(player, spec.rewardPath());
    }

    private static void register(Material block, Material produce, Material seeds,
            String rewardPath, Consumer<Player> updater, boolean requiresMaturity) {
        CROPS.put(block, new HarvestSpec(produce, seeds, rewardPath, updater, requiresMaturity));
    }

    private record HarvestSpec(Material produce, Material seeds, String rewardPath,
            Consumer<Player> statUpdater, boolean requiresMaturity) {
    }
}
