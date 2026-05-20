package net.dark.threecore.screentext;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ScreenTextRegistry {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<String, ScreenText> templates = new ConcurrentHashMap<>();
    private final Set<String> imageIds = ConcurrentHashMap.newKeySet();

    public ScreenTextRegistry(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
        reload();
    }

    public void reload() {
        configs.reload();
        templates.clear();
        imageIds.clear();
        ConfigurationSection imageSection = yaml().getConfigurationSection("screen-text.images.placeholders");
        if (imageSection != null) imageIds.addAll(imageSection.getKeys(false));
        ConfigurationSection section = yaml().getConfigurationSection("screen-texts");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ScreenText text = loadTemplate(id, section.getConfigurationSection(id));
            if (text != null) templates.put(id.toLowerCase(Locale.ROOT), text);
        }
    }

    public ScreenText template(String id) {
        return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> templateIds() {
        return templates.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<ScreenText> templates() {
        return templates.values().stream()
            .sorted((a, b) -> a.id().compareToIgnoreCase(b.id()))
            .toList();
    }

    public int templateCount() {
        return templates.size();
    }

    public int renderIntervalTicks() {
        return Math.max(1, yaml().getInt("screen-text.render-interval-ticks", 2));
    }

    public boolean rainbowEnabled() {
        return yaml().getBoolean("screen-text.rainbow-enabled", false);
    }

    public String gradient(String id) {
        String key = id == null || id.isBlank() ? "default" : id.toLowerCase(Locale.ROOT);
        return yaml().getString("screen-text.gradients." + key, yaml().getString("screen-text.gradients.default", "#f8fafc:#cbd5e1"));
    }

    public String image(String id) {
        if (!yaml().getBoolean("screen-text.images.enabled", true)) return "";
        return yaml().getString("screen-text.images.placeholders." + id, "%img_" + id + "%");
    }

    public Set<String> imageIds() {
        Set<String> ids = new LinkedHashSet<>(imageIds);
        ids.addAll(List.of("server_logo", "owner", "dev", "admin", "sr_admin", "mod", "builder", "sr_mod", "jr_mod", "3smp", "pro", "mvp", "ultra", "patron"));
        return ids;
    }

    public void saveTemplate(ScreenText text) {
        YamlConfiguration yaml = yaml();
        String path = "screen-texts." + text.id();
        yaml.set(path + ".content", List.of(text.content().split("\n", -1)));
        yaml.set(path + ".position.anchor", text.position().name());
        yaml.set(path + ".position.x", text.x());
        yaml.set(path + ".position.y", text.y());
        yaml.set(path + ".position.offsetX", text.offsetX());
        yaml.set(path + ".position.offsetY", text.offsetY());
        yaml.set(path + ".layer", text.layer().name());
        yaml.set(path + ".priority", text.priority());
        yaml.set(path + ".z-index", text.zIndex());
        yaml.set(path + ".type", text.type().name());
        yaml.set(path + ".duration", text.durationMillis() + "ms");
        yaml.set(path + ".refresh-ticks", text.refreshTicks());
        yaml.set(path + ".condition", text.condition());
        ScreenTextStyle style = text.style();
        yaml.set(path + ".style.gradient", style.gradient());
        yaml.set(path + ".style.color", style.color());
        yaml.set(path + ".style.bold", style.bold());
        yaml.set(path + ".style.italic", style.italic());
        yaml.set(path + ".style.underline", style.underline());
        yaml.set(path + ".style.letter-spacing", style.letterSpacing());
        yaml.set(path + ".style.line-spacing", style.lineSpacing());
        yaml.set(path + ".style.alignment", style.alignment().name());
        yaml.set(path + ".style.max-width", style.maxWidth());
        yaml.set(path + ".style.opacity", style.opacity());
        yaml.set(path + ".style.scale", style.scale());
        yaml.set(path + ".style.rainbow", style.rainbow());
        ScreenTextStyle.Shadow shadow = style.shadow();
        yaml.set(path + ".style.shadow.enabled", shadow.enabled());
        yaml.set(path + ".style.shadow.color", shadow.color());
        yaml.set(path + ".style.shadow.alpha", shadow.alpha());
        yaml.set(path + ".style.shadow.offsetX", shadow.offsetX());
        yaml.set(path + ".style.shadow.offsetY", shadow.offsetY());
        yaml.set(path + ".style.shadow.blur", shadow.blur());
        yaml.set(path + ".animation.type", text.animation().type().name());
        yaml.set(path + ".animation.duration", text.animation().durationMillis() + "ms");
        yaml.set(path + ".animation.delay", text.animation().delayMillis() + "ms");
        yaml.set(path + ".animation.easing", text.animation().easing().name());
        try {
            yaml.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save screen text template " + text.id() + ": " + ex.getMessage());
        }
        reload();
    }

    private ScreenText loadTemplate(String id, ConfigurationSection section) {
        if (section == null) return null;
        ScreenTextBuilder builder = ScreenText.builder().id(id);
        if (section.isList("content")) builder.content(section.getStringList("content"));
        else builder.content(section.getString("content", ""));
        ConfigurationSection position = section.getConfigurationSection("position");
        if (position == null) builder.position(enumValue(Position.class, section.getString("position", "TOP_CENTER"), Position.TOP_CENTER));
        else {
            Position anchor = enumValue(Position.class, position.getString("anchor", "TOP_CENTER"), Position.TOP_CENTER);
            builder.position(anchor).offset(position.getInt("offsetX", 0), position.getInt("offsetY", 0));
            if (anchor == Position.ABSOLUTE) builder.absolute(position.getDouble("x", 0.5D), position.getDouble("y", 0.5D));
        }
        builder.layer(enumValue(Layer.class, section.getString("layer", "HUD"), Layer.HUD));
        builder.priority(section.getInt("priority", 0));
        builder.zIndex(section.getInt("z-index", section.getInt("zIndex", 0)));
        builder.type(enumValue(ScreenTextType.class, section.getString("type", section.getLong("duration", 0L) > 0L ? "TIMED" : "STATIC"), ScreenTextType.TIMED));
        builder.duration(parseDuration(section.get("duration"), 0L));
        builder.refreshTicks(section.getInt("refresh-ticks", 10));
        builder.condition(section.getString("condition", ""));
        builder.style(loadStyle(section.getConfigurationSection("style")));
        builder.animation(loadAnimation(section.getConfigurationSection("animation")));
        return builder.build();
    }

    private ScreenTextStyle loadStyle(ConfigurationSection section) {
        ScreenTextStyle.Builder builder = ScreenTextStyle.builder();
        if (section == null) return builder.build();
        builder.gradient(section.getString("gradient", ""));
        builder.color(section.getString("color", ""));
        builder.bold(section.getBoolean("bold", false));
        builder.italic(section.getBoolean("italic", false));
        builder.underline(section.getBoolean("underline", false));
        builder.letterSpacing(section.getInt("letter-spacing", section.getInt("letterSpacing", 0)));
        builder.lineSpacing(section.getInt("line-spacing", section.getInt("lineSpacing", 0)));
        builder.alignment(enumValue(TextAlignment.class, section.getString("alignment", "CENTER"), TextAlignment.CENTER));
        builder.maxWidth(section.getInt("max-width", section.getInt("maxWidth", 0)));
        builder.opacity(section.getDouble("opacity", 1.0D));
        builder.scale(section.getDouble("scale", 1.0D));
        builder.rainbow(section.getBoolean("rainbow", false));
        if (section.isBoolean("shadow")) builder.shadow(section.getBoolean("shadow", true));
        ConfigurationSection shadow = section.getConfigurationSection("shadow");
        if (shadow != null) {
            builder.shadow(new ScreenTextStyle.Shadow(
                shadow.getBoolean("enabled", true),
                shadow.getString("color", "#020617"),
                shadow.getDouble("alpha", 0.8D),
                shadow.getInt("offsetX", 1),
                shadow.getInt("offsetY", 1),
                shadow.getInt("blur", 0)
            ));
        }
        return builder.build();
    }

    private ScreenTextAnimation loadAnimation(ConfigurationSection section) {
        if (section == null) return ScreenTextAnimation.none();
        return ScreenTextAnimation.builder()
            .type(enumValue(ScreenTextAnimation.Type.class, section.getString("type", "NONE"), ScreenTextAnimation.Type.NONE))
            .duration(parseDuration(section.get("duration"), 0L))
            .delay(parseDuration(section.get("delay"), 0L))
            .easing(enumValue(ScreenTextAnimation.Easing.class, section.getString("easing", "EASE_OUT"), ScreenTextAnimation.Easing.EASE_OUT))
            .build();
    }

    private long parseDuration(Object raw, long fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number number) return number.longValue();
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        try {
            if (text.endsWith("ms")) return Long.parseLong(text.substring(0, text.length() - 2).trim());
            if (text.endsWith("s")) return Math.round(Double.parseDouble(text.substring(0, text.length() - 1).trim()) * 1000.0D);
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private YamlConfiguration yaml() {
        return configs.get("screen/screen-texts.yml");
    }

    private File file() {
        return new File(plugin.getDataFolder(), "screen/screen-texts.yml");
    }
}
