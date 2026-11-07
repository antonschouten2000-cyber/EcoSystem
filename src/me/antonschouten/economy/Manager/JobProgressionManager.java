package me.antonschouten.economy.Manager;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.PlayerData;

/**
 * Handles persistent per-job progression for Economy++.
 *
 * <p>Progress is stored per job instead of per active assignment, so players can
 * switch jobs without losing the level and XP they earned in another job.</p>
 */
public final class JobProgressionManager {

    private static final PlayerData PLAYER_DATA = PlayerData.getInstance();
    private static final ConfigData CONFIG_DATA = ConfigData.getInstance();
    private JobProgressionManager() {
    }

    public static boolean isEnabled() {
        return CONFIG_DATA.getData().getBoolean("Jobs.Progression.Enabled", true);
    }

    public static int getMaxLevel() {
        return Math.max(1, CONFIG_DATA.getData().getInt("Jobs.Progression.MaxLevel", 50));
    }

    public static int getLevel(Player player) {
        return getLevel(player, JobManager.getPlayerJob(player));
    }

    public static int getLevel(Player player, String job) {
        String canonicalJob = canonicalJob(job);
        if (player == null || canonicalJob == null) {
            return 1;
        }

        int stored = PLAYER_DATA.getData().getInt(progressPath(player, canonicalJob) + ".Level", 1);
        return Math.max(1, Math.min(getMaxLevel(), stored));
    }

    public static int getCurrentXp(Player player) {
        return getCurrentXp(player, JobManager.getPlayerJob(player));
    }

    public static int getCurrentXp(Player player, String job) {
        String canonicalJob = canonicalJob(job);
        if (player == null || canonicalJob == null) {
            return 0;
        }
        return Math.max(0, PLAYER_DATA.getData().getInt(progressPath(player, canonicalJob) + ".XP", 0));
    }

    public static long getLifetimeXp(Player player) {
        return getLifetimeXp(player, JobManager.getPlayerJob(player));
    }

    public static long getLifetimeXp(Player player, String job) {
        String canonicalJob = canonicalJob(job);
        if (player == null || canonicalJob == null) {
            return 0L;
        }
        return Math.max(0L, PLAYER_DATA.getData().getLong(progressPath(player, canonicalJob) + ".LifetimeXP", 0L));
    }

    public static long getTotalEarned(Player player) {
        return getTotalEarned(player, JobManager.getPlayerJob(player));
    }

    public static long getTotalEarned(Player player, String job) {
        String canonicalJob = canonicalJob(job);
        if (player == null || canonicalJob == null) {
            return 0L;
        }
        return Math.max(0L, PLAYER_DATA.getData().getLong(progressPath(player, canonicalJob) + ".TotalEarned", 0L));
    }

    public static int getXpRequiredForNextLevel(Player player) {
        return getXpRequiredForNextLevel(getLevel(player));
    }

    public static int getXpRequiredForNextLevel(int level) {
        if (level >= getMaxLevel()) {
            return 0;
        }

        long baseXp = Math.max(1, CONFIG_DATA.getData().getInt("Jobs.Progression.BaseXP", 100));
        long increase = Math.max(0, CONFIG_DATA.getData().getInt("Jobs.Progression.XPIncreasePerLevel", 50));
        long required = baseXp + Math.max(0L, (long) level - 1L) * increase;
        return (int) Math.min(Integer.MAX_VALUE, required);
    }

    /**
     * Returns the bonus above 100%. Example: level 6 with 2% per level returns 10.
     */
    public static int getRewardBonusPercent(Player player) {
        return getRewardBonusPercent(player, JobManager.getPlayerJob(player));
    }

    public static int getRewardBonusPercent(Player player, String job) {
        if (!isEnabled()) {
            return 0;
        }

        int level = getLevel(player, job);
        int perLevel = Math.max(0,
                CONFIG_DATA.getData().getInt("Jobs.Progression.RewardBonusPercentPerLevel", 2));
        long bonus = Math.max(0L, (long) (level - 1) * perLevel);
        return (int) Math.min(10_000L, bonus);
    }

