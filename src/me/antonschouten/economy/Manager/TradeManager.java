package me.antonschouten.economy.Manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.Villager.Type;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.TradeData;

public final class TradeManager {

    private static final TradeData DATA = TradeData.getInstance();

    /** Kept public for compatibility with the command/tab-completion code. */
    public static final ArrayList<String> tradersList = new ArrayList<>();

    private TradeManager() {
    }

    public static void TradeHelpMessage(Player player) {
        player.sendMessage("");
        player.sendMessage("§8-------------- §6[Economy++ Trader NPC] §8--------------");
        player.sendMessage("§a/tradenpc spawn <name> §8- §fSpawn a new trader.");
        player.sendMessage("§a/tradenpc addtrade <name> <result> <resultAmount> <ingredient> <ingredientAmount> §8- §fAdd a trade.");
        player.sendMessage("§a/tradenpc remove <name> §8- §fRemove a trader.");
        player.sendMessage("§a/tradenpc list §8- §fList all traders.");
        player.sendMessage("§8-------------- §6[Economy++ Trader NPC] §8--------------");
    }

    public static Villager spawnTrader(Location location, String id) {
        if (location == null || location.getWorld() == null || !validId(id)) {
            return null;
        }
        if (Main.traderIdKey == null) {
            throw new IllegalStateException("Trader NamespacedKey has not been initialized yet.");
        }

        Villager villager = location.getWorld().spawn(location, Villager.class);
        configureTrader(villager, id);
        villager.setRecipes(buildRecipes(id));
        return villager;
    }

    public static void removeTrader(String name) {
        String id = findTraderId(name);
        if (id == null) {
            return;
        }

        tradersList.removeIf(existing -> existing.equalsIgnoreCase(id));
        DATA.getData().set("traders." + id, null);
        DATA.getData().set("TradersList", new ArrayList<>(tradersList));
        DataSaveManager.markTradeDataDirty();

        forEachTaggedTrader((villager, traderId) -> {
            if (traderId.equalsIgnoreCase(id)) {
                villager.remove();
            }
        });
    }

    /**
     * Rebuilds managed trader NPCs from TradeData.yml. Existing tagged trader
     * villagers are removed first, preventing duplicates after restarts/reloads.
     */
    public static void loadTradersFromConfig() {
        tradersList.clear();
        removeAllTaggedTraderEntities();

        ConfigurationSection traders = DATA.getData().getConfigurationSection("traders");
        if (traders == null) {
            DATA.getData().set("TradersList", new ArrayList<String>());
            DataSaveManager.markTradeDataDirty();
            return;
        }

        for (String id : traders.getKeys(false)) {
            String rawLocation = DATA.getData().getString("traders." + id + ".location");
            Location location = parseLocation(rawLocation);
            if (location == null || location.getWorld() == null) {
                warn("Skipped trader '" + id + "': invalid or unavailable location.");
                continue;
            }

            Villager villager = spawnTrader(location, id);
            if (villager == null) {
                warn("Skipped trader '" + id + "': could not spawn villager.");
                continue;
            }
            tradersList.add(id);
        }

        DATA.getData().set("TradersList", new ArrayList<>(tradersList));
        DataSaveManager.markTradeDataDirty();
    }

    public static void saveTraderToConfig(String name, Location location) {
        if (!validId(name) || location == null || location.getWorld() == null) {
            return;
        }

        String existing = findTraderId(name);
        String id = existing == null ? name : existing;
        if (existing == null) {
            tradersList.add(id);
        }

        DATA.getData().set("TradersList", new ArrayList<>(tradersList));
        DATA.getData().set("traders." + id + ".location", locationToString(location));
        DataSaveManager.markTradeDataDirty();
    }

    public static void addTradeToConfig(
            String name,
            Material result,
            int resultAmount,
            Material ingredient,
            int ingredientAmount) {

        String id = findTraderId(name);
        if (id == null || result == null || ingredient == null
                || resultAmount <= 0 || ingredientAmount <= 0
                || resultAmount > result.getMaxStackSize()
                || ingredientAmount > ingredient.getMaxStackSize()) {
            return;
        }

        String path = "traders." + id + ".trades";
        Map<String, Object> trade = new HashMap<>();
        trade.put("result", result.name());
        trade.put("resultAmount", resultAmount);

        Map<String, Object> ingredientData = new HashMap<>();
        ingredientData.put("item", ingredient.name());
        ingredientData.put("amount", ingredientAmount);
        trade.put("ingredients", List.of(ingredientData));

        List<Map<?, ?>> existingTrades = new ArrayList<>(DATA.getData().getMapList(path));
        existingTrades.add(trade);
        DATA.getData().set(path, existingTrades);
        DataSaveManager.markTradeDataDirty();

        List<MerchantRecipe> recipes = buildRecipes(id);
        forEachTaggedTrader((villager, traderId) -> {
            if (traderId.equalsIgnoreCase(id)) {
                villager.setRecipes(recipes);
            }
        });
    }

