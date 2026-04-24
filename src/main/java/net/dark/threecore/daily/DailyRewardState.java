package net.dark.threecore.daily;

public final class DailyRewardState {
    private long lastClaimAt;
    private int streak;
    private long totalClaims;

    public DailyRewardState(long lastClaimAt, int streak, long totalClaims) {
        this.lastClaimAt = lastClaimAt;
        this.streak = streak;
        this.totalClaims = totalClaims;
    }

    public long lastClaimAt() {
        return lastClaimAt;
    }

    public void lastClaimAt(long lastClaimAt) {
        this.lastClaimAt = lastClaimAt;
    }

    public int streak() {
        return streak;
    }

    public void streak(int streak) {
        this.streak = streak;
    }

    public long totalClaims() {
        return totalClaims;
    }

    public void totalClaims(long totalClaims) {
        this.totalClaims = totalClaims;
    }
}
