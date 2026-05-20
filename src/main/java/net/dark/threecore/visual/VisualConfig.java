package net.dark.threecore.visual;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VisualConfig {
    private final ConfigFiles configs;

    public VisualConfig(ConfigFiles configs) {
        this.configs = configs;
    }

    public void reload() {
        configs.reload();
    }

    public YamlConfiguration yaml() {
        return configs.get("social/friends.yml");
    }

    public String base() {
        return "friends";
    }

    public String tabPath() {
        return base() + ".tab";
    }

    public boolean visualsEnabled() {
        return yaml().getBoolean(tabPath() + ".visuals.enabled", true);
    }

    public boolean overrideTabPlugin() {
        return yaml().getBoolean(tabPath() + ".visuals.override-tab-plugin", true);
    }

    public boolean forceOverTabPlugin() {
        return yaml().getBoolean(tabPath() + ".visuals.force-over-tab-plugin", true);
    }

    public int tabRefreshTicks() {
        return Math.max(1, yaml().getInt(tabPath() + ".visuals.refresh-ticks", 20));
    }

    public List<String> header() {
        return yaml().getStringList(tabPath() + ".header");
    }

    public List<String> footer() {
        return yaml().getStringList(tabPath() + ".footer");
    }

    public String playerFormat() {
        return yaml().getString(tabPath() + ".player-format", "<rank_image> <tab_prefix>{grad:rank}<player>{/grad} <tab_tag>");
    }

    public boolean afkStatusEnabled() {
        return yaml().getBoolean(tabPath() + ".afk.enabled", true);
    }

    public boolean afkWorld(String world) {
        if (world == null || world.isBlank()) return false;
        for (String configured : afkWorlds()) {
            if (configured.equalsIgnoreCase(world)) return true;
        }
        return false;
    }

    public List<String> afkWorlds() {
        List<String> worlds = yaml().getStringList(tabPath() + ".afk.worlds");
        if (!worlds.isEmpty()) return worlds;
        List<String> fallback = new ArrayList<>();
        ConfigurationSection zones = configs.get("world/afk.yml").getConfigurationSection("zones");
        if (zones != null) {
            for (String id : zones.getKeys(false)) {
                String world = zones.getString(id + ".world", "");
                if (world != null && !world.isBlank()) fallback.add(world);
            }
        }
        return fallback;
    }

    public String afkPlayerFormatSuffix() {
        return yaml().getString(tabPath() + ".afk.suffix", " <dark_gray>[AFK]</dark_gray>");
    }

    public String nametagPrefix() {
        return yaml().getString(tabPath() + ".nametag-prefix", "<rank_image> <tab_prefix> ");
    }

    public String nametagSuffix() {
        return yaml().getString(tabPath() + ".nametag-suffix", " <tab_tag>");
    }

    public String nametagDisplay() {
        return yaml().getString(tabPath() + ".nametag-display", "<rank_image> <tab_prefix>{grad:rank}<player>{/grad}");
    }

    public boolean scoreboardEnabled() {
        return yaml().getBoolean(base() + ".scoreboards.enabled", true);
    }

    public int scoreboardRefreshTicks() {
        return Math.max(1, yaml().getInt(base() + ".scoreboards.refresh-ticks", 20));
    }

    public String activeBoard() {
        return yaml().getString(base() + ".scoreboards.active", "default");
    }

    public String boardTitle() {
        String board = activeBoard();
        return yaml().getString(base() + ".scoreboards.boards." + board + ".title", "{grad:royal_gold}&l3SMP{/grad}");
    }

    public List<String> boardLines() {
        String board = activeBoard();
        return yaml().getStringList(base() + ".scoreboards.boards." + board + ".lines");
    }

    public String gradient(String id) {
        String key = id == null || id.isBlank() ? "default" : id.toLowerCase(java.util.Locale.ROOT);
        if (key.startsWith("__cosmetic_")) {
            String cosmetic = key.substring("__cosmetic_".length());
            return yaml().getString("visual-cosmetics.name-gradients." + cosmetic + ".gradient", yaml().getString(tabPath() + ".gradients.default", "#f8fafc:#cbd5e1"));
        }
        if (key.startsWith("__gradient_literal_")) return key.substring("__gradient_literal_".length());
        if (key.startsWith("__color_literal_")) {
            String color = key.substring("__color_literal_".length());
            return color + ":" + color;
        }
        if (key.startsWith("__color_")) {
            String cosmetic = key.substring("__color_".length());
            String color = yaml().getString("visual-cosmetics.name-colors." + cosmetic + ".color", "#f8fafc");
            return color + ":" + color;
        }
        if (key.equals("__duel_red")) return yaml().getString(base() + ".tab.duels.team-one-gradient", "#ef4444:#fca5a5");
        if (key.equals("__duel_blue")) return yaml().getString(base() + ".tab.duels.team-two-gradient", "#2563eb:#93c5fd");
        if (key.equals("__duel_ffa")) return yaml().getString(base() + ".tab.duels.ffa-gradient", "#f4cd2a:#eda323:#d28d0d");
        return yaml().getString(tabPath() + ".gradients." + key, yaml().getString(tabPath() + ".gradients.default", "#f8fafc:#cbd5e1"));
    }

    public String shadow(String target, RankStyle rank) {
        if (!yaml().getBoolean(base() + ".tab.visuals.shadows.enabled", true)) return "";
        if (rank != null && rank.shadow() != null && !rank.shadow().isBlank()) return rank.shadow();
        String rankShadow = rank == null ? "" : yaml().getString(base() + ".tab.visuals.shadows.ranks." + rank.id(), "");
        if (rankShadow != null && !rankShadow.isBlank()) return rankShadow;
        String specific = yaml().getString(base() + ".tab.visuals.shadows." + target, "");
        if (specific != null && !specific.isBlank()) return specific;
        return yaml().getString(base() + ".tab.visuals.shadows.default", "#020617:0.70");
    }

    public String image(String id) {
        if (!yaml().getBoolean(base() + ".tab.visuals.images.enabled", true)) return "";
        return yaml().getString(base() + ".tab.visuals.images.placeholders." + id, "%img_" + id + "%");
    }

    public Set<String> imageIds() {
        Set<String> ids = new LinkedHashSet<>();
        ConfigurationSection section = yaml().getConfigurationSection(base() + ".tab.visuals.images.placeholders");
        if (section != null) ids.addAll(section.getKeys(false));
        if (ids.isEmpty()) ids.addAll(List.of("server_logo", "owner", "dev", "admin", "sr_admin", "mod", "builder", "sr_mod", "jr_mod", "3smp", "pro", "mvp", "ultra", "patron"));
        return ids;
    }

    public boolean stripMissingImages() {
        return yaml().getBoolean(base() + ".tab.visuals.images.strip-missing", false);
    }

    public boolean centerScoreboardLines() {
        return yaml().getBoolean(base() + ".scoreboards.center.enabled", true);
    }

    public boolean centerScoreboardTitle() {
        return yaml().getBoolean(base() + ".scoreboards.center.title", true);
    }

    public int scoreboardCenterWidth() {
        return Math.max(8, Math.min(64, yaml().getInt(base() + ".scoreboards.center.width", 28)));
    }

    public int scoreboardImageWidth(String id) {
        return Math.max(0, yaml().getInt(base() + ".scoreboards.center.image-widths." + id, 4));
    }

    public String chatMessageColor() {
        return yaml().getString(base() + ".tab.chat.message-color", "#dbeafe");
    }

    public boolean duelTeamNameColors() {
        return yaml().getBoolean(base() + ".tab.duels.name-colors-enabled", false);
    }

    public String worldDisplayName(String world) {
        return yaml().getString(base() + ".tab.world-display-names." + world, world);
    }

    public boolean hideLineNumbers() {
        return yaml().getBoolean(base() + ".scoreboards.hide-line-numbers", true);
    }

    public RankStyle rank(String id) {
        String key = id == null || id.isBlank() ? "default" : id.toLowerCase(java.util.Locale.ROOT);
        ConfigurationSection section = yaml().getConfigurationSection(tabPath() + ".ranks." + key);
        if (section == null) section = yaml().getConfigurationSection(tabPath() + ".ranks.default");
        if (section == null) return RankStyle.fallback();
        return new RankStyle(
            key,
            section.getString("image", ""),
            section.getString("prefix", "&7"),
            section.getString("tab-prefix", section.getString("prefix", "&7")),
            section.getString("gradient", key),
            section.getInt("sort-weight", key.equals("default") ? 999 : 100),
            ""
        );
    }

    public List<String> rankOrder() {
        List<String> order = yaml().getStringList(tabPath() + ".sorting.order");
        return order.isEmpty() ? new ArrayList<>(List.of("owner", "dev", "admin", "ultra", "mvp", "pro", "3", "member", "default")) : order;
    }
}
