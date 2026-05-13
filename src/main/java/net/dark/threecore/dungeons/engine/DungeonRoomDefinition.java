package net.dark.threecore.dungeons.engine;

import org.bukkit.util.Vector;
import org.bukkit.block.BlockFace;

import java.util.List;
import java.util.Set;

public record DungeonRoomDefinition(
    String id,
    String schematic,
    RoomType type,
    int weight,
    Vector size,
    List<DungeonConnector> entrances,
    List<DungeonConnector> exits,
    Set<String> requiredTags,
    Set<String> blockedTags,
    int difficultyWeight,
    List<DungeonMobSpawn> spawns,
    List<DungeonTrapDefinition> traps,
    List<net.dark.threecore.dungeons.boulder.BoulderTrapDefinition> boulderTraps,
    BlockFace baseFacing,
    FacingMarker facingMarker,
    RotationOrigin rotationOrigin
) {
    public DungeonRoomDefinition {
        weight = Math.max(1, weight);
        type = type == null ? RoomType.NORMAL : type;
        size = size == null ? new Vector(32, 16, 32) : size;
        entrances = entrances == null ? List.of() : List.copyOf(entrances);
        exits = exits == null ? List.of() : List.copyOf(exits);
        requiredTags = requiredTags == null ? Set.of() : Set.copyOf(requiredTags);
        blockedTags = blockedTags == null ? Set.of() : Set.copyOf(blockedTags);
        spawns = spawns == null ? List.of() : List.copyOf(spawns);
        traps = traps == null ? List.of() : List.copyOf(traps);
        boulderTraps = boulderTraps == null ? List.of() : List.copyOf(boulderTraps);
        baseFacing = baseFacing == null ? BlockFace.SOUTH : baseFacing;
        rotationOrigin = rotationOrigin == null ? RotationOrigin.roomMin() : rotationOrigin;
    }

    public List<DungeonConnector> connectors() {
        java.util.ArrayList<DungeonConnector> all = new java.util.ArrayList<>(entrances);
        all.addAll(exits);
        return all;
    }

    public DungeonRotation effectiveRotation(DungeonRotation requested) {
        return DungeonRotation.fromBaseFacing(baseFacing).add(requested == null ? DungeonRotation.NONE : requested);
    }

    public record FacingMarker(Vector localPosition, BlockFace facing) {
        public FacingMarker {
            localPosition = localPosition == null ? new Vector() : localPosition.clone();
            facing = facing == null ? BlockFace.SOUTH : facing;
        }
    }

    public record RotationOrigin(Mode mode, Vector localPosition) {
        public RotationOrigin {
            mode = mode == null ? Mode.ROOM_MIN : mode;
            localPosition = localPosition == null ? new Vector() : localPosition.clone();
        }

        public static RotationOrigin roomMin() {
            return new RotationOrigin(Mode.ROOM_MIN, new Vector());
        }

        public Vector resolve(Vector roomSize, FacingMarker marker) {
            return switch (mode) {
                case ROOM_CENTER -> new Vector(roomSize.getX() / 2.0D, 0.0D, roomSize.getZ() / 2.0D);
                case MARKER -> marker == null ? localPosition.clone() : marker.localPosition().clone();
                case CUSTOM -> localPosition.clone();
                case ROOM_MIN -> new Vector();
            };
        }

        public enum Mode {
            ROOM_MIN,
            ROOM_CENTER,
            MARKER,
            CUSTOM
        }
    }
}
