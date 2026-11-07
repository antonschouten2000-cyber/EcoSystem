package me.antonschouten.economy.Manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.antonschouten.economy.Main;
import me.antonschouten.economy.Transaction.TransactionReason;
import me.antonschouten.economy.Data.ChunkData;
import me.antonschouten.economy.Data.ConfigData;

public final class ChunkManager {

    private static final ChunkData CHUNK_DATA = ChunkData.getInstance();
    private static final ConfigData CONFIG_DATA = ConfigData.getInstance();
    private static final Map<ChunkKey, ChunkClaim> CLAIMS = new HashMap<>();

    public static void chunkHelpMessage(Player player) {
        player.sendMessage("");
        player.sendMessage("§8-------------- §6[Economy++ Chunks] §8--------------");
        player.sendMessage("§a/chunk §8- §fPrints the help message.");
        player.sendMessage("§a/chunk info §8- §fGet chunk information.");
        player.sendMessage("§a/chunk claim §8- §fClaim a chunk.");
        player.sendMessage("§a/chunk unclaim §8- §fUnclaim a chunk.");
        player.sendMessage("§a/chunk trust <player> §8- §fAdd a player to your chunk.");
        player.sendMessage("§a/chunk untrust <player> §8- §fRemove a player from your chunk.");
        player.sendMessage("§8-------------- §6[Economy++ Chunks] §8--------------");
    }

    public static int getMaxClaimsPerPlayer() {
        return Math.max(0, CONFIG_DATA.getData().getInt("Max-Claims-Per-Player", 3));
    }

    public static int getChunkPrice() {
        return Math.max(0, CONFIG_DATA.getData().getInt("Price-Per-Chunk-Claim", 1000));
    }

    public static long getClaimCount(UUID playerUUID) {
        if (playerUUID == null) {
            return 0L;
        }
        return CLAIMS.values().stream()
                .filter(claim -> claim.getOwner().equals(playerUUID))
                .count();
    }

