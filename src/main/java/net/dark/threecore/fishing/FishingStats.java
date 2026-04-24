package net.dark.threecore.fishing;

public record FishingStats(long fishCaught, long rareCatches, long fishingPoints) {
    public FishingStats addFish(long amount) {
        return new FishingStats(fishCaught + Math.max(0L, amount), rareCatches, fishingPoints);
    }

    public FishingStats addRare(long amount) {
        return new FishingStats(fishCaught, rareCatches + Math.max(0L, amount), fishingPoints);
    }

    public FishingStats addPoints(long amount) {
        return new FishingStats(fishCaught, rareCatches, fishingPoints + Math.max(0L, amount));
    }
}
