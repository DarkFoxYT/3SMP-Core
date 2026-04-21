package net.dark.threecore.clearlag;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ClearLagManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private int taskId = -1;

    public ClearLagManager(JavaPlugin plugin, ConfigFiles configs) { this.plugin = plugin; this.configs = configs; start(); }
    public void reload() { stop(); start(); }
    public void shutdown() { stop(); }

    private void start() {
        if (!configs.get("clearlag.yml").getBoolean("enabled", true)) return;
        long interval = Math.max(1200L, configs.get("clearlag.yml").getLong("interval-seconds", 300L) * 20L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::clear, interval, interval);
    }
    private void stop() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
    public int clear() {
        Set<EntityType> types = new HashSet<>();
        for (String name : configs.get("clearlag.yml").getStringList("entity-types")) {
            try { types.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!types.contains(entity.getType())) continue;
                entity.remove();
                removed++;
            }
        }
        plugin.getLogger().info("ClearLag removed " + removed + " entities.");
        return removed;
    }
}