    public static void sendChunkInfo(Player player, Chunk chunk) {
        if (player == null || chunk == null) {
            return;
        }

        ChunkClaim claim = getClaim(chunk);
        if (claim == null) {
            player.sendMessage(Main.prefix + "This chunk isn't claimed.");
            return;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : owner.getUniqueId().toString();

        player.sendMessage("§f§l🔍 Chunk Information:");
        player.sendMessage("§8» Owner: §a" + ownerName);

        Set<UUID> members = claim.getMembers();
        if (members.isEmpty()) {
            player.sendMessage("§8» Members: §aNone");
            return;
        }

        String memberList = members.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(member -> member.getName() != null ? member.getName() : member.getUniqueId().toString())
                .collect(Collectors.joining(", "));
        player.sendMessage("§8» Members: §a" + memberList);
    }

    public void loadClaims() {
        CLAIMS.clear();

        List<Map<?, ?>> serializedClaims = CHUNK_DATA.getData().getMapList("claims");
        int skipped = 0;

        for (Map<?, ?> map : serializedClaims) {
            try {
                UUID owner = UUID.fromString(String.valueOf(map.get("owner")));
                String world = String.valueOf(map.get("world"));
                int x = asInt(map.get("x"));
                int z = asInt(map.get("z"));

                if (world.isBlank() || "null".equalsIgnoreCase(world)) {
                    skipped++;
                    continue;
                }

                ChunkClaim claim = new ChunkClaim(owner, world, x, z);
                Object rawMembers = map.get("members");
                if (rawMembers instanceof Iterable<?> iterable) {
                    for (Object rawMember : iterable) {
                        try {
                            claim.addMember(UUID.fromString(String.valueOf(rawMember)));
                        } catch (IllegalArgumentException ignored) {
                            // Skip malformed member UUIDs, but keep the claim.
                        }
                    }
                }

                CLAIMS.put(new ChunkKey(world, x, z), claim);
            } catch (RuntimeException exception) {
                skipped++;
            }
        }

        if (skipped > 0 && Main.getInstance() != null) {
            Main.getInstance().getLogger().warning("Skipped " + skipped + " malformed chunk claim(s) while loading ChunkData.yml.");
        }
    }

    public static void saveClaims() {
        List<Map<String, Object>> serializedClaims = new ArrayList<>(CLAIMS.size());

        for (ChunkClaim claim : CLAIMS.values()) {
            Map<String, Object> map = new HashMap<>();
            map.put("owner", claim.getOwner().toString());
            map.put("world", claim.getWorld());
            map.put("x", claim.getChunkX());
            map.put("z", claim.getChunkZ());
            map.put("members", claim.getMembers().stream().map(UUID::toString).toList());
            serializedClaims.add(map);
        }

        CHUNK_DATA.getData().set("claims", serializedClaims);
        DataSaveManager.markChunkDataDirty();
    }

    public static boolean isClaimed(Chunk chunk) {
        return getClaim(chunk) != null;
    }

    public static ChunkClaim getClaim(Chunk chunk) {
        if (chunk == null) {
            return null;
        }
        return CLAIMS.get(ChunkKey.of(chunk));
    }

    public static boolean claimChunk(Player player, Chunk chunk) {
        if (player == null || chunk == null) {
            return false;
        }

        if (isClaimed(chunk)) {
            player.sendMessage(Main.prefix + "This chunk is already claimed.");
            return false;
        }

        UUID uuid = player.getUniqueId();
        int maxClaims = getMaxClaimsPerPlayer();
        if (maxClaims <= 0 || getClaimCount(uuid) >= maxClaims) {
            player.sendMessage(Main.prefix + "You have reached the maximum number of chunk claims.");
            return false;
        }

        int cost = getChunkPrice();
        if (cost > 0 && !EconomyManager.tryWithdraw(player, cost, TransactionReason.CHUNK_CLAIM, player, "world=" + chunk.getWorld().getName() + ";x=" + chunk.getX() + ";z=" + chunk.getZ())) {
            player.sendMessage(Main.prefix + "You don't have enough money to claim this chunk §a($" + cost + ")§f.");
            return false;
        }

        ChunkClaim claim = new ChunkClaim(uuid, chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        CLAIMS.put(ChunkKey.of(chunk), claim);
        saveClaims();
        player.sendMessage(Main.prefix + "Chunk successfully claimed.");
        return true;
    }

    public static boolean unclaimChunk(Player player, Chunk chunk) {
        if (player == null || chunk == null) {
            return false;
        }

        ChunkKey key = ChunkKey.of(chunk);
        ChunkClaim claim = CLAIMS.get(key);
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            return false;
        }

        CLAIMS.remove(key);
        saveClaims();
        return true;
    }

    public static boolean addTrustedMember(Player owner, Chunk chunk, UUID member) {
        if (owner == null || chunk == null || member == null || owner.getUniqueId().equals(member)) {
            return false;
        }

        ChunkClaim claim = getClaim(chunk);
        if (claim == null || !claim.getOwner().equals(owner.getUniqueId())) {
            return false;
        }

        boolean changed = claim.addMember(member);
        if (changed) {
            saveClaims();
        }
        return changed;
    }

    public static boolean removeTrustedMember(Player owner, Chunk chunk, UUID member) {
        if (owner == null || chunk == null || member == null) {
            return false;
        }

        ChunkClaim claim = getClaim(chunk);
        if (claim == null || !claim.getOwner().equals(owner.getUniqueId())) {
            return false;
        }

        boolean changed = claim.removeMember(member);
        if (changed) {
            saveClaims();
        }
        return changed;
    }

    public static boolean canBuild(Player player, Chunk chunk) {
        if (player == null || chunk == null) {
            return false;
        }

        ChunkClaim claim = getClaim(chunk);
        return claim == null
                || claim.getOwner().equals(player.getUniqueId())
                || claim.isMember(player.getUniqueId());
    }

    public static int getLoadedClaimCount() {
        return CLAIMS.size();
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private record ChunkKey(String world, int x, int z) {
        private static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }
}
