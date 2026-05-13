package net.dark.threecore.dungeons.runtime;

import net.dark.threecore.dungeons.engine.DungeonLayout;
import net.dark.threecore.dungeons.engine.PlacedDungeonRoom;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DungeonSession {
    private final UUID id;
    private final String dungeonId;
    private final String difficulty;
    private final Set<UUID> players;
    private final DungeonLayout layout;
    private final long startedAt;
    private PlacedDungeonRoom currentRoom;
    private boolean bossDefeated;

    public DungeonSession(UUID id, String dungeonId, Set<UUID> players, DungeonLayout layout) {
        this(id, dungeonId, "normal", players, layout);
    }

    public DungeonSession(UUID id, String dungeonId, String difficulty, Set<UUID> players, DungeonLayout layout) {
        this.id = id;
        this.dungeonId = dungeonId;
        this.difficulty = difficulty == null || difficulty.isBlank() ? "normal" : difficulty.toLowerCase(java.util.Locale.ROOT);
        this.players = new HashSet<>(players);
        this.layout = layout;
        this.startedAt = System.currentTimeMillis();
        this.currentRoom = layout == null ? null : layout.startRoom();
        this.bossDefeated = false;
    }

    public UUID id() { return id; }
    public String dungeonId() { return dungeonId; }
    public String difficulty() { return difficulty; }
    public Set<UUID> players() { return Set.copyOf(players); }
    public DungeonLayout layout() { return layout; }
    public long startedAt() { return startedAt; }
    public PlacedDungeonRoom currentRoom() { return currentRoom; }
    public void currentRoom(PlacedDungeonRoom currentRoom) { this.currentRoom = currentRoom; }
    public boolean bossDefeated() { return bossDefeated; }
    public void bossDefeated(boolean bossDefeated) { this.bossDefeated = bossDefeated; }
    public boolean contains(UUID uuid) { return players.contains(uuid); }
}
