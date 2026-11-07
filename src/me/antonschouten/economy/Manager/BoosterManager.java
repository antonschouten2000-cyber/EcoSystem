package me.antonschouten.economy.Manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Data.BoosterData;

public final class BoosterManager {

    public static final int COST_X2 = 200;
    public static final int COST_X3 = 600;
    public static final int COST_X4 = 1000;

    private static final long BOOSTER_DURATION_MILLIS = 60L * 60L * 1000L;
    private static final BoosterData DATA = BoosterData.getInstance();
    private static final Map<UUID, BukkitTask> EXPIRY_TASKS = new HashMap<>();

    private BoosterManager() {
    }

    /**
     * Compatibility method for older callers. The active booster is scheduled
     * using the persisted expiry time, or one hour from now for legacy data.
     */
    public static void boosterTimerStart(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long expiry = getExpiry(uuid);
        if (expiry <= System.currentTimeMillis()) {
            expiry = System.currentTimeMillis() + BOOSTER_DURATION_MILLIS;
            setExpiry(uuid, expiry);
            DataSaveManager.markBoosterDataDirty();
        }
        scheduleExpiry(uuid, expiry);
    }

    public static void EnableX2Booster(Player player) {
        enableBooster(player, 2, COST_X2);
    }

    public static void EnableX3Booster(Player player) {
        enableBooster(player, 3, COST_X3);
    }

    public static void EnableX4Booster(Player player) {
        enableBooster(player, 4, COST_X4);
    }

    public static int getActiveMultiplier(Player player) {
        return player == null ? 1 : getActiveMultiplier(player.getUniqueId());
    }

    public static int getActiveMultiplier(UUID uuid) {
        if (uuid == null) {
            return 1;
        }

        long expiry = getExpiry(uuid);
        if (expiry > 0 && expiry <= System.currentTimeMillis()) {
            expire(uuid, false);
            return 1;
        }

        String base = basePath(uuid);
        if (DATA.getData().getBoolean(base + ".x4")) {
            return 4;
        }
        if (DATA.getData().getBoolean(base + ".x3")) {
            return 3;
        }
        if (DATA.getData().getBoolean(base + ".x2")) {
            return 2;
        }
        return 1;
    }

    public static boolean isActive(Player player) {
        return getActiveMultiplier(player) > 1;
    }

    /** Restores booster expiry timers after a restart/reload. */
    public static void restoreActiveBoosters() {
        shutdownTasks();

        ConfigurationSection players = DATA.getData().getConfigurationSection("Players");
        if (players == null) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean changed = false;

        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            int multiplier = getStoredMultiplier(uuid);
            if (multiplier == 1) {
                continue;
            }

            long expiry = getExpiry(uuid);
            if (expiry <= 0) {
                // Migration for old booster data that did not store an expiry.
                expiry = now + BOOSTER_DURATION_MILLIS;
                setExpiry(uuid, expiry);
                changed = true;
            }

            if (expiry <= now) {
                clearBooster(uuid);
                changed = true;
            } else {
                scheduleExpiry(uuid, expiry);
            }
        }

        if (changed) {
            DataSaveManager.markBoosterDataDirty();
        }
    }

    public static void shutdown() {
        shutdownTasks();
    }

    private static void enableBooster(Player player, int multiplier, int cost) {
        if (player == null || multiplier < 2 || multiplier > 4 || cost <= 0) {
            return;
        }

        if (isActive(player)) {
            player.sendMessage(Main.prefix + "You already have an active booster.");
            return;
        }

        if (!EconomyManager.tryWithdraw(player, cost, TransactionReason.BOOSTER_PURCHASE, player, "multiplier=x" + multiplier)) {
            player.sendMessage(Main.prefix + "You don't have enough money to buy this booster.");
            return;
        }

        UUID uuid = player.getUniqueId();
        String base = basePath(uuid);
        DATA.getData().set(base + ".x2", multiplier == 2);
        DATA.getData().set(base + ".x3", multiplier == 3);
        DATA.getData().set(base + ".x4", multiplier == 4);

        long expiry = System.currentTimeMillis() + BOOSTER_DURATION_MILLIS;
        setExpiry(uuid, expiry);
        DataSaveManager.markBoosterDataDirty();
        scheduleExpiry(uuid, expiry);
    }

    private static void scheduleExpiry(UUID uuid, long expiryMillis) {
        BukkitTask previous = EXPIRY_TASKS.remove(uuid);
        if (previous != null) {
            previous.cancel();
        }

        Main plugin = Main.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        long remainingMillis = Math.max(1L, expiryMillis - System.currentTimeMillis());
        long delayTicks = Math.max(1L, (remainingMillis + 49L) / 50L);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(uuid, true), delayTicks);
        EXPIRY_TASKS.put(uuid, task);
    }

    private static void expire(UUID uuid, boolean notifyPlayer) {
        EXPIRY_TASKS.remove(uuid);
        if (getStoredMultiplier(uuid) == 1 && getExpiry(uuid) <= 0) {
            return;
        }

        clearBooster(uuid);
        DataSaveManager.markBoosterDataDirty();

        if (notifyPlayer) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(Main.prefix + "Your booster has expired.");
            }
        }
    }

    private static void clearBooster(UUID uuid) {
        String base = basePath(uuid);
        DATA.getData().set(base + ".x2", false);
        DATA.getData().set(base + ".x3", false);
        DATA.getData().set(base + ".x4", false);
        DATA.getData().set(base + ".ExpiresAt", null);
    }

    private static int getStoredMultiplier(UUID uuid) {
        String base = basePath(uuid);
        if (DATA.getData().getBoolean(base + ".x4")) {
            return 4;
        }
        if (DATA.getData().getBoolean(base + ".x3")) {
            return 3;
        }
        if (DATA.getData().getBoolean(base + ".x2")) {
            return 2;
        }
        return 1;
    }

    private static long getExpiry(UUID uuid) {
        return DATA.getData().getLong(basePath(uuid) + ".ExpiresAt", 0L);
    }

    private static void setExpiry(UUID uuid, long expiry) {
        DATA.getData().set(basePath(uuid) + ".ExpiresAt", expiry);
    }

    private static String basePath(UUID uuid) {
        return "Players." + uuid + ".Booster";
    }

    private static void shutdownTasks() {
        for (BukkitTask task : EXPIRY_TASKS.values()) {
            task.cancel();
        }
        EXPIRY_TASKS.clear();
    }
}
