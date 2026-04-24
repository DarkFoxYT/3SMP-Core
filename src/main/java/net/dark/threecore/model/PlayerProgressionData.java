package net.dark.threecore.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerProgressionData {
    private final UUID uuid;
    private final Set<String> unlockedPerks = new HashSet<>();
    private String activePrefix = "";
    private String activeTag = "";
    private String activeBadge = "";
    private String activeTrim = "";
    private String activeMessageColor = "";
    private String activeCosmetic = "";
    private String activeParticle = "";
    private String activeEffect = "";
    private int duelRating = 1000;
    private int duelWins;
    private int duelLosses;
    private int duelWinStreak;
    private int duelBestWinStreak;
    private int duelKills;
    private int duelDeaths;

    public PlayerProgressionData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() { return uuid; }
    public Set<String> unlockedPerks() { return unlockedPerks; }
    public String activePrefix() { return activePrefix; }
    public void activePrefix(String value) { this.activePrefix = value; }
    public String activeTag() { return activeTag; }
    public void activeTag(String value) { this.activeTag = value; }
    public String activeBadge() { return activeBadge; }
    public void activeBadge(String value) { this.activeBadge = value; }
    public String activeTrim() { return activeTrim; }
    public void activeTrim(String value) { this.activeTrim = value; }
    public String activeMessageColor() { return activeMessageColor; }
    public void activeMessageColor(String value) { this.activeMessageColor = value; }
    public String activeCosmetic() { return activeCosmetic; }
    public void activeCosmetic(String value) { this.activeCosmetic = value; }
    public String activeParticle() { return activeParticle; }
    public void activeParticle(String value) { this.activeParticle = value; }
    public String activeEffect() { return activeEffect; }
    public void activeEffect(String value) { this.activeEffect = value; }
    public int duelRating() { return duelRating; }
    public void duelRating(int value) { this.duelRating = Math.max(0, value); }
    public int duelWins() { return duelWins; }
    public void duelWins(int value) { this.duelWins = Math.max(0, value); }
    public int duelLosses() { return duelLosses; }
    public void duelLosses(int value) { this.duelLosses = Math.max(0, value); }
    public int duelWinStreak() { return duelWinStreak; }
    public void duelWinStreak(int value) { this.duelWinStreak = Math.max(0, value); }
    public int duelBestWinStreak() { return duelBestWinStreak; }
    public void duelBestWinStreak(int value) { this.duelBestWinStreak = Math.max(0, value); }
    public int duelKills() { return duelKills; }
    public void duelKills(int value) { this.duelKills = Math.max(0, value); }
    public int duelDeaths() { return duelDeaths; }
    public void duelDeaths(int value) { this.duelDeaths = Math.max(0, value); }
    public void recordDuel(boolean win) { if (win) { duelWins++; duelWinStreak++; duelBestWinStreak = Math.max(duelBestWinStreak, duelWinStreak); } else { duelLosses++; duelWinStreak = 0; } }
}
