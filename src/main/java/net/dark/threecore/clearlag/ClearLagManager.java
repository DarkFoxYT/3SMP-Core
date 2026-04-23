package net.dark.threecore.clearlag;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
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
        if (!configs.get("world/clearlag.yml").getBoolean("enabled", true)) return;
        long interval = Math.max(1200L, configs.get("world/clearlag.yml").getLong("interval-seconds", 300L) * 20L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::clear, interval, interval);
    }
    private void stop() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
    public int clear() {
        Set<EntityType> types = new HashSet<>();
        for (String name : configs.get("world/clearlag.yml").getStringList("entity-types")) {
            try { types.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) {}
        }
        int removed = 0;
        Set<String> disabledWorlds = new HashSet<>(configs.get("world/clearlag.yml").getStringList("disabled-worlds"));
        boolean protectNamed = configs.get("world/clearlag.yml").getBoolean("protect.named-items", true);
        boolean protectPersistent = configs.get("world/clearlag.yml").getBoolean("protect.persistent-entities", true);
        int maxPerRun = Math.max(1, configs.get("world/clearlag.yml").getInt("max-removals-per-run", 2500));
        for (World world : Bukkit.getWorlds()) {
            if (disabledWorlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()))) continue;
            for (Entity entity : world.getEntities()) {
                if (removed >= maxPerRun) break;
                if (!types.contains(entity.getType())) continue;
                if (protectPersistent && entity.isPersistent()) continue;
                if (protectNamed && entity instanceof Item item && item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName()) continue;
                entity.remove();
                removed++;
            }
        }
        plugin.getLogger().info("ClearLag removed " + removed + " entities. Limit=" + maxPerRun + ", WorldsSkipped=" + disabledWorlds.size());
        return removed;
    }
}
