package net.dark.threecore.dungeons.engine;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public record PlacedConnector(
    DungeonRoomDefinition room,
    DungeonConnector connector,
    String world,
    int worldRoomIndex,
    Vector localPosition,
    Vector worldPosition,
    BlockFace facing
) {
    public static PlacedConnector from(DungeonRoomDefinition room, DungeonConnector connector, String world, int graphIndex, Vector origin, RoomTransform transform) {
        DungeonConnector local = transform.rotatedConnector(connector);
        Vector localPosition = local.localPosition();
        Vector worldPosition = origin.clone().add(localPosition);
        return new PlacedConnector(room, local, world, graphIndex, localPosition, worldPosition, local.facing());
    }

    public Location location() {
        org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    public Vector center() {
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + Math.max(1, connector.height()) / 2.0D;
        double z = worldPosition.getZ() + 0.5D;
        if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
            x = worldPosition.getX() + connector.width() / 2.0D;
        } else if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
            z = worldPosition.getZ() + connector.width() / 2.0D;
        }
        return new Vector(x, y, z);
    }

    public boolean compatibleWith(PlacedConnector other) {
        return other != null
            && facing.getOppositeFace() == other.facing()
            && connector.width() == other.connector().width()
            && connector.height() == other.connector().height()
            && connector.requiredMatch().matches(other.connector().type())
            && other.connector().requiredMatch().matches(connector.type());
    }
}
