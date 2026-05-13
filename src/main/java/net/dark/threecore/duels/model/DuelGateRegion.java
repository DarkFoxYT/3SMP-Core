package net.dark.threecore.duels.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

public record DuelGateRegion(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public DuelGateRegion {
        int aX = Math.min(minX, maxX);
        int aY = Math.min(minY, maxY);
        int aZ = Math.min(minZ, maxZ);
        int bX = Math.max(minX, maxX);
        int bY = Math.max(minY, maxY);
        int bZ = Math.max(minZ, maxZ);
        minX = aX;
        minY = aY;
        minZ = aZ;
        maxX = bX;
        maxY = bY;
        maxZ = bZ;
    }

    public static DuelGateRegion from(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) return null;
        if (!first.getWorld().getName().equalsIgnoreCase(second.getWorld().getName())) return null;
        return new DuelGateRegion(
            first.getWorld().getName(),
            first.getBlockX(),
            first.getBlockY(),
            first.getBlockZ(),
            second.getBlockX(),
            second.getBlockY(),
            second.getBlockZ()
        );
    }

    public DuelGateRegion inWorld(World world) {
        if (world == null) return this;
        return new DuelGateRegion(world.getName(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    public int blockCount() {
        return Math.max(0, (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
    }

    public BoundingBox box() {
        return new BoundingBox(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return location.getWorld().getName().equalsIgnoreCase(worldName)
            && location.getBlockX() >= minX && location.getBlockX() <= maxX
            && location.getBlockY() >= minY && location.getBlockY() <= maxY
            && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public String dimensions() {
        return (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
    }
}
