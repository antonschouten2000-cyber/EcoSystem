package me.antonschouten.economy.Manager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Chunk;

public final class ChunkClaim {

    private final UUID owner;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final Set<UUID> members = new HashSet<>();

    public ChunkClaim(UUID owner, String world, int chunkX, int chunkZ) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.world = Objects.requireNonNull(world, "world");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean addMember(UUID uuid) {
        return uuid != null && !owner.equals(uuid) && members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        return uuid != null && members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return uuid != null && members.contains(uuid);
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean matches(Chunk chunk) {
        return chunk != null
                && chunk.getX() == chunkX
                && chunk.getZ() == chunkZ
                && chunk.getWorld().getName().equals(world);
    }
}
