package net.dark.threecore.hologram;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.model.PlayerProgressionData;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class HologramManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final List<ArmorStand> stands = new ArrayList<>();

    public HologramManager(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
    }

    public void reload() {
        removeAll();
        var root = configs.get("world/holograms.yml").getConfigurationSection("holograms");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            if (!root.getBoolean(id + ".enabled", true)) continue;
            Location base = location("holograms." + id + ".location");
            if (base == null || base.getWorld() == null) continue;
            List<String> lines = resolveLines(root.getString(id + ".type", "static"), root.getStringList(id + ".lines"));
            double spacing = root.getDouble(id + ".line-spacing", 0.28D);
            float yaw = (float) root.getDouble(id + ".location.yaw", base.getYaw());
            for (int i = 0; i < lines.size(); i++) {
                Location line = base.clone().add(0, -i * spacing, 0);
                line.setYaw(yaw);
                spawn(line, lines.get(i));
            }
        }
    }

    public ArmorStand spawn(Location location, String text) {
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setRotation(location.getYaw(), location.getPitch());
            s.customName(Text.mm(text));
            s.setCustomNameVisible(true);
        });
        stands.add(stand);
        return stand;
    }

    public void removeAll() {
        for (Entity entity : new ArrayList<>(stands)) entity.remove();
        stands.clear();
    }

    private List<String> resolveLines(String type, List<String> configured) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "duels" -> duelLines(configured);
            case "money", "survival" -> moneyLines(configured);
            case "dungeons" -> configured.isEmpty() ? List.of("<gradient:#4c1d95:#a78bfa>Dungeon Leaderboard</gradient>", "<gray>Completion tracking ready.</gray>") : configured;
            default -> configured;
        };
    }

    private List<String> duelLines(List<String> fallback) {
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:#60a5fa:#c084fc>Duel Leaderboard</gradient>");
        List<UUID> top = Arrays.stream(Bukkit.getOfflinePlayers()).map(p -> p.getUniqueId()).sorted(Comparator.comparingInt((UUID id) -> repository.load(id).duelRating()).reversed()).limit(10).toList();
        int place = 1;
        for (UUID uuid : top) {
            PlayerProgressionData data = repository.load(uuid);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            lines.add("<white>#" + place++ + "</white> <gray>" + (name == null ? uuid.toString().substring(0, 8) : name) + "</gray> <aqua>" + data.duelRating() + "</aqua>");
        }
        return lines.size() > 1 ? lines : fallback;
    }

    private List<String> moneyLines(List<String> fallback) {
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:#f59e0b:#fbbf24>Survival Money</gradient>");
        List<UUID> top = Arrays.stream(Bukkit.getOfflinePlayers()).map(p -> p.getUniqueId()).sorted(Comparator.comparingDouble((UUID id) -> repository.getMoneyBalance(id)).reversed()).limit(5).toList();
        int place = 1;
        for (UUID uuid : top) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            lines.add("<white>#" + place++ + "</white> <gray>" + (name == null ? uuid.toString().substring(0, 8) : name) + "</gray> <gold>$" + String.format(Locale.ROOT, "%,.0f", repository.getMoneyBalance(uuid)) + "</gold>");
        }
        return lines.size() > 1 ? lines : fallback;
    }

    private Location location(String path) {
        var section = configs.get("world/holograms.yml").getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", "spawn"));
        if (world == null) return null;
        return new Location(world, section.getDouble("x", 0.0D), section.getDouble("y", 68.0D), section.getDouble("z", 0.0D), (float) section.getDouble("yaw", 0.0D), (float) section.getDouble("pitch", 0.0D));
    }
}
