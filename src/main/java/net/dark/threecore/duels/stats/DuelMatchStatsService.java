package net.dark.threecore.duels.stats;

import net.dark.threecore.duels.model.DuelMatch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DuelMatchStatsService {
    private final Map<UUID, DuelMatchStats> statsByMatch = new LinkedHashMap<>();

    public void start(DuelMatch match) {
        if (match == null) return;
        DuelMatchStats stats = new DuelMatchStats(match.id(), match.startedAt());
        for (UUID uuid : match.teamOne()) stats.player(uuid);
        for (UUID uuid : match.teamTwo()) stats.player(uuid);
        statsByMatch.put(match.id(), stats);
    }

    public DuelMatchStats snapshot(UUID matchId) {
        return statsByMatch.get(matchId);
    }

    public DuelMatchStats finish(UUID matchId, long endedAt) {
        DuelMatchStats stats = statsByMatch.remove(matchId);
        if (stats != null) stats.endedAt(endedAt);
        return stats;
    }

    public void clear(UUID matchId) {
        statsByMatch.remove(matchId);
    }

    public void clearAll() {
        statsByMatch.clear();
    }

    public void recordDamage(DuelMatch match, UUID attacker, UUID victim, double damage, boolean projectile) {
        if (match == null || attacker == null || victim == null || damage <= 0.0D) return;
        DuelMatchStats stats = statsByMatch.get(match.id());
        if (stats == null) return;
        PlayerDuelStats attackerStats = stats.player(attacker);
        PlayerDuelStats victimStats = stats.player(victim);
        attackerStats.damageDealt += damage;
        attackerStats.hitsLanded++;
        victimStats.damageTaken += damage;
        victimStats.hitsReceived++;
    }

    public void recordProjectileLaunched(DuelMatch match, UUID shooter) {
        PlayerDuelStats stats = playerStats(match, shooter);
        if (stats != null) stats.projectilesLaunched++;
    }

    public void recordProjectileHit(DuelMatch match, UUID shooter) {
        PlayerDuelStats stats = playerStats(match, shooter);
        if (stats != null) stats.projectilesHit++;
    }

    public void recordPotionUsed(DuelMatch match, UUID uuid) {
        PlayerDuelStats stats = playerStats(match, uuid);
        if (stats != null) stats.potionsUsed++;
    }

    public void recordGoldenApple(DuelMatch match, UUID uuid) {
        PlayerDuelStats stats = playerStats(match, uuid);
        if (stats != null) stats.goldenApples++;
    }

    public void recordTotem(DuelMatch match, UUID uuid) {
        PlayerDuelStats stats = playerStats(match, uuid);
        if (stats != null) stats.totemsPopped++;
    }

    public void recordCrystalPlaced(DuelMatch match, UUID uuid) {
        PlayerDuelStats stats = playerStats(match, uuid);
        if (stats != null) stats.crystalsPlaced++;
    }

    public void recordCrystalBroken(DuelMatch match, UUID uuid) {
        PlayerDuelStats stats = playerStats(match, uuid);
        if (stats != null) stats.crystalsBroken++;
    }

    private PlayerDuelStats playerStats(DuelMatch match, UUID uuid) {
        if (match == null || uuid == null) return null;
        DuelMatchStats stats = statsByMatch.get(match.id());
        return stats == null ? null : stats.player(uuid);
    }

    public static final class DuelMatchStats {
        private final UUID matchId;
        private final long startedAt;
        private long endedAt;
        private final Map<UUID, PlayerDuelStats> players = new LinkedHashMap<>();

        private DuelMatchStats(UUID matchId, long startedAt) {
            this.matchId = matchId;
            this.startedAt = startedAt;
            this.endedAt = System.currentTimeMillis();
        }

        public UUID matchId() { return matchId; }
        public long startedAt() { return startedAt; }
        public long endedAt() { return endedAt; }
        public long durationMillis() { return Math.max(0L, endedAt - startedAt); }
        public Map<UUID, PlayerDuelStats> players() { return Map.copyOf(players); }

        public PlayerDuelStats player(UUID uuid) {
            return players.computeIfAbsent(uuid, PlayerDuelStats::new);
        }

        private void endedAt(long endedAt) {
            this.endedAt = endedAt;
        }
    }

    public static final class PlayerDuelStats {
        private final UUID uuid;
        private double damageDealt;
        private double damageTaken;
        private int hitsLanded;
        private int hitsReceived;
        private int projectilesLaunched;
        private int projectilesHit;
        private int potionsUsed;
        private int goldenApples;
        private int totemsPopped;
        private int crystalsPlaced;
        private int crystalsBroken;

        private PlayerDuelStats(UUID uuid) {
            this.uuid = uuid;
        }

        public UUID uuid() { return uuid; }
        public double damageDealt() { return damageDealt; }
        public double damageTaken() { return damageTaken; }
        public int hitsLanded() { return hitsLanded; }
        public int hitsReceived() { return hitsReceived; }
        public int projectilesLaunched() { return projectilesLaunched; }
        public int projectilesHit() { return projectilesHit; }
        public int potionsUsed() { return potionsUsed; }
        public int goldenApples() { return goldenApples; }
        public int totemsPopped() { return totemsPopped; }
        public int crystalsPlaced() { return crystalsPlaced; }
        public int crystalsBroken() { return crystalsBroken; }

        public double accuracyPercent() {
            if (projectilesLaunched > 0) return (projectilesHit * 100.0D) / projectilesLaunched;
            int exchanges = hitsLanded + hitsReceived;
            return exchanges <= 0 ? 0.0D : (hitsLanded * 100.0D) / exchanges;
        }
    }
}
