package me.antonschouten.economy.Listeners.Jobs.Smith.CraftArmor;

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

public final class SmithCraftArmorListener implements Listener {

	private static final Map<Material, String> REWARD_PATHS = new EnumMap<>(Material.class);

	static {
		registerTier("Jobs.Rewards.Smith.CraftLeatherArmor", Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
				Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
		registerTier("Jobs.Rewards.Smith.CraftChainmailArmor", Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
				Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS);
		registerTier("Jobs.Rewards.Smith.CraftIronArmor", Material.IRON_HELMET, Material.IRON_CHESTPLATE,
				Material.IRON_LEGGINGS, Material.IRON_BOOTS);
		registerTier("Jobs.Rewards.Smith.CraftGoldArmor", Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
				Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS);
		registerTier("Jobs.Rewards.Smith.CraftDiamondArmor", Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
				Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
		registerTier("Jobs.Rewards.Smith.CraftCopperArmor", Material.COPPER_HELMET, Material.COPPER_CHESTPLATE,
				Material.COPPER_LEGGINGS, Material.COPPER_BOOTS);
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

		JobManager.UpdateCraftedArmor(player, crafts);
		JobListenerSupport.payReward(player, rewardPath, crafts);
	}

	private static void registerTier(String rewardPath, Material... materials) {
		for (Material material : materials) {
			REWARD_PATHS.put(material, rewardPath);
		}
	}
}
