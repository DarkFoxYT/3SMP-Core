package net.dark.threecore.duels.model;

import org.bukkit.Location;

public record DuelMap(String id, String displayName, boolean enabled, String worldName, Location lobby, Location spawnA, Location spawnB, Location spectator) {
    public DuelMap withEnabled(boolean enabled) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, spectator);
    }

    public DuelMap withLobby(Location lobby) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, spectator);
    }

    public DuelMap withSpawnA(Location spawnA) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, spectator);
    }

    public DuelMap withSpawnB(Location spawnB) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, spectator);
    }

    public DuelMap withSpectator(Location spectator) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, spectator);
    }
}
