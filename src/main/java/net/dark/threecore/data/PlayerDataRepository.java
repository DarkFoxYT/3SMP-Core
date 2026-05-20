package net.dark.threecore.data;

import net.dark.threecore.model.PlayerProgressionData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class PlayerDataRepository {
    private final Database database;

    public PlayerDataRepository(Database database) {
        this.database = database;
    }

    public PlayerProgressionData load(UUID uuid) {
        PlayerProgressionData data = new PlayerProgressionData(uuid);
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT data FROM player_perks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) parseInto(data, rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load player data", e);
        }
        return data;
    }

    public void save(PlayerProgressionData data) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_perks(uuid, data) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET data = excluded.data")) {
            ps.setString(1, data.uuid().toString());
            ps.setString(2, serialize(data));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save player data", e);
        }
    }

    public long getSapphireBalance(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT balance FROM player_sapphires WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load sapphire balance", e);
        }
    }

    public void setSapphireBalance(UUID uuid, long balance) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_sapphires(uuid, balance) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, Math.max(0L, balance));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save sapphire balance", e);
        }
    }

    public double getMoneyBalance(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT balance FROM player_money WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 1_000.0D;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load money balance", e);
        }
    }

    public void setMoneyBalance(UUID uuid, double balance) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_money(uuid, balance) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance")) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, Math.max(0.0D, balance));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save money balance", e);
        }
    }

    public boolean hasDungeonCompletion(UUID uuid, String level, String difficulty) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT 1 FROM player_dungeon_completions WHERE uuid = ? AND level = ? AND difficulty = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, level.toLowerCase(java.util.Locale.ROOT));
            ps.setString(3, difficulty.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load dungeon completion", e);
        }
    }

    public void markDungeonCompletion(UUID uuid, String level, String difficulty) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_dungeon_completions(uuid, level, difficulty, completed_at) VALUES(?, ?, ?, ?) ON CONFLICT(uuid, level, difficulty) DO UPDATE SET completed_at = excluded.completed_at")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, level.toLowerCase(java.util.Locale.ROOT));
            ps.setString(3, difficulty.toLowerCase(java.util.Locale.ROOT));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save dungeon completion", e);
        }
    }

    public Set<UUID> friends(UUID uuid) {
        Set<UUID> friends = new LinkedHashSet<>();
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT friend FROM player_friends WHERE owner = ? ORDER BY created_at ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) friends.add(UUID.fromString(rs.getString(1)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load friends", e);
        }
        return friends;
    }

    public void addFriend(UUID owner, UUID friend) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_friends(owner, friend, created_at) VALUES(?, ?, ?) ON CONFLICT(owner, friend) DO NOTHING")) {
            ps.setString(1, owner.toString());
            ps.setString(2, friend.toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add friend", e);
        }
    }

    public void removeFriend(UUID owner, UUID friend) {
        try (PreparedStatement ps = database.connection().prepareStatement("DELETE FROM player_friends WHERE owner = ? AND friend = ?")) {
            ps.setString(1, owner.toString());
            ps.setString(2, friend.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove friend", e);
        }
    }

    public void wipePlayerData(UUID uuid) {
        try {
            deleteWhere("DELETE FROM player_perks WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_sapphires WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_gems WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_money WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_dungeon_completions WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_friends WHERE owner = ? OR friend = ?", uuid);
            deleteWhere("DELETE FROM player_daily_rewards WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_fishing_stats WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_souls WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_inventory_profiles WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_homes WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM player_duel_kit_stats WHERE uuid = ?", uuid);
            deleteWhere("DELETE FROM market_plot_trust WHERE player_uuid = ?", uuid);
            deleteWhere("DELETE FROM market_plots WHERE owner = ?", uuid);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to wipe player data", e);
        }
    }

    public void saveInventoryProfile(UUID uuid, String profile, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_inventory_profiles(uuid, profile, contents, armor, offhand) VALUES(?, ?, ?, ?, ?) ON CONFLICT(uuid, profile) DO UPDATE SET contents = excluded.contents, armor = excluded.armor, offhand = excluded.offhand")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            ps.setString(3, encode(contents));
            ps.setString(4, encode(armor));
            ps.setString(5, encode(new ItemStack[]{offhand}));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save inventory profile", e);
        }
    }

    public InventoryProfile loadInventoryProfile(UUID uuid, String profile) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT contents, armor, offhand FROM player_inventory_profiles WHERE uuid = ? AND profile = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, profile);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new InventoryProfile(new ItemStack[36], new ItemStack[4], null);
                ItemStack[] contents = decode(rs.getString(1));
                ItemStack[] armor = decode(rs.getString(2));
                ItemStack[] offhand = decode(rs.getString(3));
                return new InventoryProfile(contents, armor, offhand.length == 0 ? null : offhand[0]);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load inventory profile", e);
        }
    }

    public int deleteInventoryProfile(String profile) {
        try (PreparedStatement ps = database.connection().prepareStatement("DELETE FROM player_inventory_profiles WHERE profile = ?")) {
            ps.setString(1, profile);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete inventory profile", e);
        }
    }

    public DuelKitStats loadDuelKitStats(UUID uuid, String kitId, int defaultMmr) {
        String normalizedKit = normalizeKit(kitId);
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT wins, losses, ranked_wins, ranked_losses, current_streak, best_streak, mmr FROM player_duel_kit_stats WHERE uuid = ? AND kit_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, normalizedKit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DuelKitStats(uuid, normalizedKit, rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load duel kit stats", e);
        }
        return new DuelKitStats(uuid, normalizedKit, 0, 0, 0, 0, 0, 0, Math.max(0, defaultMmr));
    }

    public void saveDuelKitStats(DuelKitStats stats) {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_duel_kit_stats(uuid, kit_id, wins, losses, ranked_wins, ranked_losses, current_streak, best_streak, mmr) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid, kit_id) DO UPDATE SET wins = excluded.wins, losses = excluded.losses, ranked_wins = excluded.ranked_wins, ranked_losses = excluded.ranked_losses, current_streak = excluded.current_streak, best_streak = excluded.best_streak, mmr = excluded.mmr")) {
            ps.setString(1, stats.uuid().toString());
            ps.setString(2, normalizeKit(stats.kitId()));
            ps.setInt(3, Math.max(0, stats.wins()));
            ps.setInt(4, Math.max(0, stats.losses()));
            ps.setInt(5, Math.max(0, stats.rankedWins()));
            ps.setInt(6, Math.max(0, stats.rankedLosses()));
            ps.setInt(7, Math.max(0, stats.currentStreak()));
            ps.setInt(8, Math.max(0, stats.bestStreak()));
            ps.setInt(9, Math.max(0, stats.mmr()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save duel kit stats", e);
        }
    }

    private String normalizeKit(String kitId) {
        return kitId == null || kitId.isBlank() ? "default" : kitId.toLowerCase(java.util.Locale.ROOT);
    }

    private void parseInto(PlayerProgressionData data, String text) {
        if (text == null || text.isBlank()) return;
        Map<String, String> values = new HashMap<>();
        for (String pair : text.split(";")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            values.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        String unlocked = values.getOrDefault("unlocked", "");
        if (!unlocked.isBlank()) data.unlockedPerks().addAll(Arrays.asList(unlocked.split(",")));
        data.activePrefix(values.getOrDefault("activePrefix", ""));
        data.activeTag(values.getOrDefault("activeTag", ""));
        data.activeBadge(values.getOrDefault("activeBadge", ""));
        data.activeTrim(values.getOrDefault("activeTrim", ""));
        data.activeMessageColor(values.getOrDefault("activeMessageColor", ""));
        data.activeCosmetic(values.getOrDefault("activeCosmetic", ""));
        data.activeParticle(values.getOrDefault("activeParticle", ""));
        data.activeEffect(values.getOrDefault("activeEffect", ""));
        data.activeJoinQuitMessage(values.getOrDefault("activeJoinQuitMessage", ""));
        data.duelRating(parseInt(values.getOrDefault("duelRating", "0")));
        data.duelWins(parseInt(values.getOrDefault("duelWins", "0")));
        data.duelLosses(parseInt(values.getOrDefault("duelLosses", "0")));
        data.duelWinStreak(parseInt(values.getOrDefault("duelWinStreak", "0")));
        data.duelBestWinStreak(parseInt(values.getOrDefault("duelBestWinStreak", "0")));
        data.duelKills(parseInt(values.getOrDefault("duelKills", "0")));
        data.duelDeaths(parseInt(values.getOrDefault("duelDeaths", "0")));
    }

    private String serialize(PlayerProgressionData data) {
        return "unlocked=" + String.join(",", data.unlockedPerks())
                + ";activePrefix=" + clean(data.activePrefix())
                + ";activeTag=" + clean(data.activeTag())
                + ";activeBadge=" + clean(data.activeBadge())
                + ";activeTrim=" + clean(data.activeTrim())
                + ";activeMessageColor=" + clean(data.activeMessageColor())
                + ";activeCosmetic=" + clean(data.activeCosmetic())
                + ";activeParticle=" + clean(data.activeParticle())
                + ";activeEffect=" + clean(data.activeEffect())
                + ";activeJoinQuitMessage=" + clean(data.activeJoinQuitMessage())
                + ";duelRating=" + data.duelRating()
                + ";duelWins=" + data.duelWins()
                + ";duelLosses=" + data.duelLosses()
                + ";duelWinStreak=" + data.duelWinStreak()
                + ";duelBestWinStreak=" + data.duelBestWinStreak()
                + ";duelKills=" + data.duelKills()
                + ";duelDeaths=" + data.duelDeaths();
    }

    private int parseInt(String input) { try { return Integer.parseInt(input); } catch (NumberFormatException ex) { return 0; } }
    private String clean(String value) { return value == null ? "" : value.replace(";", "").replace("=", "").replace(",", ""); }

    private void deleteWhere(String sql, UUID uuid) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (sql.contains("owner = ? OR friend = ?")) ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    private String encode(ItemStack[] items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeInt(items == null ? 0 : items.length);
            if (items != null) for (ItemStack item : items) oos.writeObject(item);
            oos.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private ItemStack[] decode(String data) {
        if (data == null || data.isBlank()) return new ItemStack[0];
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data)); BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            int len = ois.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) ois.readObject();
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    public record InventoryProfile(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {}
    public record DuelKitStats(UUID uuid, String kitId, int wins, int losses, int rankedWins, int rankedLosses, int currentStreak, int bestStreak, int mmr) {
        public DuelKitStats recordResult(boolean win, boolean ranked, int newMmr) {
            int nextWins = wins + (win ? 1 : 0);
            int nextLosses = losses + (win ? 0 : 1);
            int nextRankedWins = rankedWins + (ranked && win ? 1 : 0);
            int nextRankedLosses = rankedLosses + (ranked && !win ? 1 : 0);
            int nextStreak = win ? currentStreak + 1 : 0;
            int nextBest = Math.max(bestStreak, nextStreak);
            return new DuelKitStats(uuid, kitId, nextWins, nextLosses, nextRankedWins, nextRankedLosses, nextStreak, nextBest, Math.max(0, newMmr));
        }
    }
}
