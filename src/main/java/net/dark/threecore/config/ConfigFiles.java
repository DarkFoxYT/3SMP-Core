package net.dark.threecore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ConfigFiles {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> cache = new HashMap<>();

    public ConfigFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public YamlConfiguration get(String path) {
        return cache.computeIfAbsent(path, p -> YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), p)));
    }

    public void reload() {
        cache.clear();
    }

    public void reload(String path) {
        if (path == null || path.isBlank()) {
            reload();
            return;
        }
        cache.remove(path);
    }
}
