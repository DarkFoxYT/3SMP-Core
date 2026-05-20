package net.dark.threecore.visual;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class VisualCosmeticService {
    public enum Type {
        PREFIX("prefixes", "prefix"),
        NAME_COLOR("name-colors", "name-color"),
        NAME_GRADIENT("name-gradients", "name-gradient"),
        SHADOW("shadows", "shadow");

        private final String configPath;
        private final String storageKey;

        Type(String configPath, String storageKey) {
            this.configPath = configPath;
            this.storageKey = storageKey;
        }

        public String configPath() { return configPath; }
        public String storageKey() { return storageKey; }
    }

    public record Cosmetic(String id, String displayName, Material icon, String value, String permission) {}

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final File file;
    private YamlConfiguration storage;

    public VisualCosmeticService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
        this.file = new File(plugin.getDataFolder(), "social/visual-players.yml");
        this.storage = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        this.storage = YamlConfiguration.loadConfiguration(file);
    }

    public boolean enabled() {
        return config().getBoolean("visual-cosmetics.enabled", true);
    }

    public boolean allowPrefixOverride() {
        return config().getBoolean("visual-cosmetics.allow-player-prefix-override", true);
    }

    public boolean allowNameOverride() {
        return config().getBoolean("visual-cosmetics.allow-player-name-color-override", true);
    }

    public boolean allowShadowOverride() {
        return config().getBoolean("visual-cosmetics.allow-player-shadow-override", true);
    }

    public List<Cosmetic> cosmetics(Type type) {
        ConfigurationSection section = config().getConfigurationSection("visual-cosmetics." + type.configPath());
        if (section == null) return List.of();
        List<Cosmetic> out = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String path = "visual-cosmetics." + type.configPath() + "." + id;
            Material icon = Material.matchMaterial(config().getString(path + ".icon", "PAPER"));
            String valueKey = switch (type) {
                case PREFIX -> "text";
                case NAME_COLOR -> "color";
                case NAME_GRADIENT -> "gradient";
                case SHADOW -> "shadow";
            };
            out.add(new Cosmetic(
                id.toLowerCase(Locale.ROOT),
                config().getString(path + ".display-name", id),
                icon == null ? Material.PAPER : icon,
                config().getString(path + "." + valueKey, ""),
                config().getString(path + ".permission", "")
            ));
        }
        return out;
    }

    public Cosmetic selected(Player player, Type type) {
        if (!enabled()) return null;
        String id = storage.getString(path(player.getUniqueId(), type), "");
        if (id.isBlank()) return null;
        if (id.equalsIgnoreCase("custom") && (type == Type.NAME_COLOR || type == Type.NAME_GRADIENT)) {
            String value = storage.getString("players." + player.getUniqueId() + "." + type.storageKey() + "-custom", "");
            if (value.isBlank()) return null;
            return new Cosmetic("custom", "Custom " + type.storageKey(), Material.NAME_TAG, value, "");
        }
        Cosmetic cosmetic = cosmetic(type, id);
        if (cosmetic == null || !owns(player, type, cosmetic)) {
            clear(player, type);
            return null;
        }
        return cosmetic;
    }

    public Cosmetic cosmetic(Type type, String id) {
        return cosmetics(type).stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public boolean owns(Player player, Type type, Cosmetic cosmetic) {
        return player.hasPermission("3smpcore.visuals.admin")
            || cosmetic.permission().isBlank()
            || unlocked(player.getUniqueId(), type).contains(cosmetic.id().toLowerCase(Locale.ROOT));
    }

    public void unlock(Player player, Type type, String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        List<String> owned = new ArrayList<>(unlocked(player.getUniqueId(), type));
        if (!owned.contains(normalized)) owned.add(normalized);
        storage.set(unlockedPath(player.getUniqueId(), type), owned);
        save();
    }

    public void select(Player player, Type type, String id) {
        storage.set(path(player.getUniqueId(), type), id.toLowerCase(Locale.ROOT));
        save();
    }

    public void selectCustom(Player player, Type type, String value) {
        if (type != Type.NAME_COLOR && type != Type.NAME_GRADIENT) return;
        storage.set(path(player.getUniqueId(), type), "custom");
        storage.set("players." + player.getUniqueId() + "." + type.storageKey() + "-custom", value);
        save();
    }

    public void clear(Player player, Type type) {
        storage.set(path(player.getUniqueId(), type), null);
        save();
    }

    public void reset(Player player) {
        storage.set("players." + player.getUniqueId(), null);
        save();
    }

    private String path(UUID uuid, Type type) {
        return "players." + uuid + "." + type.storageKey();
    }

    private String unlockedPath(UUID uuid, Type type) {
        return "players." + uuid + ".unlocked." + type.storageKey();
    }

    private List<String> unlocked(UUID uuid, Type type) {
        return storage.getStringList(unlockedPath(uuid, type)).stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
    }

    private void save() {
        try {
            storage.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save visual cosmetics: " + ex.getMessage());
        }
    }

    private YamlConfiguration config() {
        return configs.get("social/friends.yml");
    }
}
