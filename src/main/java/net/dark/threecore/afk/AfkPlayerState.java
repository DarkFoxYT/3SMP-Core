package net.dark.threecore.afk;

import java.util.UUID;

public final class AfkPlayerState {
    private final UUID uuid;
    private long lastRealMovementAt;
    private long lastRewardAt;
    private boolean afk;
    private String zoneId = "";
    private int stationaryTicks;

    public AfkPlayerState(UUID uuid) {
        this.uuid = uuid;
        this.lastRealMovementAt = System.currentTimeMillis();
        this.lastRewardAt = 0L;
    }

    public UUID uuid() {
        return uuid;
    }

    public long lastRealMovementAt() {
        return lastRealMovementAt;
    }

    public void lastRealMovementAt(long value) {
        this.lastRealMovementAt = value;
    }

    public long lastRewardAt() {
        return lastRewardAt;
    }

    public void lastRewardAt(long value) {
        this.lastRewardAt = value;
    }

    public boolean afk() {
        return afk;
    }

    public void afk(boolean value) {
        this.afk = value;
    }

    public String zoneId() {
        return zoneId;
    }

    public void zoneId(String value) {
        this.zoneId = value == null ? "" : value;
    }

    public int stationaryTicks() {
        return stationaryTicks;
    }

    public void stationaryTicks(int value) {
        this.stationaryTicks = Math.max(0, value);
    }

    public void touchMovement() {
        lastRealMovementAt = System.currentTimeMillis();
        stationaryTicks = 0;
    }
}
