package me.antonschouten.economy.Teleport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Data.ConfigData;
import me.antonschouten.economy.Utils.Perms;

/**
 * Central Economy++ teleport service.
 *
 * <p>Spawn, warps, homes, TPA and /back all use this service so delay,
 * cooldown, movement/damage cancellation, safe-location validation and /back
 * behavior stay consistent across the plugin.</p>
 */
public final class TeleportManager {

    private static final long TICK_INTERVAL = 5L;

    private static final Map<UUID, PendingTeleport> PENDING_TELEPORTS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, LinkedHashMap<UUID, PlayerTeleportRequest>> INCOMING_REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST_AT = new HashMap<>();
    private static final Map<UUID, Location> BACK_LOCATIONS = new HashMap<>();
    private static final Set<UUID> INTERNAL_TELEPORTS = new HashSet<>();

    private static Main plugin;
    private static BukkitTask tickTask;

    private TeleportManager() {
    }

    public static void initialize(Main owningPlugin) {
        Objects.requireNonNull(owningPlugin, "owningPlugin");
        shutdown();
        plugin = owningPlugin;
        tickTask = Bukkit.getScheduler().runTaskTimer(
                owningPlugin,
                TeleportManager::tick,
                TICK_INTERVAL,
                TICK_INTERVAL);
    }

