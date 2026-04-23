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



