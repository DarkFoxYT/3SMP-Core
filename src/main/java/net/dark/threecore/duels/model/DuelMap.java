package net.dark.threecore.duels.model;

import org.bukkit.Location;

public record DuelMap(String id, String displayName, boolean enabled, String worldName, Location lobby, Location spawnA, Location spawnB, Location ffaSpawn, Location spectator, DuelGateRegion redGate, DuelGateRegion blueGate, Location redGateExit, Location blueGateExit, DuelGateRegion redGateCloseZone, DuelGateRegion blueGateCloseZone) {
    public DuelMap(String id, String displayName, boolean enabled, String worldName, Location lobby, Location spawnA, Location spawnB, Location spectator) {
        this(id, displayName, enabled, worldName, lobby, spawnA, spawnB, null, spectator, null, null, null, null, null, null);
    }

    public DuelMap(String id, String displayName, boolean enabled, String worldName, Location lobby, Location spawnA, Location spawnB, Location spectator, DuelGateRegion redGate, DuelGateRegion blueGate) {
        this(id, displayName, enabled, worldName, lobby, spawnA, spawnB, null, spectator, redGate, blueGate, null, null, null, null);
    }

    public DuelMap(String id, String displayName, boolean enabled, String worldName, Location lobby, Location spawnA, Location spawnB, Location spectator, DuelGateRegion redGate, DuelGateRegion blueGate, Location redGateExit, Location blueGateExit) {
        this(id, displayName, enabled, worldName, lobby, spawnA, spawnB, null, spectator, redGate, blueGate, redGateExit, blueGateExit, null, null);
    }

    public DuelMap withEnabled(boolean enabled) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withLobby(Location lobby) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withSpawnA(Location spawnA) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withSpawnB(Location spawnB) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withFfaSpawn(Location ffaSpawn) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withSpectator(Location spectator) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withRedGate(DuelGateRegion redGate) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withBlueGate(DuelGateRegion blueGate) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withGates(DuelGateRegion redGate, DuelGateRegion blueGate) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withRedGateExit(Location redGateExit) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withBlueGateExit(Location blueGateExit) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withRedGateCloseZone(DuelGateRegion redGateCloseZone) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }

    public DuelMap withBlueGateCloseZone(DuelGateRegion blueGateCloseZone) {
        return new DuelMap(id, displayName, enabled, worldName, lobby, spawnA, spawnB, ffaSpawn, spectator, redGate, blueGate, redGateExit, blueGateExit, redGateCloseZone, blueGateCloseZone);
    }
}
