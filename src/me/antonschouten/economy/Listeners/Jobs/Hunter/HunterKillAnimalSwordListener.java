package me.antonschouten.economy.Listeners.Jobs.Hunter;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import me.antonschouten.economy.Listeners.Jobs.JobListenerSupport;
import me.antonschouten.economy.Manager.JobManager;

public final class HunterKillAnimalSwordListener implements Listener {

    private static final Set<Material> MELEE_TOOLS = EnumSet.of(
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
            Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.COPPER_SWORD,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
            Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE, Material.COPPER_AXE);

    static final Set<EntityType> REWARDABLE_ANIMALS = EnumSet.of(
            EntityType.PIG, EntityType.SHEEP, EntityType.COW, EntityType.CHICKEN,
            EntityType.WOLF, EntityType.HORSE, EntityType.SQUID, EntityType.OCELOT,
            EntityType.MOOSHROOM, EntityType.RABBIT, EntityType.POLAR_BEAR, EntityType.LLAMA,
            EntityType.PARROT, EntityType.DOLPHIN, EntityType.TURTLE, EntityType.COD,
            EntityType.SALMON, EntityType.PUFFERFISH, EntityType.TROPICAL_FISH,
            EntityType.DONKEY, EntityType.MULE, EntityType.CAT, EntityType.FOX,
            EntityType.PANDA, EntityType.BEE, EntityType.HOGLIN, EntityType.STRIDER,
            EntityType.GOAT, EntityType.AXOLOTL, EntityType.FROG, EntityType.TADPOLE,
            EntityType.CAMEL, EntityType.SNIFFER, EntityType.TRADER_LLAMA);

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!REWARDABLE_ANIMALS.contains(event.getEntityType())) {
            return;
        }
        if (!(event.getDamageSource().getCausingEntity() instanceof Player player)) {
            return;
        }
        // A direct player source distinguishes melee kills from projectile kills.
        if (event.getDamageSource().getDirectEntity() != player) {
            return;
        }
        if (!JobListenerSupport.canEarn(player, "Hunter")) {
            return;
        }
        if (!MELEE_TOOLS.contains(player.getInventory().getItemInMainHand().getType())) {
            return;
        }

        JobManager.UpdateKilledAnimalsBySword(player);
        JobListenerSupport.payReward(player, "Jobs.Rewards.Hunter.KilledAnimals");
    }
}
