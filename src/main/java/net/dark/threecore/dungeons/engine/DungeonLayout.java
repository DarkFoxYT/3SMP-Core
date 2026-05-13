package net.dark.threecore.dungeons.engine;

import java.util.List;

public record DungeonLayout(
    String dungeonId,
    String world,
    List<PlacedDungeonRoom> rooms,
    List<DungeonConnection> connections,
    long planningMillis,
    List<String> debug
) {
    public PlacedDungeonRoom startRoom() {
        return rooms.stream().filter(room -> room.definition().type() == RoomType.START).findFirst().orElse(rooms.isEmpty() ? null : rooms.get(0));
    }

    public PlacedDungeonRoom bossRoom() {
        return rooms.stream().filter(room -> room.definition().type() == RoomType.BOSS).findFirst().orElse(null);
    }
}
