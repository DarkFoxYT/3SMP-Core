package net.dark.threecore.dungeons.engine;

public record DungeonConnection(
    int fromRoom,
    String fromConnector,
    int toRoom,
    String toConnector
) {
}
