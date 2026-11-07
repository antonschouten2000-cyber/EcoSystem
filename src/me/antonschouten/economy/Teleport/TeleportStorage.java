package me.antonschouten.economy.Teleport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import me.antonschouten.economy.Data.TeleportData;
import me.antonschouten.economy.Manager.DataSaveManager;
import me.antonschouten.economy.Utils.CommandUtils;

/**
 * Persistent location storage used by the Teleport Core.
 *
 * <p>All writes go through DataSaveManager so spawn/warp/back updates benefit
 * from the same batched, atomic YAML persistence as the rest of Economy++.</p>
 */
public final class TeleportStorage {

    private static final TeleportData DATA = TeleportData.getInstance();
    private static final String SPAWN = "Spawn";
    private static final String WARPS = "Warps";
    private static final String BACK = "Back";

    private TeleportStorage() {
    }

    public static void setSpawn(Location location) {
        writeLocation(SPAWN, location);
        DataSaveManager.markTeleportDataDirty();
    }

    public static Location getSpawn() {
        return readLocation(SPAWN);
    }

    public static boolean hasSpawn() {
        return getSpawn() != null;
    }

    public static boolean setWarp(String name, Location location) {
        if (!validWarpName(name) || !validLocation(location)) {
            return false;
        }

        String key = normalizedWarpKey(name);
        String base = WARPS + "." + key;
        DATA.getData().set(base + ".Name", name);
        writeLocation(base + ".Location", location);
        DataSaveManager.markTeleportDataDirty();
        return true;
    }

    public static boolean deleteWarp(String name) {
        String key = findWarpKey(name);
        if (key == null) {
            return false;
        }
        DATA.getData().set(WARPS + "." + key, null);
        DataSaveManager.markTeleportDataDirty();
        return true;
    }

    public static Location getWarp(String name) {
        String key = findWarpKey(name);
        if (key == null) {
            return null;
        }
        return readLocation(WARPS + "." + key + ".Location");
    }

    public static String getWarpDisplayName(String name) {
        String key = findWarpKey(name);
        if (key == null) {
            return null;
        }
        return DATA.getData().getString(WARPS + "." + key + ".Name", key);
    }

    public static List<String> getWarpNames() {
        ConfigurationSection section = DATA.getData().getConfigurationSection(WARPS);
        if (section == null) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            String name = DATA.getData().getString(WARPS + "." + key + ".Name", key);
            if (name != null && !name.isBlank() && getWarp(name) != null) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    public static void setBackLocation(UUID playerId, Location location) {
        if (playerId == null || !validLocation(location)) {
            return;
        }
        writeLocation(BACK + "." + playerId, location);
        DataSaveManager.markTeleportDataDirty();
    }

    public static Location getBackLocation(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return readLocation(BACK + "." + playerId);
    }

    public static boolean validWarpName(String name) {
        return CommandUtils.isSafeIdentifier(name, 32);
    }

    private static String normalizedWarpKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String findWarpKey(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        ConfigurationSection section = DATA.getData().getConfigurationSection(WARPS);
        if (section == null) {
            return null;
        }

        String normalized = normalizedWarpKey(name);
        if (section.contains(normalized)) {
            return normalized;
        }

        for (String key : section.getKeys(false)) {
            String displayName = DATA.getData().getString(WARPS + "." + key + ".Name", key);
            if (key.equalsIgnoreCase(name)
                    || (displayName != null && displayName.equalsIgnoreCase(name))) {
                return key;
            }
        }
        return null;
    }

    private static void writeLocation(String base, Location location) {
        if (!validLocation(location)) {
            throw new IllegalArgumentException("Cannot store an invalid teleport location");
        }

        FileConfiguration data = DATA.getData();
        data.set(base + ".World", location.getWorld().getName());
        data.set(base + ".X", location.getX());
        data.set(base + ".Y", location.getY());
        data.set(base + ".Z", location.getZ());
        data.set(base + ".Yaw", location.getYaw());
        data.set(base + ".Pitch", location.getPitch());
    }

    private static Location readLocation(String base) {
        FileConfiguration data = DATA.getData();
        String worldName = data.getString(base + ".World");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = data.getDouble(base + ".X");
        double y = data.getDouble(base + ".Y");
        double z = data.getDouble(base + ".Z");
        float yaw = (float) data.getDouble(base + ".Yaw");
        float pitch = (float) data.getDouble(base + ".Pitch");

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return null;
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    private static boolean validLocation(Location location) {
        return location != null
                && location.getWorld() != null
                && Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }
}
