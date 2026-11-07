package me.antonschouten.economy.Manager;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Data.HomeData;

public final class HomeManager {

    private static final HomeData DATA = HomeData.getInstance();
    private static final ConfigData CONFIG = ConfigData.getInstance();

    private HomeManager() {
    }

    public static void setHome(Player player, String home) {
        if (player == null || !validHomeName(home)) {
            return;
        }

        String actualName = findHome(player, home);
        if (actualName == null) {
            actualName = home;
        }

        List<String> homes = new ArrayList<>(getHomes(player));
        if (findIgnoreCase(homes, actualName) == null) {
            homes.add(actualName);
        }

        Location location = player.getLocation();
        String playerBase = player.getUniqueId().toString();
        String homeBase = playerBase + ".Homes." + actualName;

        DATA.getData().set(playerBase + ".HomeList", homes);
        DATA.getData().set(homeBase + ".W", location.getWorld().getName());
        DATA.getData().set(homeBase + ".X", location.getX());
        DATA.getData().set(homeBase + ".Y", location.getY());
        DATA.getData().set(homeBase + ".Z", location.getZ());
        DATA.getData().set(homeBase + ".Yaw", location.getYaw());
        DATA.getData().set(homeBase + ".Pitch", location.getPitch());
        DataSaveManager.markHomeDataDirty();
    }

    public static void deleteHome(Player player, String home) {
        if (player == null || !validHomeName(home)) {
            return;
        }

        String actualName = findHome(player, home);
        if (actualName == null) {
            return;
        }

        String playerBase = player.getUniqueId().toString();
        List<String> homes = new ArrayList<>(getHomes(player));
        homes.removeIf(existing -> existing.equalsIgnoreCase(actualName));

        DATA.getData().set(playerBase + ".HomeList", homes);
        DATA.getData().set(playerBase + ".Homes." + actualName, null);
        DataSaveManager.markHomeDataDirty();
    }

    public static Location getHomeLocation(Player player, String home) {
        if (player == null || !validHomeName(home)) {
            throw new IllegalArgumentException("Invalid player or home name");
        }

        String actualName = findHome(player, home);
        if (actualName == null) {
            throw new IllegalArgumentException("Home does not exist: " + home);
        }

        String base = player.getUniqueId() + ".Homes." + actualName;
        String worldName = DATA.getData().getString(base + ".W");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Home world does not exist: " + worldName);
        }

        double x = DATA.getData().getDouble(base + ".X");
        double y = DATA.getData().getDouble(base + ".Y");
        double z = DATA.getData().getDouble(base + ".Z");
        float yaw = (float) DATA.getData().getDouble(base + ".Yaw");
        float pitch = (float) DATA.getData().getDouble(base + ".Pitch");

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalStateException("Home contains non-finite coordinates");
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Legacy direct-teleport API retained for source compatibility. New code
     * should use getHomeLocation() and route the location through TeleportManager.
     */
    @Deprecated
    public static void teleportToHome(Player player, String home) {
        player.teleport(getHomeLocation(player, home));
    }

    public static int getMaxAmountOfHomes() {
        return Math.max(0, CONFIG.getData().getInt("Settings.AmountOfHomes", 5));
    }

    public static List<String> getHomes(Player player) {
        if (player == null) {
            return List.of();
        }
        return List.copyOf(DATA.getData().getStringList(player.getUniqueId() + ".HomeList"));
    }

    public static boolean hasHome(Player player, String home) {
        return findHome(player, home) != null;
    }

    public static String findHome(Player player, String home) {
        if (player == null || !validHomeName(home)) {
            return null;
        }
        return findIgnoreCase(getHomes(player), home);
    }

    public static boolean homesEnabled() {
        return CONFIG.getData().getBoolean("Settings.HomesEnabled", true);
    }

    private static boolean validHomeName(String home) {
        return home != null && !home.isBlank();
    }

    private static String findIgnoreCase(List<String> values, String input) {
        for (String value : values) {
            if (value.equalsIgnoreCase(input)) {
                return value;
            }
        }
        return null;
    }
}
