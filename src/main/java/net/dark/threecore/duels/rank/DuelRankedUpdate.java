package net.dark.threecore.duels.rank;

import java.util.List;
import java.util.UUID;

public record DuelRankedUpdate(
        UUID uuid,
        String kitId,
        boolean ranked,
        boolean valid,
        int oldMmr,
        int newMmr,
        int change,
        String oldRank,
        String newRank,
        String oldRankDisplay,
        String newRankDisplay,
        List<String> invalidReasons
) {
    public String signedChange() {
        if (change > 0) return "+" + change;
        return String.valueOf(change);
    }
}
