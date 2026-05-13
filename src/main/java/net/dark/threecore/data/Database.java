package net.dark.threecore.data;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private final JavaPlugin plugin;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "storage.db");
            dbFile.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_perks (uuid TEXT PRIMARY KEY, data TEXT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_sapphires (uuid TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_gems (uuid TEXT PRIMARY KEY, data TEXT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_money (uuid TEXT PRIMARY KEY, balance REAL NOT NULL DEFAULT 1000)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_dungeon_completions (uuid TEXT NOT NULL, level TEXT NOT NULL, difficulty TEXT NOT NULL, completed_at INTEGER NOT NULL, PRIMARY KEY(uuid, level, difficulty))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_friends (owner TEXT NOT NULL, friend TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(owner, friend))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_daily_rewards (uuid TEXT PRIMARY KEY, last_claim_at INTEGER NOT NULL DEFAULT 0, streak INTEGER NOT NULL DEFAULT 0, total_claims INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_fishing_stats (uuid TEXT PRIMARY KEY, fish_caught INTEGER NOT NULL DEFAULT 0, rare_catches INTEGER NOT NULL DEFAULT 0, fishing_points INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_souls (uuid TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS market_plots (id TEXT PRIMARY KEY, name TEXT NOT NULL, owner TEXT NOT NULL, world TEXT NOT NULL, pos1x REAL NOT NULL, pos1y REAL NOT NULL, pos1z REAL NOT NULL, pos2x REAL NOT NULL, pos2y REAL NOT NULL, pos2z REAL NOT NULL, price REAL NOT NULL DEFAULT 0, rent REAL NOT NULL DEFAULT 0, rent_due_at INTEGER NOT NULL DEFAULT 0, last_paid_at INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS market_plot_trust (plot_id TEXT NOT NULL, player_uuid TEXT NOT NULL, PRIMARY KEY(plot_id, player_uuid))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS shop_chests (world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, owner TEXT NOT NULL, price REAL NOT NULL DEFAULT 1, quantity INTEGER NOT NULL DEFAULT 1, enabled INTEGER NOT NULL DEFAULT 0, item_type TEXT NOT NULL DEFAULT 'COBBLESTONE', item_name TEXT NOT NULL DEFAULT 'Cobblestone', PRIMARY KEY(world, x, y, z))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_inventory_profiles (uuid TEXT NOT NULL, profile TEXT NOT NULL, contents TEXT NOT NULL, armor TEXT NOT NULL, offhand TEXT NOT NULL, PRIMARY KEY(uuid, profile))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS player_duel_kit_stats (uuid TEXT NOT NULL, kit_id TEXT NOT NULL, wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0, ranked_wins INTEGER NOT NULL DEFAULT 0, ranked_losses INTEGER NOT NULL DEFAULT 0, current_streak INTEGER NOT NULL DEFAULT 0, best_streak INTEGER NOT NULL DEFAULT 0, mmr INTEGER NOT NULL DEFAULT 1000, PRIMARY KEY(uuid, kit_id))");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    public Connection connection() {
        return connection;
    }

    public void close() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException ignored) {
        }
    }
}