    public static String locationToString(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and world are required");
        }
        return location.getWorld().getName()
                + "," + location.getX()
                + "," + location.getY()
                + "," + location.getZ()
                + "," + location.getYaw()
                + "," + location.getPitch();
    }

    /** Supports both the legacy world,x,y,z format and the new yaw/pitch format. */
    public static Location parseLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String[] parts = value.split(",");
        if (parts.length != 4 && parts.length != 6) {
            return null;
        }

        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                return null;
            }

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length == 6 ? Float.parseFloat(parts[4]) : 0.0F;
            float pitch = parts.length == 6 ? Float.parseFloat(parts[5]) : 0.0F;

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return null;
            }

            // Legacy files stored block coordinates and the old manager spawned
            // traders in the center of that block. Preserve that behavior even
            // when a legacy file is manually restored after migration.
            if (parts.length == 4) {
                x += 0.5D;
                z += 0.5D;
            }
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static String findTraderId(String input) {
        if (!validId(input)) {
            return null;
        }
        for (String id : tradersList) {
            if (id.equalsIgnoreCase(input)) {
                return id;
            }
        }

        ConfigurationSection traders = DATA.getData().getConfigurationSection("traders");
        if (traders != null) {
            for (String id : traders.getKeys(false)) {
                if (id.equalsIgnoreCase(input)) {
                    return id;
                }
            }
        }
        return null;
    }

    public static List<MerchantRecipe> buildRecipes(String name) {
        String id = findTraderId(name);
        if (id == null && DATA.getData().contains("traders." + name)) {
            id = name;
        }
        if (id == null) {
            return List.of();
        }

        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Map<?, ?> rawTrade : DATA.getData().getMapList("traders." + id + ".trades")) {
            MerchantRecipe recipe = parseRecipe(rawTrade, id);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private static MerchantRecipe parseRecipe(Map<?, ?> rawTrade, String traderId) {
        try {
            Material resultMaterial = Material.matchMaterial(String.valueOf(rawTrade.get("result")));
            int resultAmount = asInt(rawTrade.get("resultAmount"));
            if (resultMaterial == null || resultMaterial.isAir() || resultAmount <= 0
                    || resultAmount > resultMaterial.getMaxStackSize()) {
                return null;
            }

            MerchantRecipe recipe = new MerchantRecipe(new ItemStack(resultMaterial, resultAmount), 99_999);
            Object rawIngredients = rawTrade.get("ingredients");
            if (!(rawIngredients instanceof Iterable<?> ingredients)) {
                return null;
            }

            int ingredientCount = 0;
            for (Object object : ingredients) {
                if (!(object instanceof Map<?, ?> ingredientMap)) {
                    continue;
                }
                Material material = Material.matchMaterial(String.valueOf(ingredientMap.get("item")));
                int amount = asInt(ingredientMap.get("amount"));
                if (material == null || material.isAir() || amount <= 0 || amount > material.getMaxStackSize()) {
                    continue;
                }
                recipe.addIngredient(new ItemStack(material, amount));
                ingredientCount++;
            }
            return ingredientCount == 0 ? null : recipe;
        } catch (RuntimeException exception) {
            warn("Skipped malformed trade for trader '" + traderId + "'.");
            return null;
        }
    }

    private static void configureTrader(Villager villager, String id) {
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setCustomName("§8[§6Trader§8] §f" + id);
        villager.setCustomNameVisible(true);
        villager.setProfession(Profession.NITWIT);
        villager.setVillagerLevel(1);
        villager.setVillagerType(Type.PLAINS);
        villager.setPersistent(true);
        villager.getPersistentDataContainer().set(Main.traderIdKey, PersistentDataType.STRING, id);
    }

    private static void removeAllTaggedTraderEntities() {
        forEachTaggedTrader((villager, id) -> villager.remove());
    }

    private static void forEachTaggedTrader(TraderConsumer consumer) {
        if (Main.traderIdKey == null) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            // Copy to avoid surprises when an entity is removed during iteration.
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!(entity instanceof Villager villager)) {
                    continue;
                }
                PersistentDataContainer container = villager.getPersistentDataContainer();
                String id = container.get(Main.traderIdKey, PersistentDataType.STRING);
                if (id != null) {
                    consumer.accept(villager, id);
                }
            }
        }
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,32}");
    }

    private static void warn(String message) {
        if (Main.getInstance() != null) {
            Main.getInstance().getLogger().warning(message);
        }
    }

    @FunctionalInterface
    private interface TraderConsumer {
        void accept(Villager villager, String id);
    }
}
