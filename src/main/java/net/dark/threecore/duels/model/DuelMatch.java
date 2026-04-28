package net.dark.threecore.duels.model;

import java.util.Set;
import java.util.UUID;

public final class DuelMatch {
    private final UUID id;
    private final DuelMode mode;
    private final String kitId;
    private final String mapId;
    private final Set<UUID> teamOne;
    private final Set<UUID> teamTwo;
    private final long startedAt;
    private final int roundsToWin;
    private final Set<UUID> teamOneEliminated;
    private final Set<UUID> teamTwoEliminated;
    private int teamOneWins;
    private int teamTwoWins;

    public DuelMatch(UUID id, DuelMode mode, String kitId, String mapId, Set<UUID> teamOne, Set<UUID> teamTwo, long startedAt, int roundsToWin) {
        this.id = id;
        this.mode = mode;
        this.kitId = kitId;
        this.mapId = mapId;
        this.teamOne = teamOne;
        this.teamTwo = teamTwo;
        this.startedAt = startedAt;
        this.roundsToWin = Math.max(1, roundsToWin);
        this.teamOneEliminated = new java.util.HashSet<>();
        this.teamTwoEliminated = new java.util.HashSet<>();
    }

    public UUID id() { return id; }
    public DuelMode mode() { return mode; }
    public String kitId() { return kitId; }
    public String mapId() { return mapId; }
    public Set<UUID> teamOne() { return teamOne; }
    public Set<UUID> teamTwo() { return teamTwo; }
    public long startedAt() { return startedAt; }
    public int roundsToWin() { return roundsToWin; }
    public int teamOneWins() { return teamOneWins; }
    public int teamTwoWins() { return teamTwoWins; }
    public int totalRoundsPlayed() { return teamOneWins + teamTwoWins; }
    public int winningThreshold() { return roundsToWin; }
    public Set<UUID> teamOneEliminated() { return java.util.Set.copyOf(teamOneEliminated); }
    public Set<UUID> teamTwoEliminated() { return java.util.Set.copyOf(teamTwoEliminated); }

    public boolean awardWin(Set<UUID> winners) {
        if (winners == null || winners.isEmpty()) return false;
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
        return !teamOne.isEmpty() && teamOneEliminated.containsAll(teamOne)
                || !teamTwo.isEmpty() && teamTwoEliminated.containsAll(teamTwo);
    }

    public Set<UUID> roundWinners() {
        if (!teamOne.isEmpty() && teamOneEliminated.containsAll(teamOne)) return teamTwo;
        if (!teamTwo.isEmpty() && teamTwoEliminated.containsAll(teamTwo)) return teamOne;
        return java.util.Set.of();
    }

    public void resetRoundState() {
        teamOneEliminated.clear();
        teamTwoEliminated.clear();
    }

    public boolean isMatchPoint() {
        return teamOneWins == roundsToWin - 1 || teamTwoWins == roundsToWin - 1;
    }

    public boolean isComplete() {
        return teamOneWins >= roundsToWin || teamTwoWins >= roundsToWin;
    }

    public Set<UUID> winningTeam() {
        return teamOneWins >= roundsToWin ? teamOne : teamTwo;
    }
}
