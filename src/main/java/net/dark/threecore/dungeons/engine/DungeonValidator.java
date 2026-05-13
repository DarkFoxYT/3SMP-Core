package net.dark.threecore.dungeons.engine;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class DungeonValidator {
    public DungeonValidationResult validate(DungeonLayout layout, boolean bossRequired) {
        DungeonValidationResult.Builder result = DungeonValidationResult.builder();
        if (layout == null || layout.rooms().isEmpty()) {
            result.fail("No rooms were placed.");
            return result.build();
        }
        long starts = layout.rooms().stream().filter(room -> room.definition().type() == RoomType.START).count();
        if (starts != 1) result.fail("Expected exactly one START room, found " + starts + ".");
        if (bossRequired && layout.bossRoom() == null) result.fail("Boss room is required but was not placed.");
        for (PlacedDungeonRoom room : layout.rooms()) {
            long entrances = room.connectors().stream().filter(connector -> connector.connector().role() == ConnectorRole.ENTRANCE).count();
            if (room.definition().type() == RoomType.START && entrances != 0) {
                result.fail("START room has an entrance: " + room.definition().id() + ".");
            } else if (room.definition().type() != RoomType.START && entrances < 1) {
                result.fail("Room " + room.definition().id() + " has no entrances.");
            }
            if (room.rotation() == null) result.fail("Room " + room.definition().id() + " has no rotation.");
        }
        for (int i = 0; i < layout.rooms().size(); i++) {
            BoundingBox a = layout.rooms().get(i).box();
            for (int j = i + 1; j < layout.rooms().size(); j++) {
                if (a.overlaps(layout.rooms().get(j).box())) result.fail("Room overlap: " + layout.rooms().get(i).definition().id() + " intersects " + layout.rooms().get(j).definition().id() + ".");
            }
        }
        Map<Integer, Set<Integer>> graph = new HashMap<>();
        Set<String> connected = new HashSet<>();
        Map<Integer, Integer> connectedEntrances = new HashMap<>();
        for (DungeonConnection connection : layout.connections()) {
            String fromKey = key(connection.fromRoom(), connection.fromConnector());
            String toKey = key(connection.toRoom(), connection.toConnector());
            if (!connected.add(fromKey)) result.fail("Connector used more than once: " + fromKey + ".");
            if (!connected.add(toKey)) result.fail("Connector used more than once: " + toKey + ".");
            PlacedDungeonRoom from = room(layout, connection.fromRoom());
            PlacedDungeonRoom to = room(layout, connection.toRoom());
            if (from == null || to == null) {
                result.fail("Connection references missing room: " + connection + ".");
                continue;
            }
            PlacedConnector a = connector(from, connection.fromConnector());
            PlacedConnector b = connector(to, connection.toConnector());
            if (a == null || b == null) {
                result.fail("Connection references missing connector: " + connection + ".");
                continue;
            }
            if (a.connector().role() == ConnectorRole.ENTRANCE) result.fail("Connection starts from an entrance: " + connection + ".");
            if (b.connector().role() != ConnectorRole.ENTRANCE) result.fail("Connection does not end at a room entrance: " + connection + ".");
            else connectedEntrances.merge(to.graphIndex(), 1, Integer::sum);
            if (!a.compatibleWith(b)) result.fail("Connector mismatch between " + from.definition().id() + "." + a.connector().id() + " and " + to.definition().id() + "." + b.connector().id() + ".");
            Vector expected = a.center().add(offset(a.facing(), 1)).add(new Vector(0, connectorYOffset(a.connector(), b.connector()), 0));
            if (expected.distanceSquared(b.center()) > 0.0001D) {
                result.fail("Connector centers do not touch: " + from.definition().id() + "." + a.connector().id() + " -> " + to.definition().id() + "." + b.connector().id() + ".");
            }
            if (a.connector().type().vertical() || b.connector().type().vertical()) {
                double dy = Math.abs(a.worldPosition().getY() - b.worldPosition().getY());
                if (a.connector().verticalDirection() == DungeonConnector.VerticalDirection.UP && b.connector().verticalDirection() != DungeonConnector.VerticalDirection.DOWN) result.fail("UP connector does not meet DOWN connector: " + connection + ".");
                if (a.connector().verticalDirection() == DungeonConnector.VerticalDirection.DOWN && b.connector().verticalDirection() != DungeonConnector.VerticalDirection.UP) result.fail("DOWN connector does not meet UP connector: " + connection + ".");
                if (!a.connector().type().matches(b.connector().type())) result.fail("Vertical connector type mismatch: " + connection + ".");
                if (dy > 64.0D) result.fail("Vertical connection exceeds max sane span: " + connection + ".");
            }
            graph.computeIfAbsent(connection.fromRoom(), ignored -> new HashSet<>()).add(connection.toRoom());
            graph.computeIfAbsent(connection.toRoom(), ignored -> new HashSet<>()).add(connection.fromRoom());
        }
        for (PlacedDungeonRoom room : layout.rooms()) {
            if (room.definition().type() != RoomType.START && connectedEntrances.getOrDefault(room.graphIndex(), 0) != 1) {
                result.fail("Room " + room.definition().id() + " has " + connectedEntrances.getOrDefault(room.graphIndex(), 0) + " active entrances; expected exactly 1.");
            }
        }
        for (PlacedDungeonRoom room : layout.rooms()) {
            for (PlacedConnector connector : room.connectors()) {
                if (connector.connector().required() && !connected.contains(key(room.graphIndex(), connector.connector().id()))) {
                    result.fail("Required connector left floating: " + room.definition().id() + "." + connector.connector().id() + ".");
                }
            }
        }
        PlacedDungeonRoom start = layout.startRoom();
        if (start != null) {
            Set<Integer> reached = reachable(start.graphIndex(), graph);
            if (reached.size() != layout.rooms().size()) result.fail("Not all rooms are reachable from START. Reached " + reached.size() + "/" + layout.rooms().size() + ".");
            if (bossRequired && layout.bossRoom() != null && !reached.contains(layout.bossRoom().graphIndex())) result.fail("Boss room is not reachable from START.");
        }
        boolean hasEnd = layout.rooms().stream().anyMatch(room -> room.definition().type() == RoomType.BOSS || room.definition().type() == RoomType.DEAD_END || room.definition().type() == RoomType.TREASURE);
        if (!hasEnd) result.fail("Dungeon has no valid end path.");
        return result.build();
    }

    private Set<Integer> reachable(int start, Map<Integer, Set<Integer>> graph) {
        Set<Integer> reached = new HashSet<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        reached.add(start);
        while (!queue.isEmpty()) {
            int next = queue.poll();
            for (int edge : graph.getOrDefault(next, Set.of())) {
                if (reached.add(edge)) queue.add(edge);
            }
        }
        return reached;
    }

    private PlacedDungeonRoom room(DungeonLayout layout, int graphIndex) {
        return layout.rooms().stream().filter(room -> room.graphIndex() == graphIndex).findFirst().orElse(null);
    }

    private PlacedConnector connector(PlacedDungeonRoom room, String id) {
        return room.connectors().stream().filter(connector -> connector.connector().id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    private String key(int room, String connector) {
        return room + ":" + connector.toLowerCase(java.util.Locale.ROOT);
    }

    private org.bukkit.util.Vector offset(org.bukkit.block.BlockFace face, int gap) {
        return switch (face) {
            case NORTH -> new org.bukkit.util.Vector(0, 0, -gap);
            case SOUTH -> new org.bukkit.util.Vector(0, 0, gap);
            case EAST -> new org.bukkit.util.Vector(gap, 0, 0);
            case WEST -> new org.bukkit.util.Vector(-gap, 0, 0);
            case UP -> new org.bukkit.util.Vector(0, gap, 0);
            case DOWN -> new org.bukkit.util.Vector(0, -gap, 0);
            default -> new org.bukkit.util.Vector();
        };
    }

    private int connectorYOffset(DungeonConnector from, DungeonConnector to) {
        if (!from.type().vertical() && !to.type().vertical()) return 0;
        if (from.targetYOffset() != 0) return from.targetYOffset();
        if (to.targetYOffset() != 0) return -to.targetYOffset();
        return 0;
    }
}
