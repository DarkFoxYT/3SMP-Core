package net.dark.threecore.gems;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SeasonalGemRegistry {
    private final Set<Material> supported;
    private final Map<String, GemStats> baseStats = new HashMap<>();
    private final Map<String, Map<String, GemStats>> seasonalStats = new HashMap<>();
    private final String currentSeason;

    public SeasonalGemRegistry(ConfigFiles configs) {
        this.currentSeason = configs.get("gems/gems.yml").getString("gems.current-season", "").toLowerCase(Locale.ROOT);
        var list = configs.get("gems/gems.yml").getStringList("gems.supported-materials");
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : list) {
            try {
                set.add(Material.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        this.supported = set;
        loadDefinitions(configs, "gems.definitions", baseStats);
        var seasons = configs.get("gems/gems.yml").getConfigurationSection("gems.seasons");
        if (seasons != null) {
            for (String seasonId : seasons.getKeys(false)) {
                Map<String, GemStats> seasonal = new HashMap<>();
                loadDefinitions(configs, "gems.seasons." + seasonId + ".definitions", seasonal);
                seasonalStats.put(seasonId.toLowerCase(Locale.ROOT), seasonal);
            }
        }
    }

    public boolean supports(Material material) {
        return supported.contains(material);
    }

    public boolean isDuelRestricted(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        Material type = item.getType();
        return type == Material.DIAMOND_SWORD || type == Material.NETHERITE_SWORD || type == Material.DIAMOND_AXE || type == Material.NETHERITE_AXE;
    }

    public GemStats stats(String gemId) {
        if (gemId == null) return null;
        String key = gemId.toLowerCase(Locale.ROOT);
        Map<String, GemStats> season = currentSeason.isBlank() ? null : seasonalStats.get(currentSeason);
        if (season != null && season.containsKey(key)) return season.get(key);
        return baseStats.get(key);
    }

    public Particle particle(String gemId) {
        GemStats stats = stats(gemId);
        if (stats == null) return Particle.END_ROD;
        try {
            return Particle.valueOf(stats.particle());
        } catch (Exception ex) {
            return Particle.END_ROD;
        }
    }

    public double scale(String gemId) {
        return scale(stats(gemId));
    }

    public int value(String gemId) {
        GemStats stats = stats(gemId);
        return stats == null ? 1 : stats.value();
    }

    public String effect(String gemId) {
        GemStats stats = stats(gemId);
        return stats == null ? "" : stats.effect();
    }

    public double procChance(String gemId) {
        GemStats stats = stats(gemId);
        return stats == null ? 0.0 : stats.procChance();
    }

    public long cooldownTicks(String gemId) {
        GemStats stats = stats(gemId);
        return stats == null ? 0L : stats.cooldownTicks();
    }

    public String tier(String gemId) {
        GemStats stats = stats(gemId);
        return stats == null ? "ROUGH" : stats.tier();
    }

    public String displayName(String gemId) {
        GemStats stats = stats(gemId);
        return stats == null ? gemId : stats.displayName();
    }

    public String currentSeason() {
        return currentSeason.isBlank() ? "default" : currentSeason;
    }

    public Set<String> gemIds() {
        return Set.copyOf(baseStats.keySet());
    }

    public Map<String, GemStats> statsSnapshot() {
        Map<String, GemStats> map = new HashMap<>(baseStats);
        Map<String, GemStats> season = currentSeason.isBlank() ? null : seasonalStats.get(currentSeason);
        if (season != null) map.putAll(season);
        return map;
    }

    public String statSummary(String gemId) {
        GemStats stats = stats(gemId);
        if (stats == null) return "<gray>No gem data available.</gray>";
        return "<gray>Effect:</gray> <white>" + stats.effect() + "</white> <gray>| Tier:</gray> <white>" + stats.tier() + "</white> <gray>| Value:</gray> <white>" + stats.value() + "</white> <gray>| Proc:</gray> <white>" + Math.round(stats.procChance() * 100.0) + "%</white> <gray>| Cooldown:</gray> <white>" + (stats.cooldownTicks() / 20L) + "s</white>";
    }

    private void loadDefinitions(ConfigFiles configs, String path, Map<String, GemStats> target) {
        var defs = configs.get("gems/gems.yml").getConfigurationSection(path);
        if (defs == null) return;
        for (String id : defs.getKeys(false)) {
            var sec = defs.getConfigurationSection(id);
            if (sec == null) continue;
            String gemId = id.toLowerCase(Locale.ROOT);
            target.put(gemId, new GemStats(
                    sec.getString("display-name", id),
                    sec.getString("effect", ""),
                    sec.getInt("value", 1),
                    sec.getString("particle", defaultParticle(gemId)).toUpperCase(Locale.ROOT),
                    sec.getString("tier", "ROUGH"),
                    sec.getDouble("proc-chance", sec.getDouble("procChance", 0.0)),
                    sec.getLong("cooldown-ticks", sec.getLong("cooldownTicks", 0L))
            ));
        }
    }

    private double scale(GemStats stats) {
        if (stats == null) return 1.0;
        return switch (stats.tier().toUpperCase(Locale.ROOT)) {
            case "POLISHED" -> 1.1;
            case "CUT" -> 1.2;
            case "FLAWLESS" -> 1.35;
            case "PRISMATIC" -> 1.6;
            default -> 1.0;
        };
    }

    private String defaultParticle(String id) {
        return switch (id) {
            case "power" -> "CRIT";
            case "vitality" -> "HEART";
            case "speed" -> "CLOUD";
            case "prismatic" -> "ELECTRIC_SPARK";
            default -> "END_ROD";
        };
    }

    public record GemStats(String displayName, String effect, int value, String particle, String tier, double procChance, long cooldownTicks) {}
}
