package net.dark.threecore.daily;

import net.dark.threecore.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class DailyRewardStorage {
    private final Database database;

    public DailyRewardStorage(Database database) {
        this.database = database;
    }

    public DailyRewardState load(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT last_claim_at, streak, total_claims FROM player_daily_rewards WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DailyRewardState(rs.getLong(1), rs.getInt(2), rs.getLong(3));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load daily reward state", e);
        }
        return new DailyRewardState(0L, 0, 0L);
    }

    public void save(UUID uuid, DailyRewardState state) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_daily_rewards(uuid, last_claim_at, streak, total_claims) VALUES(?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET last_claim_at = excluded.last_claim_at, streak = excluded.streak, total_claims = excluded.total_claims")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, Math.max(0L, state.lastClaimAt()));
            ps.setInt(3, Math.max(0, state.streak()));
            ps.setLong(4, Math.max(0L, state.totalClaims()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save daily reward state", e);
        }
    }
}
