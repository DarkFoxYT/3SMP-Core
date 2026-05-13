package net.dark.threecore.duels;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.model.DuelMap;
import net.dark.threecore.duels.model.DuelGateRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
                maps.put(id.toLowerCase(Locale.ROOT), new DuelMap(id.toLowerCase(Locale.ROOT), map.getString("display-name", id), map.getBoolean("enabled", true), worldName, loc(map.getConfigurationSection("lobby")), loc(map.getConfigurationSection("spawn-a")), loc(map.getConfigurationSection("spawn-b")), loc(map.getConfigurationSection("ffa-spawn")), loc(map.getConfigurationSection("spectator")), gate(map.getConfigurationSection("gates.red"), worldName), gate(map.getConfigurationSection("gates.blue"), worldName), loc(map.getConfigurationSection("gate-exits.red")), loc(map.getConfigurationSection("gate-exits.blue")), gate(gateZoneSection(map, "red"), worldName), gate(gateZoneSection(map, "blue"), worldName)));
            }
        }
        return maps;
    }

    public void save(Map<String, DuelMap> values) {
        backupMapsFile(file.toPath());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> icons = preservedMapValues(yaml, "icon");
        yaml.set("maps", null);
        for (var entry : values.entrySet()) {
            DuelMap map = entry.getValue();
            String path = "maps." + entry.getKey();
            yaml.set(path + ".display-name", map.displayName());
            yaml.set(path + ".enabled", map.enabled());
            if (icons.containsKey(entry.getKey().toLowerCase(Locale.ROOT))) yaml.set(path + ".icon", icons.get(entry.getKey().toLowerCase(Locale.ROOT)));
            yaml.set(path + ".world", map.worldName());
            writeLoc(yaml, path + ".lobby", map.lobby());
            writeLoc(yaml, path + ".spawn-a", map.spawnA());
            writeLoc(yaml, path + ".spawn-b", map.spawnB());
            writeLoc(yaml, path + ".ffa-spawn", map.ffaSpawn());
            writeLoc(yaml, path + ".spectator", map.spectator());
            writeGate(yaml, path + ".gates.red", map.redGate());
            writeGate(yaml, path + ".gates.blue", map.blueGate());
            writeLoc(yaml, path + ".gate-exits.red", map.redGateExit());
            writeLoc(yaml, path + ".gate-exits.blue", map.blueGateExit());
            writeGate(yaml, path + ".gate-zones.red", map.redGateCloseZone());
            writeGate(yaml, path + ".gate-zones.blue", map.blueGateCloseZone());
        }
        try {
            yaml.save(file);
            configs.reload("duels/maps.yml");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save duel maps", e);
        }
    }

    private Map<String, Object> preservedMapValues(YamlConfiguration yaml, String key) {
        Map<String, Object> values = new HashMap<>();
        var section = yaml.getConfigurationSection("maps");
        if (section == null) return values;
        for (String id : section.getKeys(false)) {
            Object value = section.get(id + "." + key);
            if (value != null) values.put(id.toLowerCase(Locale.ROOT), value);
        }
        return values;
    }

    private void backupMapsFile(Path source) {
        if (source == null || !Files.exists(source)) return;
        try {
            Path parent = source.getParent();
            if (parent == null) return;
            Files.createDirectories(parent);
            Files.copy(source, parent.resolve("maps.yml.backup"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Path backupDir = parent.resolve("backups");
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Files.copy(source, backupDir.resolve("maps-" + stamp + ".yml"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create duel maps backup before saving: " + ex.getMessage());
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

    private org.bukkit.configuration.ConfigurationSection gateZoneSection(org.bukkit.configuration.ConfigurationSection map, String team) {
        if (map == null) return null;
        org.bukkit.configuration.ConfigurationSection section = map.getConfigurationSection("gate-zones." + team);
        return section == null ? map.getConfigurationSection("gate-close-zones." + team) : section;
    }

    private DuelGateRegion gate(org.bukkit.configuration.ConfigurationSection section, String worldName) {
        if (section == null) return null;
        java.util.List<Integer> min = section.getIntegerList("min");
        java.util.List<Integer> max = section.getIntegerList("max");
        if (min.size() < 3 || max.size() < 3) {
            min = section.getIntegerList("pos1");
            max = section.getIntegerList("pos2");
        }
        if (min.size() < 3 || max.size() < 3) return null;
        return new DuelGateRegion(section.getString("world", worldName), min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
    }

    private void writeGate(YamlConfiguration yaml, String path, DuelGateRegion gate) {
        if (gate == null) return;
        yaml.set(path + ".world", gate.worldName());
        yaml.set(path + ".pos1", java.util.List.of(gate.minX(), gate.minY(), gate.minZ()));
        yaml.set(path + ".pos2", java.util.List.of(gate.maxX(), gate.maxY(), gate.maxZ()));
        yaml.set(path + ".min", java.util.List.of(gate.minX(), gate.minY(), gate.minZ()));
        yaml.set(path + ".max", java.util.List.of(gate.maxX(), gate.maxY(), gate.maxZ()));
    }
}
