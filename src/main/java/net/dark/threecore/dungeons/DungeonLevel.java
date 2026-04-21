package net.dark.threecore.dungeons;

public enum DungeonLevel {
    JUNGLE(1, "Jungle", false),
    DESERT(2, "Desert", true),
    VOLCANIC(3, "Volcanic", true),
    FROZEN(4, "Frozen", true),
    ANCIENT(5, "Ancient", true);

    public final int id;
    public final String displayName;
    public final boolean underDev;

    DungeonLevel(int id, String displayName, boolean underDev) {
        this.id = id;
        this.displayName = displayName;
        this.underDev = underDev;
    }

    public static DungeonLevel byId(int id) {
        for (DungeonLevel level : values()) {
            if (level.id == id) return level;
        }
        return JUNGLE;
    }
}