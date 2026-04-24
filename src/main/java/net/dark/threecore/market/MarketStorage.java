package net.dark.threecore.market;

import net.dark.threecore.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MarketStorage {
    private final Database database;

    public MarketStorage(Database database) {
        this.database = database;
    }

    public void save(MarketPlot plot) {
        try (PreparedStatement st = database.connection().prepareStatement("""
                INSERT INTO market_plots(id, name, owner, world, pos1x, pos1y, pos1z, pos2x, pos2y, pos2z, price, rent, rent_due_at, last_paid_at, created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    name=excluded.name,
                    owner=excluded.owner,
                    world=excluded.world,
                    pos1x=excluded.pos1x,pos1y=excluded.pos1y,pos1z=excluded.pos1z,
                    pos2x=excluded.pos2x,pos2y=excluded.pos2y,pos2z=excluded.pos2z,
                    price=excluded.price,rent=excluded.rent,rent_due_at=excluded.rent_due_at,last_paid_at=excluded.last_paid_at
                """)) {
            st.setString(1, plot.id());
            st.setString(2, plot.name());
            st.setString(3, plot.owner() == null ? "" : plot.owner().toString());
            st.setString(4, plot.world());
            st.setDouble(5, plot.pos1x());
            st.setDouble(6, plot.pos1y());
            st.setDouble(7, plot.pos1z());
            st.setDouble(8, plot.pos2x());
            st.setDouble(9, plot.pos2y());
            st.setDouble(10, plot.pos2z());
            st.setDouble(11, plot.price());
            st.setDouble(12, plot.rent());
            st.setLong(13, plot.rentDueAt());
            st.setLong(14, plot.lastPaidAt());
            st.setLong(15, plot.createdAt());
            st.executeUpdate();
            try (PreparedStatement clear = database.connection().prepareStatement("DELETE FROM market_plot_trust WHERE plot_id=?")) {
                clear.setString(1, plot.id());
                clear.executeUpdate();
            }
            try (PreparedStatement trust = database.connection().prepareStatement("INSERT INTO market_plot_trust(plot_id, player_uuid) VALUES(?, ?)")) {
                for (UUID uuid : plot.trusted()) {
                    trust.setString(1, plot.id());
                    trust.setString(2, uuid.toString());
                    trust.addBatch();
                }
                trust.executeBatch();
            }
        } catch (Exception ignored) {}
    }

    public List<MarketPlot> loadAll() {
        List<MarketPlot> plots = new ArrayList<>();
        try (Statement st = database.connection().createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM market_plots")) {
            while (rs.next()) {
                plots.add(new MarketPlot(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getDouble("pos1x"), rs.getDouble("pos1y"), rs.getDouble("pos1z"),
                        rs.getDouble("pos2x"), rs.getDouble("pos2y"), rs.getDouble("pos2z"),
                        emptyToNull(rs.getString("owner")),
                        rs.getDouble("price"),
                        rs.getDouble("rent"),
                        rs.getLong("rent_due_at"),
                        rs.getLong("last_paid_at"),
                        rs.getLong("created_at"),
                        loadTrusted(rs.getString("id"))
                ));
            }
        } catch (Exception ignored) {}
        return plots;
    }

    public MarketPlot load(String id) {
        try (PreparedStatement st = database.connection().prepareStatement("SELECT * FROM market_plots WHERE id=?")) {
            st.setString(1, id);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return null;
                return new MarketPlot(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getDouble("pos1x"), rs.getDouble("pos1y"), rs.getDouble("pos1z"),
                        rs.getDouble("pos2x"), rs.getDouble("pos2y"), rs.getDouble("pos2z"),
                        emptyToNull(rs.getString("owner")),
                        rs.getDouble("price"),
                        rs.getDouble("rent"),
                        rs.getLong("rent_due_at"),
                        rs.getLong("last_paid_at"),
                        rs.getLong("created_at"),
                        loadTrusted(rs.getString("id"))
                );
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void delete(String id) {
        try (PreparedStatement st = database.connection().prepareStatement("DELETE FROM market_plots WHERE id=?")) {
            st.setString(1, id);
            st.executeUpdate();
        } catch (Exception ignored) {}
        try (PreparedStatement st = database.connection().prepareStatement("DELETE FROM market_plot_trust WHERE plot_id=?")) {
            st.setString(1, id);
            st.executeUpdate();
        } catch (Exception ignored) {}
    }

    private Set<UUID> loadTrusted(String plotId) {
        Set<UUID> trusted = new HashSet<>();
        try (PreparedStatement st = database.connection().prepareStatement("SELECT player_uuid FROM market_plot_trust WHERE plot_id=?")) {
            st.setString(1, plotId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) trusted.add(UUID.fromString(rs.getString(1)));
            }
        } catch (Exception ignored) {}
        return trusted;
    }

    private UUID emptyToNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return UUID.fromString(raw);
    }
}
