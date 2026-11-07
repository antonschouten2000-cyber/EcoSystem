package me.antonschouten.economy.Listeners.Jobs.Smith.CraftTools;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;

import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class SmithCraftToolsListener implements Listener {

	private static final Map<Material, String> REWARD_PATHS = new EnumMap<>(Material.class);

	static {
		registerTier("Jobs.Rewards.Smith.CraftWoodTools", Material.WOODEN_SWORD, Material.WOODEN_PICKAXE,
				Material.WOODEN_AXE, Material.WOODEN_SHOVEL, Material.WOODEN_HOE);
		registerTier("Jobs.Rewards.Smith.CraftStoneTools", Material.STONE_SWORD, Material.STONE_PICKAXE,
				Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE);
		registerTier("Jobs.Rewards.Smith.CraftIronTools", Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
				Material.IRON_SHOVEL, Material.IRON_HOE);
		registerTier("Jobs.Rewards.Smith.CraftGoldTools", Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE,
				Material.GOLDEN_AXE, Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE);
		registerTier("Jobs.Rewards.Smith.CraftDiamondTools", Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE,
				Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE);
		registerTier("Jobs.Rewards.Smith.CraftCopperTools", Material.COPPER_SWORD, Material.COPPER_PICKAXE,
				Material.COPPER_AXE, Material.COPPER_SHOVEL, Material.COPPER_HOE);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCraft(CraftItemEvent event) {
		if (!(event.getWhoClicked() instanceof Player player) || !JobListenerSupport.canEarn(player, "Smith")) {
			return;
		}

		Material result = event.getRecipe().getResult().getType();
		String rewardPath = REWARD_PATHS.get(result);
		if (rewardPath == null) {
			return;
		}

		int crafts = JobListenerSupport.calculateCraftCount(event);
		if (crafts <= 0) {
			return;
		}

		JobManager.UpdateCraftedTools(player, crafts);
		JobListenerSupport.payReward(player, rewardPath, crafts);
	}

	private static void registerTier(String rewardPath, Material... materials) {
		for (Material material : materials) {
			REWARD_PATHS.put(material, rewardPath);
		}
	}
}