    /**
     * Applies the configured job-level reward bonus using integer-safe rounding.
     */
    public static long applyRewardBonus(Player player, String job, long amount) {
        if (amount <= 0L || !isEnabled()) {
            return Math.max(0L, amount);
        }

        int bonusPercent = getRewardBonusPercent(player, job);
        long multiplierPercent = 100L + bonusPercent;

        if (amount > Long.MAX_VALUE / multiplierPercent) {
            return Long.MAX_VALUE;
        }

        long numerator = amount * multiplierPercent;
        return (numerator + 50L) / 100L;
    }

    /**
     * Records XP and actual money earned for one rewarded job action.
     */
    public static ProgressUpdate recordActivity(
            Player player,
            String job,
            int baseRewardPerUnit,
            int units,
            int actualPayout) {

        String canonicalJob = canonicalJob(job);
        if (player == null || canonicalJob == null || units <= 0) {
            return ProgressUpdate.none();
        }

        if (!Bukkit.isPrimaryThread()) {
            Main plugin = Main.getInstance();
            if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> recordActivity(player, canonicalJob, baseRewardPerUnit, units, actualPayout));
            }
            return ProgressUpdate.none();
        }

        ensureProgress(player, canonicalJob);

        String basePath = progressPath(player, canonicalJob);
        long totalEarned = safeAdd(
                PLAYER_DATA.getData().getLong(basePath + ".TotalEarned", 0L),
                Math.max(0, actualPayout));
        PLAYER_DATA.getData().set(basePath + ".TotalEarned", totalEarned);

        if (!isEnabled()) {
            DataSaveManager.markPlayerDataDirty();
            return ProgressUpdate.none();
        }

        int xpPerRewardPoint = Math.max(0,
                CONFIG_DATA.getData().getInt("Jobs.Progression.XPPerRewardPoint", 1));
        int minimumXpPerUnit = Math.max(0,
                CONFIG_DATA.getData().getInt("Jobs.Progression.MinimumXPPerAction", 1));

        long weightedXpPerUnit = Math.max(
                minimumXpPerUnit,
                (long) Math.max(0, baseRewardPerUnit) * xpPerRewardPoint);
        long gainedXpLong = safeMultiply(weightedXpPerUnit, units);
        int gainedXp = (int) Math.min(Integer.MAX_VALUE, gainedXpLong);

        long lifetimeXp = safeAdd(
                PLAYER_DATA.getData().getLong(basePath + ".LifetimeXP", 0L),
                gainedXpLong);
        PLAYER_DATA.getData().set(basePath + ".LifetimeXP", lifetimeXp);

        int oldLevel = getLevel(player, canonicalJob);
        int level = oldLevel;
        long xp = Math.max(0L, PLAYER_DATA.getData().getLong(basePath + ".XP", 0L));
        xp = safeAdd(xp, gainedXpLong);

        int maxLevel = getMaxLevel();
        while (level < maxLevel) {
            int required = getXpRequiredForNextLevel(level);
            if (required <= 0 || xp < required) {
                break;
            }
            xp -= required;
            level++;
        }

        if (level >= maxLevel) {
            level = maxLevel;
            xp = 0L;
        }

        PLAYER_DATA.getData().set(basePath + ".Level", level);
        PLAYER_DATA.getData().set(basePath + ".XP", Math.min(Integer.MAX_VALUE, xp));
        DataSaveManager.markPlayerDataDirty();

        if (level > oldLevel) {
            notifyLevelUp(player, canonicalJob, oldLevel, level);
        }

        int nextRequired = getXpRequiredForNextLevel(level);
        return new ProgressUpdate(
                gainedXp,
                oldLevel,
                level,
                (int) Math.min(Integer.MAX_VALUE, xp),
                nextRequired,
                level > oldLevel);
    }

    public static void ensureProgress(Player player, String job) {
        String canonicalJob = canonicalJob(job);
        if (player == null || canonicalJob == null) {
            return;
        }

        String basePath = progressPath(player, canonicalJob);
        boolean changed = false;

        if (!PLAYER_DATA.getData().contains(basePath + ".Level")) {
            PLAYER_DATA.getData().set(basePath + ".Level", 1);
            changed = true;
        }
        if (!PLAYER_DATA.getData().contains(basePath + ".XP")) {
            PLAYER_DATA.getData().set(basePath + ".XP", 0);
            changed = true;
        }
        if (!PLAYER_DATA.getData().contains(basePath + ".LifetimeXP")) {
            PLAYER_DATA.getData().set(basePath + ".LifetimeXP", 0L);
            changed = true;
        }
        if (!PLAYER_DATA.getData().contains(basePath + ".TotalEarned")) {
            PLAYER_DATA.getData().set(basePath + ".TotalEarned", 0L);
            changed = true;
        }

        if (changed) {
            DataSaveManager.markPlayerDataDirty();
        }
    }

    public static void resetProgress(Player player, String job) {
        String canonicalJob = canonicalJob(job);
        if (player == null || canonicalJob == null) {
            return;
        }
        PLAYER_DATA.getData().set(progressPath(player, canonicalJob), null);
        DataSaveManager.markPlayerDataDirty();
    }

    public static void sendProgress(Player player) {
        if (player == null) {
            return;
        }

        String job = canonicalJob(JobManager.getPlayerJob(player));
        if (job == null) {
            player.sendMessage(Main.prefix + "You need to choose a job first. Use §a/jobs§f.");
            return;
        }

        int level = getLevel(player, job);
        int maxLevel = getMaxLevel();
        int currentXp = getCurrentXp(player, job);
        int nextXp = getXpRequiredForNextLevel(level);
        int bonus = getRewardBonusPercent(player, job);

        player.sendMessage("");
        player.sendMessage("§8-------------- §6[Job Progress] §8--------------");
        player.sendMessage("§fJob: §a" + job);
        player.sendMessage("§fLevel: §a" + level + "§7/§a" + maxLevel);

        if (level >= maxLevel) {
            player.sendMessage("§fXP: §6MAX LEVEL");
            player.sendMessage("§fProgress: " + progressBar(1, 1, 20));
        } else {
            player.sendMessage("§fXP: §a" + formatNumber(currentXp)
                    + "§7/§a" + formatNumber(nextXp));
            player.sendMessage("§fProgress: " + progressBar(currentXp, nextXp, 20));
        }

        player.sendMessage("§fJob reward bonus: §a+" + bonus + "%");
        player.sendMessage("§fLifetime job XP: §a" + formatNumber(getLifetimeXp(player, job)));
        player.sendMessage("§fTotal job earnings: §a$" + formatNumber(getTotalEarned(player, job)));
        player.sendMessage("§8----------------------------------------------");
    }

    public static String progressBar(int current, int maximum, int segments) {
        int safeSegments = Math.max(5, Math.min(40, segments));
        if (maximum <= 0) {
            return "§a" + "|".repeat(safeSegments);
        }

        double ratio = Math.max(0.0D, Math.min(1.0D, current / (double) maximum));
        int filled = (int) Math.floor(ratio * safeSegments);
        return "§a" + "|".repeat(filled) + "§7" + "|".repeat(safeSegments - filled);
    }

    public static String canonicalJob(String job) {
        if (job == null || job.isBlank()) {
            return null;
        }

        return switch (job.trim().toLowerCase(Locale.ROOT)) {
            case "farmer" -> "Farmer";
            case "miner" -> "Miner";
            case "woodcutter" -> "Woodcutter";
            case "smith" -> "Smith";
            case "hunter" -> "Hunter";
            case "fisherman" -> "Fisherman";
            default -> null;
        };
    }

    private static String progressPath(Player player, String canonicalJob) {
        return "Players." + player.getUniqueId() + ".JobProgress." + canonicalJob;
    }

    private static void notifyLevelUp(Player player, String job, int oldLevel, int newLevel) {
        if (!CONFIG_DATA.getData().getBoolean("Jobs.Progression.NotifyLevelUp", true)) {
            return;
        }

        int bonus = getRewardBonusPercent(player, job);
        player.sendMessage(Main.prefix + "§6" + job + " §fjob level increased from §a"
                + oldLevel + " §fto §a" + newLevel + "§f! Reward bonus: §a+" + bonus + "%§f.");
        player.sendTitle("§6Job Level Up!", "§a" + job + " Level " + newLevel, 10, 50, 15);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.15F);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.US, "%,d", Math.max(0L, value));
    }

    private static long safeAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private static long safeMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    public record ProgressUpdate(
            int xpGained,
            int oldLevel,
            int newLevel,
            int currentXp,
            int xpForNextLevel,
            boolean leveledUp) {

        public static ProgressUpdate none() {
            return new ProgressUpdate(0, 1, 1, 0, 0, false);
        }
    }
}
