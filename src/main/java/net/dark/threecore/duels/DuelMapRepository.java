package net.dark.threecore.duels;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.model.DuelMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DuelMapRepository {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final File file;
    private final Map<String, DuelMap> maps = new LinkedHashMap<>();

    public DuelMapRepository(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
        this.file = new File(plugin.getDataFolder(), "duels/maps.yml");
    }

    public Map<String, DuelMap> load() {
        maps.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("maps");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                var map = section.getConfigurationSection(id);
                if (map == null) continue;
                String worldName = map.getString("world", "world");
                maps.put(id.toLowerCase(Locale.ROOT), new DuelMap(id.toLowerCase(Locale.ROOT), map.getString("display-name", id), map.getBoolean("enabled", true), worldName, loc(map.getConfigurationSection("lobby")), loc(map.getConfigurationSection("spawn-a")), loc(map.getConfigurationSection("spawn-b")), loc(map.getConfigurationSection("spectator"))));
            }
        }
        return maps;
    }

    public void save(Map<String, DuelMap> values) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : values.entrySet()) {
            DuelMap map = entry.getValue();
            String path = "maps." + entry.getKey();
            yaml.set(path + ".display-name", map.displayName());
            yaml.set(path + ".enabled", map.enabled());
            yaml.set(path + ".world", map.worldName());
            writeLoc(yaml, path + ".lobby", map.lobby());
            writeLoc(yaml, path + ".spawn-a", map.spawnA());
            writeLoc(yaml, path + ".spawn-b", map.spawnB());
            writeLoc(yaml, path + ".spectator", map.spectator());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save duel maps", e);
        }
    }

    private void writeLoc(YamlConfiguration yaml, String path, Location location) {
        if (location == null) return;
        yaml.set(path + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private Location loc(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) return null;
        String worldName = section.getString("world", "world");
        var world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }
}
