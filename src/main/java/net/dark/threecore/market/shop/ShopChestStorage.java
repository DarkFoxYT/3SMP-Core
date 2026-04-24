package net.dark.threecore.market.shop;

import net.dark.threecore.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShopChestStorage {
    private final Database database;

    public ShopChestStorage(Database database) {
        this.database = database;
    }

    public void save(ShopChestData data) {
        try (PreparedStatement st = database.connection().prepareStatement("""
                INSERT INTO shop_chests(world,x,y,z,owner,price,quantity,enabled,item_type,item_name)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(world,x,y,z) DO UPDATE SET owner=excluded.owner, price=excluded.price, quantity=excluded.quantity, enabled=excluded.enabled, item_type=excluded.item_type, item_name=excluded.item_name
                """)) {
            st.setString(1, data.world());
            st.setInt(2, data.x());
            st.setInt(3, data.y());
            st.setInt(4, data.z());
            st.setString(5, data.owner() == null ? "" : data.owner().toString());
            st.setDouble(6, data.price());
            st.setInt(7, data.quantity());
            st.setInt(8, data.enabled() ? 1 : 0);
            st.setString(9, data.itemType());
            st.setString(10, data.itemName());
            st.executeUpdate();
        } catch (Exception ignored) {}
    }

    public ShopChestData load(String world, int x, int y, int z) {
        try (PreparedStatement st = database.connection().prepareStatement("SELECT * FROM shop_chests WHERE world=? AND x=? AND y=? AND z=?")) {
            st.setString(1, world);
            st.setInt(2, x);
            st.setInt(3, y);
            st.setInt(4, z);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return null;
                return new ShopChestData(world, x, y, z, emptyToNull(rs.getString("owner")), rs.getDouble("price"), rs.getInt("quantity"), rs.getInt("enabled") != 0, rs.getString("item_type"), rs.getString("item_name"));
            }
        } catch (Exception ignored) {}
        return null;
    }

    public Map<String, ShopChestData> all() {
        Map<String, ShopChestData> out = new HashMap<>();
        try (PreparedStatement st = database.connection().prepareStatement("SELECT * FROM shop_chests");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                ShopChestData data = new ShopChestData(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), emptyToNull(rs.getString("owner")), rs.getDouble("price"), rs.getInt("quantity"), rs.getInt("enabled") != 0, rs.getString("item_type"), rs.getString("item_name"));
                out.put(key(data.world(), data.x(), data.y(), data.z()), data);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public void delete(String world, int x, int y, int z) {
        try (PreparedStatement st = database.connection().prepareStatement("DELETE FROM shop_chests WHERE world=? AND x=? AND y=? AND z=?")) {
            st.setString(1, world);
            st.setInt(2, x);
            st.setInt(3, y);
            st.setInt(4, z);
            st.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private UUID emptyToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return UUID.fromString(value);
    }
}
