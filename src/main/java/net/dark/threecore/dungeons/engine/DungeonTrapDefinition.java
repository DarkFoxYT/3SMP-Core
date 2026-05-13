package net.dark.threecore.dungeons.engine;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public record DungeonTrapDefinition(
    String id,
    String type,
    Vector localPosition,
    BlockFace facing,
    String mythicMobId
) {
    public DungeonTrapDefinition {
        id = id == null || id.isBlank() ? "trap" : id;
        type = type == null || type.isBlank() ? "generic" : type.toLowerCase(java.util.Locale.ROOT);
        localPosition = localPosition == null ? new Vector() : localPosition.clone();
        facing = facing == null ? BlockFace.SOUTH : facing;
        mythicMobId = mythicMobId == null ? "" : mythicMobId;
    }
}
