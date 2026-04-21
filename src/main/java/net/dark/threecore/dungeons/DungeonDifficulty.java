package net.dark.threecore.dungeons;

public enum DungeonDifficulty {
    EASY(1, 5),
    NORMAL(2, 8),
    HARD(3, 12),
    INSANE(4, 16),
    NIGHTMARE(5, 20);

    public final int level;
    public final int roomCount;

    DungeonDifficulty(int level, int roomCount) {
        this.level = level;
        this.roomCount = roomCount;
    }

    public DungeonDifficulty next() {
        DungeonDifficulty[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}