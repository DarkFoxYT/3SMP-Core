package net.dark.threecore.fishing;

import net.dark.threecore.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class FishingStatsStorage {
    private final Database database;

    public FishingStatsStorage(Database database) {
        this.database = database;
    }

    public FishingStats load(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT fish_caught, rare_catches, fishing_points FROM player_fishing_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FishingStats(rs.getLong(1), rs.getLong(2), rs.getLong(3));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load fishing stats", e);
        }
        return new FishingStats(0L, 0L, 0L);
    }

    public void save(UUID uuid, FishingStats stats) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_fishing_stats(uuid, fish_caught, rare_catches, fishing_points) VALUES(?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET fish_caught = excluded.fish_caught, rare_catches = excluded.rare_catches, fishing_points = excluded.fishing_points")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, Math.max(0L, stats.fishCaught()));
            ps.setLong(3, Math.max(0L, stats.rareCatches()));
            ps.setLong(4, Math.max(0L, stats.fishingPoints()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save fishing stats", e);
        }
    }
}
