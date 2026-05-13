package net.dark.threecore.visual;

public record RankStyle(String id, String image, String prefix, String tabPrefix, String gradient, int sortWeight, String shadow) {
    public static RankStyle fallback() {
        return new RankStyle("default", "", "&7", "&7", "default", 999, "");
    }
}
