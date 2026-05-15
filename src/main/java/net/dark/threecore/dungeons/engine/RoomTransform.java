package net.dark.threecore.dungeons.engine;

import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class RoomTransform {
    private final Vector originWorldPos;
    private final DungeonRotation rotation;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Vector pivot;
    private final Vector blockBoundsMin;
    private final Vector pointBoundsMin;
    private final Vector rotatedSize;

    public RoomTransform(Vector originWorldPos, DungeonRotation rotation, int sizeX, int sizeY, int sizeZ) {
        this(originWorldPos, rotation, sizeX, sizeY, sizeZ, new Vector());
    }

    public RoomTransform(Vector originWorldPos, DungeonRotation rotation, int sizeX, int sizeY, int sizeZ, Vector pivot) {
        this.originWorldPos = originWorldPos == null ? new Vector() : originWorldPos.clone();
        this.rotation = rotation == null ? DungeonRotation.NONE : rotation;
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
        this.pivot = pivot == null ? new Vector() : pivot.clone();
        this.blockBoundsMin = computeBlockBoundsMin();
        this.pointBoundsMin = computePointBoundsMin();
        this.rotatedSize = computeRotatedSize();
    }

    public Vector originWorldPos() {
        return originWorldPos.clone();
    }

    public DungeonRotation rotation() {
        return rotation;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public Vector rotatedSize() {
        return rotatedSize.clone();
    }

    public Vector localToWorld(Vector local) {
        return originWorldPos.clone().add(rotateLocal(local));
    }

    public Vector localPointToWorld(Vector local) {
        return originWorldPos.clone().add(rotatePoint(local));
    }

    public BlockFace localFacingToWorld(BlockFace facing) {
        return rotation.rotate(facing);
    }

    public Vector worldToLocal(Vector world) {
        return rotateLocalInverse(world.clone().subtract(originWorldPos));
    }

    public BoundingBox rotatedBounds() {
        return new BoundingBox(
            originWorldPos.getX(),
            originWorldPos.getY(),
            originWorldPos.getZ(),
            originWorldPos.getX() + rotatedSize().getX(),
            originWorldPos.getY() + sizeY(),
            originWorldPos.getZ() + rotatedSize().getZ()
        );
    }

    public DungeonConnector rotatedConnector(DungeonConnector connector) {
        if (connector == null) return null;
        Vector rotated = rotateLocal(connector.localPosition());
        return new DungeonConnector(
            connector.id(),
            connector.role(),
            rotated,
            localFacingToWorld(connector.facing()),
            connector.width(),
            connector.height(),
            connector.type(),
            connector.requiredMatch(),
            connector.required(),
            connector.verticalDirection(),
            connector.targetYOffset(),
            connector.snapMode(),
            rotateLocal(connector.anchor())
        );
    }

    public DungeonConnector rotateConnector(DungeonConnector connector) {
        return rotatedConnector(connector);
    }

    public Vector localConnectorCenter(DungeonConnector connector) {
        double x = connector.localPosition().getX() + 0.5D;
        double y = connector.localPosition().getY() + Math.max(1, connector.height()) / 2.0D;
        double z = connector.localPosition().getZ() + 0.5D;
        if (connector.facing() == org.bukkit.block.BlockFace.NORTH || connector.facing() == org.bukkit.block.BlockFace.SOUTH) {
            x = connector.localPosition().getX() + connector.width() / 2.0D;
        } else if (connector.facing() == org.bukkit.block.BlockFace.EAST || connector.facing() == org.bukkit.block.BlockFace.WEST) {
            z = connector.localPosition().getZ() + connector.width() / 2.0D;
        }
        return rotatePoint(new Vector(x, y, z));
    }

    public Vector localFacingOffset(BlockFace facing) {
        if (facing == null) return new Vector();
        return switch (facing) {
            case NORTH -> new Vector(0.0D, 0.0D, -1.0D);
            case SOUTH -> new Vector(0.0D, 0.0D, 1.0D);
            case EAST -> new Vector(1.0D, 0.0D, 0.0D);
            case WEST -> new Vector(-1.0D, 0.0D, 0.0D);
            case UP -> new Vector(0.0D, 1.0D, 0.0D);
            case DOWN -> new Vector(0.0D, -1.0D, 0.0D);
            default -> new Vector();
        };
    }

    public Vector connectorWorldCenter(DungeonConnector connector) {
        if (connector == null) return new Vector();
        return originWorldPos.clone().add(localConnectorCenter(connector));
    }

    public DungeonConnector projectedConnector(DungeonConnector connector) {
        return rotatedConnector(connector);
    }

    private Vector rotateLocal(Vector local) {
        return rotateAboutPivot(local).subtract(blockBoundsMin);
    }

    private Vector rotatePoint(Vector local) {
        return rotateAboutPivot(local).subtract(pointBoundsMin);
    }

    private Vector rotateAboutPivot(Vector local) {
        double x = local.getX();
        double y = local.getY();
        double z = local.getZ();
        double dx = x - pivot.getX();
        double dz = z - pivot.getZ();
        return switch (rotation) {
            case CLOCKWISE_90 -> new Vector(pivot.getX() - dz, y, pivot.getZ() + dx);
            case CLOCKWISE_180 -> new Vector(pivot.getX() - dx, y, pivot.getZ() - dz);
            case COUNTERCLOCKWISE_90 -> new Vector(pivot.getX() + dz, y, pivot.getZ() - dx);
            case NONE -> new Vector(x, y, z);
        };
    }

    private Vector computeBlockBoundsMin() {
        Vector[] corners = blockCorners();
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        for (Vector corner : corners) {
            Vector rotated = rotateAboutPivot(corner);
            minX = Math.min(minX, rotated.getX());
            minZ = Math.min(minZ, rotated.getZ());
        }
        return new Vector(minX, 0.0D, minZ);
    }

    private Vector computePointBoundsMin() {
        Vector[] corners = continuousCorners();
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        for (Vector corner : corners) {
            Vector rotated = rotateAboutPivot(corner);
            minX = Math.min(minX, rotated.getX());
            minZ = Math.min(minZ, rotated.getZ());
        }
        return new Vector(minX, 0.0D, minZ);
    }

    private Vector computeRotatedSize() {
        Vector[] corners = continuousCorners();
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Vector corner : corners) {
            Vector rotated = rotateAboutPivot(corner);
            minX = Math.min(minX, rotated.getX());
            minZ = Math.min(minZ, rotated.getZ());
            maxX = Math.max(maxX, rotated.getX());
            maxZ = Math.max(maxZ, rotated.getZ());
        }
        return new Vector(Math.round(maxX - minX), sizeY(), Math.round(maxZ - minZ));
    }

    private Vector[] continuousCorners() {
        return new Vector[] {
            new Vector(0, 0, 0),
            new Vector(sizeX(), 0, 0),
            new Vector(0, 0, sizeZ()),
            new Vector(sizeX(), 0, sizeZ())
        };
    }

    private Vector[] blockCorners() {
        int maxX = Math.max(0, sizeX() - 1);
        int maxZ = Math.max(0, sizeZ() - 1);
        return new Vector[] {
            new Vector(0, 0, 0),
            new Vector(maxX, 0, 0),
            new Vector(0, 0, maxZ),
            new Vector(maxX, 0, maxZ)
        };
    }

    private Vector rotateLocalInverse(Vector local) {
        double x = local.getX();
        double y = local.getY();
        double z = local.getZ();
        return switch (rotation) {
            case CLOCKWISE_90 -> new Vector(z, y, sizeX() - 1 - x);
            case CLOCKWISE_180 -> new Vector(sizeX() - 1 - x, y, sizeZ() - 1 - z);
            case COUNTERCLOCKWISE_90 -> new Vector(sizeZ() - 1 - z, y, x);
            case NONE -> new Vector(x, y, z);
        };
    }
}
