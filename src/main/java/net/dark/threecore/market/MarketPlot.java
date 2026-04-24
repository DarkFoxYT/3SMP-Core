package net.dark.threecore.market;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record MarketPlot(
        String id,
        String name,
        String world,
        double pos1x,
        double pos1y,
        double pos1z,
        double pos2x,
        double pos2y,
        double pos2z,
        UUID owner,
        double price,
        double rent,
        long rentDueAt,
        long lastPaidAt,
        long createdAt,
        Set<UUID> trusted
) {
    public MarketPlot withOwner(UUID owner, long dueAt, long paidAt) {
        return new MarketPlot(id, name, world, pos1x, pos1y, pos1z, pos2x, pos2y, pos2z, owner, price, rent, dueAt, paidAt, createdAt, new HashSet<>(trusted));
    }
    public MarketPlot withName(String next) { return new MarketPlot(id, next, world, pos1x, pos1y, pos1z, pos2x, pos2y, pos2z, owner, price, rent, rentDueAt, lastPaidAt, createdAt, new HashSet<>(trusted)); }
    public MarketPlot withPrice(double next) { return new MarketPlot(id, name, world, pos1x, pos1y, pos1z, pos2x, pos2y, pos2z, owner, next, rent, rentDueAt, lastPaidAt, createdAt, new HashSet<>(trusted)); }
    public MarketPlot withRent(double next) { return new MarketPlot(id, name, world, pos1x, pos1y, pos1z, pos2x, pos2y, pos2z, owner, price, next, rentDueAt, lastPaidAt, createdAt, new HashSet<>(trusted)); }
    public MarketPlot withTrusted(Set<UUID> next) { return new MarketPlot(id, name, world, pos1x, pos1y, pos1z, pos2x, pos2y, pos2z, owner, price, rent, rentDueAt, lastPaidAt, createdAt, next); }
}
