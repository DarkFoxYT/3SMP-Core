package net.dark.threecore.hologram;

import net.dark.threecore.ThreeSMPCorePlugin;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.model.PlayerProgressionData;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

public final class HologramManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final List<ArmorStand> stands = new ArrayList<>();
    private final List<String> decentIds = new ArrayList<>();

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
            if (spawnDecent(id, base, lines)) continue;
            for (int i = 0; i < lines.size(); i++) {
                Location line = base.clone().add(0, -i * spacing, 0);
                line.setYaw(yaw);
                spawn(line, lines.get(i));
            }
        }
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "<yellow>/3smpcore hologram place id | reload | list</yellow>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { reload(); Text.send(sender, "<green>Holograms reloaded.</green>"); }
            case "list" -> Text.send(sender, "<gray>Holograms:</gray> <white>" + String.join(", ", ids()) + "</white>");
            case "place" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 2) { Text.send(sender, "<red>Usage: /3smpcore hologram place id</red>"); return; }
                place(player, args[1].toLowerCase(Locale.ROOT));
            }
            default -> Text.send(sender, "<yellow>/3smpcore hologram place id | reload | list</yellow>");
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("place", "reload", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) return ids();
        return List.of();
    }

    private void place(Player player, String id) {
        var root = configs.get("world/holograms.yml");
        if (!root.isConfigurationSection("holograms." + id)) {
            Text.send(player, "<red>Unknown hologram id.</red>");
            return;
        }
        Location loc = player.getLocation();
        String path = "holograms." + id;
        root.set(path + ".enabled", true);
        root.set(path + ".location.world", loc.getWorld().getName());
        root.set(path + ".location.x", round(loc.getX()));
        root.set(path + ".location.y", round(loc.getY() + 1.8D));
        root.set(path + ".location.z", round(loc.getZ()));
        root.set(path + ".location.yaw", (double) loc.getYaw());
        root.set(path + ".location.pitch", 0.0D);
        try {
            root.save(new File(plugin.getDataFolder(), "world/holograms.yml"));
        } catch (Exception ex) {
            Text.send(player, "<red>Could not save hologram location.</red>");
            return;
        }
        reload();
        Text.send(player, "<green>Placed hologram:</green> <white>" + id + "</white>");
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
        if (decentAvailable()) {
            for (String id : new ArrayList<>(decentIds)) removeDecent(id);
        }
        decentIds.clear();
    }

    private List<String> resolveLines(String type, List<String> configured) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "duels" -> duelLines(configured);
            case "money", "survival" -> moneyLines(configured);
            case "souls" -> sqlLines(configured, "player_souls", "balance", "<gradient:#6b7280:#f3f4f6>Souls Leaderboard</gradient>", "");
            case "fishing", "fishing_points" -> sqlLines(configured, "player_fishing_stats", "fishing_points", "<gradient:#38bdf8:#22c55e>Fishing Points</gradient>", " pts");
            case "fish", "fish_caught" -> sqlLines(configured, "player_fishing_stats", "fish_caught", "<gradient:#38bdf8:#22c55e>Fish Caught</gradient>", " fish");
            case "kills" -> killLines(configured);
            case "playtime" -> playtimeLines(configured);
            case "dungeons" -> configured.isEmpty() ? List.of("<gradient:#4c1d95:#a78bfa>Dungeon Leaderboard</gradient>", "<gray>Completion tracking ready.</gray>") : configured;
            default -> configured;
        };
    }

    private List<String> duelLines(List<String> fallback) {
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:#60a5fa:#c084fc>Duel Leaderboard</gradient>");
        List<UUID> top = Arrays.stream(Bukkit.getOfflinePlayers()).map(p -> p.getUniqueId()).sorted(Comparator.comparingInt((UUID id) -> repository.load(id).duelRating()).reversed()).limit(5).toList();
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

    private List<String> sqlLines(List<String> fallback, String table, String column, String title, String suffix) {
        Map<UUID, Long> values = new LinkedHashMap<>();
        if (!(plugin instanceof ThreeSMPCorePlugin core) || core.getDatabase() == null) return fallback;
        String sql = "SELECT uuid, " + column + " FROM " + table + " ORDER BY " + column + " DESC LIMIT 5";
        try (PreparedStatement ps = core.getDatabase().connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) values.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
        } catch (SQLException | IllegalArgumentException ex) {
            return fallback;
        }
        List<String> lines = new ArrayList<>();
        lines.add(title);
        int place = 1;
        for (Map.Entry<UUID, Long> entry : values.entrySet()) {
            lines.add("<white>#" + place++ + "</white> <gray>" + name(entry.getKey()) + "</gray> <#D6E8F7>" + String.format(Locale.ROOT, "%,d", entry.getValue()) + suffix + "</#D6E8F7>");
        }
        return lines.size() > 1 ? lines : fallback;
    }

    private List<String> killLines(List<String> fallback) {
        List<UUID> top = Arrays.stream(Bukkit.getOfflinePlayers()).map(p -> p.getUniqueId()).sorted(Comparator.comparingInt((UUID id) -> repository.load(id).duelKills()).reversed()).limit(5).toList();
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:#ef4444:#f97316>Kills Leaderboard</gradient>");
        int place = 1;
        for (UUID uuid : top) {
            int kills = repository.load(uuid).duelKills();
            if (kills <= 0) continue;
            lines.add("<white>#" + place++ + "</white> <gray>" + name(uuid) + "</gray> <red>" + kills + "</red>");
        }
        return lines.size() > 1 ? lines : fallback;
    }

    private List<String> playtimeLines(List<String> fallback) {
        List<Player> top = Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparingInt((Player p) -> p.getStatistic(Statistic.PLAY_ONE_MINUTE)).reversed()).limit(5).toList();
        List<String> lines = new ArrayList<>();
        lines.add("<gradient:#5B8DD9:#F8FBFF>Online Playtime</gradient>");
        int place = 1;
        for (Player player : top) {
            long minutes = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
            lines.add("<white>#" + place++ + "</white> <gray>" + player.getName() + "</gray> <#D6E8F7>" + minutes + "m</#D6E8F7>");
        }
        return lines.size() > 1 ? lines : fallback;
    }

    private Location location(String path) {
        var section = configs.get("world/holograms.yml").getConfigurationSection(path);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", "spawn"));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw", 0.0D), (float) section.getDouble("pitch", 0.0D));
    }

    private List<String> ids() {
        var root = configs.get("world/holograms.yml").getConfigurationSection("holograms");
        return root == null ? List.of() : new ArrayList<>(root.getKeys(false));
    }

    private String name(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString().substring(0, 8) : name;
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private boolean decentAvailable() {
        org.bukkit.plugin.Plugin decent = Bukkit.getPluginManager().getPlugin("DecentHolograms");
        return decent != null && decent.isEnabled();
    }

    private boolean spawnDecent(String id, Location location, List<String> lines) {
        if (!decentAvailable()) return false;
        try {
            removeDecent(id);
            Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            Method create = api.getMethod("createHologram", String.class, Location.class, List.class);
            create.invoke(null, "3smp_" + id, location, lines);
            decentIds.add("3smp_" + id);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void removeDecent(String id) {
        try {
            Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            Method get = api.getMethod("getHologram", String.class);
            Object hologram = get.invoke(null, id.startsWith("3smp_") ? id : "3smp_" + id);
            if (hologram == null) return;
            try {
                Method remove = api.getMethod("removeHologram", hologram.getClass());
                remove.invoke(null, hologram);
            } catch (NoSuchMethodException ignored) {
                Method delete = hologram.getClass().getMethod("delete");
                delete.invoke(hologram);
            }
        } catch (Throwable ignored) {
        }
    }
}
