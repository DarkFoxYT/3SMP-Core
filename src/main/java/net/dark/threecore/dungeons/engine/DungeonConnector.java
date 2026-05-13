package net.dark.threecore.dungeons.engine;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public record DungeonConnector(
    String id,
    ConnectorRole role,
    Vector localPosition,
    BlockFace facing,
    int width,
    int height,
    ConnectorType type,
    ConnectorType requiredMatch,
    boolean required,
    VerticalDirection verticalDirection,
    int targetYOffset,
    SnapMode snapMode,
    Vector anchorPosition
) {
    public DungeonConnector {
        id = id == null || id.isBlank() ? role.name().toLowerCase(java.util.Locale.ROOT) : id;
        facing = facing == null ? BlockFace.NORTH : facing;
        width = Math.max(1, width);
        height = Math.max(1, height);
        type = type == null ? ConnectorType.NORMAL : type;
        requiredMatch = requiredMatch == null ? type : requiredMatch;
        verticalDirection = verticalDirection == null ? (type.vertical() ? VerticalDirection.UP : VerticalDirection.NONE) : verticalDirection;
        snapMode = snapMode == null ? (type.vertical() ? SnapMode.ANCHOR : SnapMode.CONNECTOR_FACE) : snapMode;
        anchorPosition = anchorPosition == null ? localPosition.clone() : anchorPosition.clone();
    }

    public boolean compatibleWith(DungeonConnector other) {
        if (other == null) return false;
        if (facing.getOppositeFace() != other.facing()) return false;
        if (width != other.width() || height != other.height()) return false;
        return requiredMatch.matches(other.type()) && other.requiredMatch().matches(type);
    }

    public Vector anchor() {
        return anchorPosition.clone();
    }

    public enum VerticalDirection {
        NONE,
        UP,
        DOWN
    }

    public enum SnapMode {
        CONNECTOR_FACE,
        CONNECTOR_CENTER,
        ANCHOR,
        FLOOR_ANCHOR
    }
}
