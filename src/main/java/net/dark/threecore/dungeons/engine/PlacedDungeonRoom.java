package net.dark.threecore.dungeons.engine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public record PlacedDungeonRoom(
    DungeonRoomDefinition definition,
    String world,
    Vector origin,
    RoomTransform transform,
    DungeonRotation rotation,
    int graphIndex,
    List<PlacedConnector> connectors
) {
    public PlacedDungeonRoom {
        connectors = List.copyOf(connectors);
    }

    public BoundingBox box() {
        Vector size = transform.rotatedSize();
        return new BoundingBox(
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            origin.getX() + size.getX(),
            origin.getY() + size.getY(),
            origin.getZ() + size.getZ()
        );
    }

    public Location originLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, origin.getX(), origin.getY(), origin.getZ());
    }

    public static PlacedDungeonRoom place(DungeonRoomDefinition definition, String world, Vector origin, DungeonRotation rotation, int graphIndex) {
        Vector size = definition.size();
        DungeonRotation effectiveRotation = definition.effectiveRotation(rotation);
        Vector pivot = definition.rotationOrigin().resolve(size, definition.facingMarker());
        RoomTransform transform = new RoomTransform(origin.clone(), effectiveRotation, size.getBlockX(), size.getBlockY(), size.getBlockZ(), pivot);
        List<PlacedConnector> placed = new ArrayList<>();
        for (DungeonConnector connector : definition.connectors()) {
            placed.add(PlacedConnector.from(definition, connector, world, graphIndex, origin, transform));
        }
        return new PlacedDungeonRoom(definition, world, origin, transform, rotation, graphIndex, placed);
    }
}
