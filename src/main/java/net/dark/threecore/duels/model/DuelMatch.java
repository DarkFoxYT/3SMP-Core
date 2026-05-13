package net.dark.threecore.duels.model;

import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class DuelMatch {
    private final UUID id;
    private final DuelMode mode;
    private final String kitId;
    private final String mapId;
    private final Set<UUID> teamOne;
    private final Set<UUID> teamTwo;
    private final long startedAt;
    private final int roundsToWin;
    private final boolean ranked;
    private final Set<UUID> teamOneEliminated;
    private final Set<UUID> teamTwoEliminated;
    private final Map<UUID, Integer> ffaWins;
    private int teamOneWins;
    private int teamTwoWins;

    public DuelMatch(UUID id, DuelMode mode, String kitId, String mapId, Set<UUID> teamOne, Set<UUID> teamTwo, long startedAt, int roundsToWin) {
        this(id, mode, kitId, mapId, teamOne, teamTwo, startedAt, roundsToWin, false);
    }

    public DuelMatch(UUID id, DuelMode mode, String kitId, String mapId, Set<UUID> teamOne, Set<UUID> teamTwo, long startedAt, int roundsToWin, boolean ranked) {
        this.id = id;
        this.mode = mode;
        this.kitId = kitId;
        this.mapId = mapId;
        this.teamOne = teamOne;
        this.teamTwo = teamTwo;
        this.startedAt = startedAt;
        this.roundsToWin = Math.max(1, roundsToWin);
        this.ranked = ranked;
        this.teamOneEliminated = new java.util.HashSet<>();
        this.teamTwoEliminated = new java.util.HashSet<>();
        this.ffaWins = new HashMap<>();
    }

    public UUID id() { return id; }
    public DuelMode mode() { return mode; }
    public String kitId() { return kitId; }
    public String mapId() { return mapId; }
    public Set<UUID> teamOne() { return teamOne; }
    public Set<UUID> teamTwo() { return teamTwo; }
    public long startedAt() { return startedAt; }
    public int roundsToWin() { return roundsToWin; }
    public boolean ranked() { return ranked; }
    public int teamOneWins() { return teamOneWins; }
    public int teamTwoWins() { return teamTwoWins; }
    public int totalRoundsPlayed() { return mode == DuelMode.FFA ? ffaWins.values().stream().mapToInt(Integer::intValue).sum() : teamOneWins + teamTwoWins; }
    public int winningThreshold() { return roundsToWin; }
    public int ffaWins(UUID uuid) { return uuid == null ? 0 : ffaWins.getOrDefault(uuid, 0); }
    public int topFfaWins() { return ffaWins.values().stream().mapToInt(Integer::intValue).max().orElse(0); }
    public Set<UUID> teamOneEliminated() { return java.util.Set.copyOf(teamOneEliminated); }
    public Set<UUID> teamTwoEliminated() { return java.util.Set.copyOf(teamTwoEliminated); }

    public boolean awardWin(Set<UUID> winners) {
        if (winners == null || winners.isEmpty()) return false;
        if (mode == DuelMode.FFA) {
            teamOneWins++;
            for (UUID winner : winners) ffaWins.merge(winner, 1, Integer::sum);
            return true;
        }
        boolean teamOneWon = winners.stream().anyMatch(teamOne::contains);
        boolean teamTwoWon = winners.stream().anyMatch(teamTwo::contains);
        if (teamOneWon == teamTwoWon) return false;
        if (teamOneWon) teamOneWins++;
        else teamTwoWins++;
        return true;
    }

    public boolean markEliminated(UUID uuid) {
        if (uuid == null) return false;
        if (teamOne.contains(uuid)) return teamOneEliminated.add(uuid);
        if (teamTwo.contains(uuid)) return teamTwoEliminated.add(uuid);
        return false;
    }

    public boolean isRoundComplete() {
        if (mode == DuelMode.FFA) return activeFfaPlayers().size() <= 1;
        return !teamOne.isEmpty() && teamOneEliminated.containsAll(teamOne)
                || !teamTwo.isEmpty() && teamTwoEliminated.containsAll(teamTwo);
    }

    public Set<UUID> roundWinners() {
        if (mode == DuelMode.FFA) {
            Set<UUID> active = activeFfaPlayers();
            return active.size() == 1 ? Set.copyOf(active) : Set.of();
        }
        if (!teamOne.isEmpty() && teamOneEliminated.containsAll(teamOne)) return teamTwo;
        if (!teamTwo.isEmpty() && teamTwoEliminated.containsAll(teamTwo)) return teamOne;
        return java.util.Set.of();
    }

    public void resetRoundState() {
        teamOneEliminated.clear();
        teamTwoEliminated.clear();
    }

    public boolean isMatchPoint() {
        if (mode == DuelMode.FFA) return topFfaWins() == roundsToWin - 1;
        return teamOneWins == roundsToWin - 1 || teamTwoWins == roundsToWin - 1;
    }

    public boolean isComplete() {
        if (mode == DuelMode.FFA) return topFfaWins() >= roundsToWin;
        return teamOneWins >= roundsToWin || teamTwoWins >= roundsToWin;
    }

    public Set<UUID> winningTeam() {
        if (mode == DuelMode.FFA) {
            int top = topFfaWins();
            if (top <= 0 || top < roundsToWin) return roundWinners();
            Set<UUID> winners = new LinkedHashSet<>();
            for (UUID uuid : matchMembers()) {
                if (ffaWins(uuid) == top) winners.add(uuid);
            }
            return winners;
        }
        return teamOneWins >= roundsToWin ? teamOne : teamTwo;
    }

    private Set<UUID> matchMembers() {
        Set<UUID> members = new LinkedHashSet<>(teamOne);
        members.addAll(teamTwo);
        return members;
    }

    private Set<UUID> activeFfaPlayers() {
        Set<UUID> active = matchMembers();
        active.removeAll(teamOneEliminated);
        active.removeAll(teamTwoEliminated);
        return active;
    }
}
