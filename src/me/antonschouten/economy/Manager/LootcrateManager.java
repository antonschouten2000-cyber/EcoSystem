package me.antonschouten.economy.Manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.LootcrateData;

public final class LootcrateManager {

    public static final String CRATE_NAME = "§fLootCrate";
    private static final LootcrateData DATA = LootcrateData.getInstance();

    private LootcrateManager() {
    }

    public static void GiveLootCrate(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        int remaining = amount;
        int maxStack = Material.NETHER_STAR.getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(remaining, maxStack);
            ItemStack crate = createCrate(stackAmount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(crate);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= stackAmount;
        }
    }

    public static void UseLootCrate(Player player) {
        if (player == null) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isLootCrate(hand)) {
            return;
        }

        List<String> rewards = DATA.getData().getStringList("Rewards");
        if (rewards.isEmpty()) {
            player.sendMessage(Main.prefix + "No lootcrate rewards are configured.");
            return;
        }

        List<ItemStack> validRewards = new ArrayList<>();
        for (String configured : rewards) {
            ItemStack parsed = parseReward(configured);
            if (parsed != null) {
                validRewards.add(parsed);
            }
        }

        if (validRewards.isEmpty()) {
            player.sendMessage(Main.prefix + "No valid lootcrate rewards are configured.");
            if (Main.getInstance() != null) {
                Main.getInstance().getLogger().warning("LootcrateData.yml contains no valid rewards.");
            }
            return;
        }

        ItemStack reward = validRewards.get(ThreadLocalRandom.current().nextInt(validRewards.size())).clone();

        consumeOne(hand, player);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    public static boolean isLootCrate(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && CRATE_NAME.equalsIgnoreCase(meta.getDisplayName());
    }

    private static ItemStack createCrate(int amount) {
        ItemStack crate = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = crate.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CRATE_NAME);
            meta.setLore(List.of("§8- §aA crate for luck."));
            crate.setItemMeta(meta);
        }
        return crate;
    }

    /** Supports MATERIAL and MATERIAL:AMOUNT entries. */
    private static ItemStack parseReward(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts = value.trim().split(":", 2);
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || material.isAir()) {
            return null;
        }

        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        if (amount <= 0) {
            return null;
        }
        amount = Math.min(amount, material.getMaxStackSize());
        return new ItemStack(material, amount);
    }

    private static void consumeOne(ItemStack hand, Player player) {
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }
}