    public static void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        PENDING_TELEPORTS.clear();
        COOLDOWNS.clear();
        INCOMING_REQUESTS.clear();
        LAST_REQUEST_AT.clear();
        BACK_LOCATIONS.clear();
        INTERNAL_TELEPORTS.clear();
        plugin = null;
    }

    public static boolean isEnabled() {
        return config().getBoolean("Settings.Teleports.Enabled", true);
    }

    public static boolean isSpawnEnabled() {
        return isEnabled() && config().getBoolean("Settings.Teleports.Features.Spawn", true);
    }

    public static boolean isWarpsEnabled() {
        return isEnabled() && config().getBoolean("Settings.Teleports.Features.Warps", true);
    }

    public static boolean isTpaEnabled() {
        return isEnabled() && config().getBoolean("Settings.Teleports.Features.TPA", true);
    }

    public static boolean isBackEnabled() {
        return isEnabled() && config().getBoolean("Settings.Teleports.Features.Back", true);
    }

    public static boolean requestTeleport(
            Player player,
            Location destination,
            TeleportReason reason,
            String destinationName) {
        return requestTeleportInternal(player, destination, null, reason, destinationName);
    }

    public static boolean requestTeleportToPlayer(
            Player player,
            Player destinationPlayer,
            TeleportReason reason,
            String destinationName) {
        if (destinationPlayer == null || !destinationPlayer.isOnline()) {
            if (player != null) {
                player.sendMessage(Main.prefix + "That player is no longer online.");
            }
            return false;
        }
        return requestTeleportInternal(
                player,
                null,
                destinationPlayer.getUniqueId(),
                reason,
                destinationName);
    }

    private static boolean requestTeleportInternal(
            Player player,
            Location fixedDestination,
            UUID destinationPlayerId,
            TeleportReason reason,
            String destinationName) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!isEnabled()) {
            player.sendMessage(Main.prefix + "Teleporting is currently disabled by the server.");
            return false;
        }
        if (fixedDestination != null && !validLocation(fixedDestination)) {
            player.sendMessage(Main.prefix + "That teleport destination is no longer available.");
            return false;
        }

        long cooldownMillis = remainingCooldownMillis(player);
        if (cooldownMillis > 0L) {
            long seconds = Math.max(1L, (cooldownMillis + 999L) / 1000L);
            player.sendMessage(Main.prefix + "You must wait §a" + seconds + "s §fbefore teleporting again.");
            return false;
        }

        cancelPendingTeleport(player.getUniqueId(), null);

        int delaySeconds = player.hasPermission(Perms.TELEPORT_BYPASS_DELAY)
                ? 0
                : config().getInt("Settings.Teleports.DelaySeconds", 3);
        delaySeconds = Math.max(0, delaySeconds);

        PendingTeleport pending = new PendingTeleport(
                player.getUniqueId(),
                player.getLocation().clone(),
                fixedDestination == null ? null : fixedDestination.clone(),
                destinationPlayerId,
                reason == null ? TeleportReason.WARP : reason,
                normalizeDestinationName(destinationName),
                System.currentTimeMillis() + (delaySeconds * 1000L));

        if (delaySeconds <= 0) {
            return executeTeleport(pending);
        }

        PENDING_TELEPORTS.put(player.getUniqueId(), pending);
        player.sendMessage(Main.prefix + "Teleporting to §a" + pending.destinationName()
                + " §fin §a" + delaySeconds + "s§f. Do not move or take damage.");
        return true;
    }

    public static void handleMovement(Player player, Location from, Location to) {
        if (player == null || from == null || to == null
                || !config().getBoolean("Settings.Teleports.CancelOnMove", true)) {
            return;
        }
        if (!PENDING_TELEPORTS.containsKey(player.getUniqueId())) {
            return;
        }

        boolean movedBlock = from.getWorld() != to.getWorld()
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
        if (movedBlock) {
            cancelPendingTeleport(player.getUniqueId(),
                    "Teleport cancelled because you moved.");
        }
    }

    public static void handleDamage(Player player) {
        if (player == null || !config().getBoolean("Settings.Teleports.CancelOnDamage", true)) {
            return;
        }
        if (PENDING_TELEPORTS.containsKey(player.getUniqueId())) {
            cancelPendingTeleport(player.getUniqueId(),
                    "Teleport cancelled because you took damage.");
        }
    }

    public static void recordExternalTeleport(Player player, Location from) {
        if (player == null || from == null || !isBackEnabled()
                || !config().getBoolean("Settings.Teleports.Back.TrackExternalTeleports", true)
                || INTERNAL_TELEPORTS.contains(player.getUniqueId())) {
            return;
        }
        rememberBackLocation(player.getUniqueId(), from);
    }

    public static void recordDeath(Player player) {
        if (player == null || !isBackEnabled()
                || !config().getBoolean("Settings.Teleports.Back.OnDeath", true)) {
            return;
        }
        rememberBackLocation(player.getUniqueId(), player.getLocation());
    }

    public static void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        cancelPendingTeleport(playerId, null);
        COOLDOWNS.remove(playerId);
        LAST_REQUEST_AT.remove(playerId);
        if (!persistBackLocations()) {
            BACK_LOCATIONS.remove(playerId);
        }

        LinkedHashMap<UUID, PlayerTeleportRequest> incoming = INCOMING_REQUESTS.remove(playerId);
        if (incoming != null) {
            for (PlayerTeleportRequest request : incoming.values()) {
                Player requester = Bukkit.getPlayer(request.requesterId());
                if (requester != null) {
                    requester.sendMessage(Main.prefix + player.getName()
                            + " went offline. Your teleport request was cancelled.");
                }
            }
        }

        for (var iterator = INCOMING_REQUESTS.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, LinkedHashMap<UUID, PlayerTeleportRequest>> entry = iterator.next();
            PlayerTeleportRequest removed = entry.getValue().remove(playerId);
            if (removed != null) {
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null) {
                    target.sendMessage(Main.prefix + player.getName()
                            + " went offline. Their teleport request was cancelled.");
                }
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static boolean sendTeleportRequest(
            Player requester,
            Player target,
            TeleportRequestType type) {
        if (requester == null || target == null) {
            return false;
        }
        if (!isTpaEnabled()) {
            requester.sendMessage(Main.prefix + "Player teleport requests are currently disabled.");
            return false;
        }
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage(Main.prefix + "You cannot send a teleport request to yourself.");
            return false;
        }
        if (!target.isOnline()) {
            requester.sendMessage(Main.prefix + "That player is not online.");
            return false;
        }

        long now = System.currentTimeMillis();
        if (!requester.hasPermission(Perms.TELEPORT_BYPASS_COOLDOWN)) {
            int requestCooldownSeconds = Math.max(0,
                    config().getInt("Settings.Teleports.TPA.RequestCooldownSeconds", 10));
            long lastRequest = LAST_REQUEST_AT.getOrDefault(requester.getUniqueId(), 0L);
            long remaining = (lastRequest + requestCooldownSeconds * 1000L) - now;
            if (remaining > 0L) {
                long seconds = Math.max(1L, (remaining + 999L) / 1000L);
                requester.sendMessage(Main.prefix + "Wait §a" + seconds
                        + "s §fbefore sending another teleport request.");
                return false;
            }
        }

        int timeoutSeconds = Math.max(5,
                config().getInt("Settings.Teleports.TPA.RequestTimeoutSeconds", 60));
        PlayerTeleportRequest request = new PlayerTeleportRequest(
                requester.getUniqueId(),
                target.getUniqueId(),
                type == null ? TeleportRequestType.TO_TARGET : type,
                now,
                now + timeoutSeconds * 1000L);

        INCOMING_REQUESTS
                .computeIfAbsent(target.getUniqueId(), ignored -> new LinkedHashMap<>())
                .put(requester.getUniqueId(), request);
        LAST_REQUEST_AT.put(requester.getUniqueId(), now);

        if (request.type() == TeleportRequestType.TO_TARGET) {
            requester.sendMessage(Main.prefix + "Teleport request sent to §a" + target.getName() + "§f.");
            target.sendMessage(Main.prefix + "§a" + requester.getName()
                    + " §fwants to teleport to you.");
        } else {
            requester.sendMessage(Main.prefix + "Teleport-here request sent to §a" + target.getName() + "§f.");
            target.sendMessage(Main.prefix + "§a" + requester.getName()
                    + " §fwants you to teleport to them.");
        }
        target.sendMessage(Main.prefix + "Use §a/tpaccept " + requester.getName()
                + " §for §a/tpdeny " + requester.getName() + "§f. Expires in §a"
                + timeoutSeconds + "s§f.");
        return true;
    }

    public static boolean acceptRequest(Player target, String requesterName) {
        PlayerTeleportRequest request = resolveIncomingRequest(target, requesterName, true);
        if (request == null) {
            return false;
        }
        removeRequest(request);

        Player requester = Bukkit.getPlayer(request.requesterId());
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(Main.prefix + "That player is no longer online.");
            return false;
        }

        boolean scheduled;
        if (request.type() == TeleportRequestType.TO_TARGET) {
            scheduled = requestTeleportToPlayer(
                    requester,
                    target,
                    TeleportReason.TPA,
                    target.getName());
        } else {
            scheduled = requestTeleportToPlayer(
                    target,
                    requester,
                    TeleportReason.TPA_HERE,
                    requester.getName());
        }

        if (!scheduled) {
            target.sendMessage(Main.prefix + "The teleport could not be started.");
            return false;
        }

        target.sendMessage(Main.prefix + "Accepted the teleport request from §a"
                + requester.getName() + "§f.");
        requester.sendMessage(Main.prefix + "§a" + target.getName()
                + " §faccepted your teleport request.");
        return true;
    }

    public static boolean denyRequest(Player target, String requesterName) {
        PlayerTeleportRequest request = resolveIncomingRequest(target, requesterName, true);
        if (request == null) {
            return false;
        }
        removeRequest(request);

        Player requester = Bukkit.getPlayer(request.requesterId());
        target.sendMessage(Main.prefix + "Denied the teleport request"
                + (requester == null ? "." : " from §a" + requester.getName() + "§f."));
        if (requester != null) {
            requester.sendMessage(Main.prefix + "§a" + target.getName()
                    + " §fdenied your teleport request.");
        }
        return true;
    }

    public static int cancelOutgoingRequests(Player requester, String targetName) {
        if (requester == null) {
            return 0;
        }

        UUID requesterId = requester.getUniqueId();
        int removedCount = 0;
        List<UUID> emptyTargets = new ArrayList<>();

        for (Map.Entry<UUID, LinkedHashMap<UUID, PlayerTeleportRequest>> entry : INCOMING_REQUESTS.entrySet()) {
            if (targetName != null && !targetName.isBlank()) {
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target == null || !target.getName().equalsIgnoreCase(targetName)) {
                    continue;
                }
            }

            PlayerTeleportRequest removed = entry.getValue().remove(requesterId);
            if (removed != null) {
                removedCount++;
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null) {
                    target.sendMessage(Main.prefix + "§a" + requester.getName()
                            + " §fcancelled their teleport request.");
                }
            }
            if (entry.getValue().isEmpty()) {
                emptyTargets.add(entry.getKey());
            }
        }

        for (UUID targetId : emptyTargets) {
            INCOMING_REQUESTS.remove(targetId);
        }
        return removedCount;
    }

    public static List<String> incomingRequesterNames(Player target) {
        if (target == null) {
            return List.of();
        }
        purgeExpiredRequests(false);
        LinkedHashMap<UUID, PlayerTeleportRequest> incoming = INCOMING_REQUESTS.get(target.getUniqueId());
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (UUID requesterId : incoming.keySet()) {
            Player requester = Bukkit.getPlayer(requesterId);
            if (requester != null) {
                names.add(requester.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    public static List<String> outgoingTargetNames(Player requester) {
        if (requester == null) {
            return List.of();
        }
        purgeExpiredRequests(false);
        List<String> names = new ArrayList<>();
        UUID requesterId = requester.getUniqueId();
        for (Map.Entry<UUID, LinkedHashMap<UUID, PlayerTeleportRequest>> entry : INCOMING_REQUESTS.entrySet()) {
            if (entry.getValue().containsKey(requesterId)) {
                Player target = Bukkit.getPlayer(entry.getKey());
                if (target != null) {
                    names.add(target.getName());
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    private static PlayerTeleportRequest resolveIncomingRequest(
            Player target,
            String requesterName,
            boolean sendFeedback) {
        if (target == null) {
            return null;
        }
        purgeExpiredRequests(false);

        LinkedHashMap<UUID, PlayerTeleportRequest> incoming = INCOMING_REQUESTS.get(target.getUniqueId());
        if (incoming == null || incoming.isEmpty()) {
            if (sendFeedback) {
                target.sendMessage(Main.prefix + "You have no pending teleport requests.");
            }
            return null;
        }

        if (requesterName != null && !requesterName.isBlank()) {
            for (Map.Entry<UUID, PlayerTeleportRequest> entry : incoming.entrySet()) {
                Player requester = Bukkit.getPlayer(entry.getKey());
                if (requester != null && requester.getName().equalsIgnoreCase(requesterName)) {
                    return entry.getValue();
                }
            }
            if (sendFeedback) {
                target.sendMessage(Main.prefix + "No pending teleport request from §a"
                        + requesterName + "§f.");
            }
            return null;
        }

        if (incoming.size() == 1) {
            return incoming.values().iterator().next();
        }

        if (sendFeedback) {
            target.sendMessage(Main.prefix + "You have multiple pending requests: §a"
                    + String.join("§f, §a", incomingRequesterNames(target)) + "§f.");
            target.sendMessage(Main.prefix + "Use §a/tpaccept <player> §for §a/tpdeny <player>§f.");
        }
        return null;
    }

    private static void removeRequest(PlayerTeleportRequest request) {
        LinkedHashMap<UUID, PlayerTeleportRequest> incoming = INCOMING_REQUESTS.get(request.targetId());
        if (incoming == null) {
            return;
        }
        incoming.remove(request.requesterId());
        if (incoming.isEmpty()) {
            INCOMING_REQUESTS.remove(request.targetId());
        }
    }

    private static void tick() {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<PendingTeleport> due = new ArrayList<>();
        for (PendingTeleport pending : PENDING_TELEPORTS.values()) {
            if (pending.executeAtMillis() <= now) {
                due.add(pending);
            }
        }
        for (PendingTeleport pending : due) {
            PENDING_TELEPORTS.remove(pending.playerId());
            executeTeleport(pending);
        }

        purgeExpiredRequests(true);
        COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static void purgeExpiredRequests(boolean notify) {
        long now = System.currentTimeMillis();
        List<PlayerTeleportRequest> expired = new ArrayList<>();
        for (LinkedHashMap<UUID, PlayerTeleportRequest> requests : INCOMING_REQUESTS.values()) {
            for (PlayerTeleportRequest request : requests.values()) {
                if (request.expiresAtMillis() <= now) {
                    expired.add(request);
                }
            }
        }

        for (PlayerTeleportRequest request : expired) {
            removeRequest(request);
            if (!notify) {
                continue;
            }
            Player requester = Bukkit.getPlayer(request.requesterId());
            Player target = Bukkit.getPlayer(request.targetId());
            if (requester != null) {
                requester.sendMessage(Main.prefix + "Your teleport request expired.");
            }
            if (target != null) {
                target.sendMessage(Main.prefix + "A teleport request from §a"
                        + (requester == null ? "an offline player" : requester.getName())
                        + " §fexpired.");
            }
        }
    }

    private static boolean executeTeleport(PendingTeleport pending) {
        Player player = Bukkit.getPlayer(pending.playerId());
        if (player == null || !player.isOnline()) {
            return false;
        }

        Location requested = resolveDestination(pending);
        if (!validLocation(requested)) {
            player.sendMessage(Main.prefix + "Teleport cancelled because the destination is unavailable.");
            return false;
        }

        Location destination = findSafeDestination(requested);
        if (destination == null) {
            player.sendMessage(Main.prefix + "Teleport cancelled because no safe location could be found nearby.");
            return false;
        }

        Location from = player.getLocation().clone();
        INTERNAL_TELEPORTS.add(player.getUniqueId());
        boolean success;
        try {
            success = player.teleport(destination);
        } catch (RuntimeException exception) {
            success = false;
            if (plugin != null) {
                plugin.getLogger().warning("Teleport failed for " + player.getName()
                        + ": " + exception.getMessage());
            }
        } finally {
            INTERNAL_TELEPORTS.remove(player.getUniqueId());
        }

        if (!success) {
            player.sendMessage(Main.prefix + "Teleport failed. Please try again.");
            return false;
        }

        if (isBackEnabled()) {
            rememberBackLocation(player.getUniqueId(), from);
        }

        if (!player.hasPermission(Perms.TELEPORT_BYPASS_COOLDOWN)) {
            int cooldownSeconds = Math.max(0,
                    config().getInt("Settings.Teleports.CooldownSeconds", 5));
            if (cooldownSeconds > 0) {
                COOLDOWNS.put(player.getUniqueId(),
                        System.currentTimeMillis() + cooldownSeconds * 1000L);
            }
        }

        player.sendMessage(Main.prefix + "Teleported to §a" + pending.destinationName() + "§f.");
        return true;
    }

    private static Location resolveDestination(PendingTeleport pending) {
        if (pending.destinationPlayerId() != null) {
            Player target = Bukkit.getPlayer(pending.destinationPlayerId());
            return target == null || !target.isOnline() ? null : target.getLocation().clone();
        }
        return pending.fixedDestination() == null ? null : pending.fixedDestination().clone();
    }

    private static Location findSafeDestination(Location requested) {
        if (!config().getBoolean("Settings.Teleports.SafeLocation.Enabled", true)) {
            return requested.clone();
        }
        if (isSafe(requested)) {
            return requested.clone();
        }

        int horizontalRadius = Math.max(0,
                config().getInt("Settings.Teleports.SafeLocation.HorizontalRadius", 2));
        int verticalRadius = Math.max(0,
                config().getInt("Settings.Teleports.SafeLocation.VerticalRadius", 6));
        World world = requested.getWorld();
        int originX = requested.getBlockX();
        int originY = requested.getBlockY();
        int originZ = requested.getBlockZ();

        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int offset = 0; offset <= verticalRadius; offset++) {
                        int upY = originY + offset;
                        Location up = centeredCandidate(world, originX + dx, upY, originZ + dz, requested);
                        if (isSafe(up)) {
                            return up;
                        }
                        if (offset == 0) {
                            continue;
                        }
                        int downY = originY - offset;
                        Location down = centeredCandidate(world, originX + dx, downY, originZ + dz, requested);
                        if (isSafe(down)) {
                            return down;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static Location centeredCandidate(
            World world,
            int x,
            int y,
            int z,
            Location requested) {
        return new Location(world, x + 0.5D, y, z + 0.5D,
                requested.getYaw(), requested.getPitch());
    }

    private static boolean isSafe(Location location) {
        if (!validLocation(location)) {
            return false;
        }
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block floor = world.getBlockAt(x, y - 1, z);

        return feet.isPassable()
                && head.isPassable()
                && !feet.isLiquid()
                && !head.isLiquid()
                && floor.getType().isSolid()
                && !isHazard(feet.getType())
                && !isHazard(head.getType())
                && !isHazard(floor.getType());
    }

    private static boolean isHazard(Material material) {
        return material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.MAGMA_BLOCK
                || material == Material.CACTUS
                || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.POWDER_SNOW;
    }

    public static Location getBackLocation(Player player) {
        if (player == null || !isBackEnabled()) {
            return null;
        }
        Location memory = BACK_LOCATIONS.get(player.getUniqueId());
        if (memory != null) {
            return memory.clone();
        }
        if (persistBackLocations()) {
            Location stored = TeleportStorage.getBackLocation(player.getUniqueId());
            if (stored != null) {
                BACK_LOCATIONS.put(player.getUniqueId(), stored.clone());
                return stored;
            }
        }
        return null;
    }

    private static void rememberBackLocation(UUID playerId, Location location) {
        if (playerId == null || !validLocation(location)) {
            return;
        }
        Location copy = location.clone();
        BACK_LOCATIONS.put(playerId, copy);
        if (persistBackLocations()) {
            TeleportStorage.setBackLocation(playerId, copy);
        }
    }

    private static boolean persistBackLocations() {
        return config().getBoolean("Settings.Teleports.Back.PersistAcrossRestarts", false);
    }

    private static long remainingCooldownMillis(Player player) {
        if (player == null || player.hasPermission(Perms.TELEPORT_BYPASS_COOLDOWN)) {
            return 0L;
        }
        long until = COOLDOWNS.getOrDefault(player.getUniqueId(), 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private static void cancelPendingTeleport(UUID playerId, String message) {
        PendingTeleport removed = PENDING_TELEPORTS.remove(playerId);
        if (removed == null || message == null || message.isBlank()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Main.prefix + message);
        }
    }

    private static String normalizeDestinationName(String destinationName) {
        return destinationName == null || destinationName.isBlank()
                ? "destination"
                : destinationName;
    }

    private static boolean validLocation(Location location) {
        return location != null
                && location.getWorld() != null
                && Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ());
    }

    private static org.bukkit.configuration.file.FileConfiguration config() {
        return ConfigData.getInstance().getData();
    }

    private record PendingTeleport(
            UUID playerId,
            Location origin,
            Location fixedDestination,
            UUID destinationPlayerId,
            TeleportReason reason,
            String destinationName,
            long executeAtMillis) {
    }

    private record PlayerTeleportRequest(
            UUID requesterId,
            UUID targetId,
            TeleportRequestType type,
            long createdAtMillis,
            long expiresAtMillis) {
    }
}
