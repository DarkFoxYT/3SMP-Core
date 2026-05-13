package net.dark.threecore.dungeons.engine;

public enum ConnectorType {
    NORMAL,
    LARGE,
    STAIRS_UP,
    STAIRS_DOWN,
    LOCKED,
    BOSS_GATE;

    public boolean matches(ConnectorType other) {
        if (this == other) return true;
        return (this == STAIRS_UP && other == STAIRS_DOWN) || (this == STAIRS_DOWN && other == STAIRS_UP);
    }

    public boolean vertical() {
        return this == STAIRS_UP || this == STAIRS_DOWN;
    }
}
