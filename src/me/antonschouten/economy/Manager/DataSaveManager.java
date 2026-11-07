package me.antonschouten.economy.Manager;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.BoosterData;
import me.antonschouten.economy.Data.ChunkData;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.GuildData;
import me.antonschouten.economy.Data.HomeData;
import me.antonschouten.economy.Data.LootcrateData;
import me.antonschouten.economy.Data.PlayerData;
import me.antonschouten.economy.Data.ShopData;
import me.antonschouten.economy.Data.TradeData;
import me.antonschouten.economy.Data.TeleportData;

/**
 * Coalesces frequent YAML writes into a small number of atomic disk saves.
 *
 * <p>Economy++ mutates YamlConfiguration on Bukkit's primary thread. Frequent
 * jobs/economy updates are collected and persisted once per save window rather
 * than rewriting a potentially large YAML file for every event.</p>
 */
public final class DataSaveManager {

    /** Five seconds: low disk pressure while limiting crash-loss exposure. */
    private static final long SAVE_DELAY_TICKS = 100L;
    private static final long RETRY_DELAY_TICKS = 200L;

    private static final Set<Target> DIRTY = EnumSet.noneOf(Target.class);
    private static BukkitTask pendingTask;

    private DataSaveManager() {
    }

    public static void markPlayerDataDirty() {
        markDirty(Target.PLAYER);
    }

    public static void markConfigDataDirty() {
        markDirty(Target.CONFIG);
    }

    public static void markGuildDataDirty() {
        markDirty(Target.GUILD);
    }

    public static void markHomeDataDirty() {
        markDirty(Target.HOME);
    }

    public static void markChunkDataDirty() {
        markDirty(Target.CHUNK);
    }

    public static void markTradeDataDirty() {
        markDirty(Target.TRADE);
    }

    public static void markShopDataDirty() {
        markDirty(Target.SHOP);
    }

    public static void markBoosterDataDirty() {
        markDirty(Target.BOOSTER);
    }

    public static void markLootcrateDataDirty() {
        markDirty(Target.LOOTCRATE);
    }

    public static void markTeleportDataDirty() {
        markDirty(Target.TELEPORT);
    }

    /** Existing API retained for shutdown and callers that do not need a result. */
    public static void flushAll() {
        flushAllAndReport();
    }

    /**
     * Flushes every pending file immediately. Returns false when at least one
     * file could not be persisted. Failed targets stay dirty for a later retry.
     */
    public static boolean flushAllAndReport() {
        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, DataSaveManager::flushAll);
            }
            return false;
        }

        cancelPendingTask();

        if (DIRTY.isEmpty()) {
            return true;
        }

        Set<Target> toSave = EnumSet.copyOf(DIRTY);
        DIRTY.clear();

        Set<Target> failed = EnumSet.noneOf(Target.class);
        for (Target target : toSave) {
            if (!target.save()) {
                failed.add(target);
            }
        }

        if (!failed.isEmpty()) {
            DIRTY.addAll(failed);
            schedule(RETRY_DELAY_TICKS);
            return false;
        }
        return true;
    }

    public static void saveNow(Target target) {
        saveNowAndReport(target);
    }

    /** Saves one target now and keeps it dirty on failure. */
    public static boolean saveNowAndReport(Target target) {
        if (target == null) {
            return false;
        }

        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> saveNow(target));
            }
            return false;
        }

        DIRTY.remove(target);
        boolean success = target.save();
        if (!success) {
            DIRTY.add(target);
            schedule(RETRY_DELAY_TICKS);
        }
        return success;
    }

    private static void markDirty(Target target) {
        if (target == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> markDirty(target));
            }
            return;
        }

        DIRTY.add(target);
        schedule(SAVE_DELAY_TICKS);
    }

    private static void schedule(long delayTicks) {
        if (pendingTask != null) {
            return;
        }

        Main plugin = Main.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        pendingTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTask = null;
            flushAllAndReport();
        }, Math.max(1L, delayTicks));
    }

    private static void cancelPendingTask() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
    }

    public enum Target {
        PLAYER(() -> PlayerData.getInstance().save()),
        CONFIG(() -> ConfigData.getInstance().save()),
        GUILD(() -> GuildData.getInstance().save()),
        HOME(() -> HomeData.getInstance().save()),
        CHUNK(() -> ChunkData.getInstance().save()),
        TRADE(() -> TradeData.getInstance().save()),
        SHOP(() -> ShopData.getInstance().save()),
        BOOSTER(() -> BoosterData.getInstance().save()),
        LOOTCRATE(() -> LootcrateData.getInstance().save()),
        TELEPORT(() -> TeleportData.getInstance().save());

        private final SaveOperation saver;

        Target(SaveOperation saver) {
            this.saver = saver;
        }

        private boolean save() {
            return saver.save();
        }
    }

    @FunctionalInterface
    private interface SaveOperation {
        boolean save();
    }
}
