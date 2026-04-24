package net.dark.threecore.souls;

import net.dark.threecore.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class SoulStorage {
    private final Database database;

    public SoulStorage(Database database) {
        this.database = database;
    }

    public long load(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT balance FROM player_souls WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load souls", e);
        }
    }

    public void save(UUID uuid, long balance) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_souls(uuid, balance) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, Math.max(0L, balance));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save souls", e);
        }
    }
}
