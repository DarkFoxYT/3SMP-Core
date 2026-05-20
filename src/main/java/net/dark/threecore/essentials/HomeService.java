package net.dark.threecore.essentials;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.Database;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HomeService {
    private static final String DEFAULT_HOME = "home";
    private static final Pattern HOME_NAME = Pattern.compile("[a-z0-9_-]{1,24}");

    private final ConfigFiles configs;
    private final Database database;

    public HomeService(ConfigFiles configs, Database database) {
        this.configs = configs;
        this.database = database;
    }

    public void home(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        if (!player.hasPermission("3smpcore.home.use")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (!canUseFrom(player)) {
            Text.send(player, "<red>/home can only be used from spawn, survival, or market.</red>");
            return;
        }

        String name = normalizeHomeName(context.args().length == 0 ? DEFAULT_HOME : context.arg(0));
        if (name.isBlank()) {
            Text.send(player, "<red>Home names can use letters, numbers, underscores, and dashes.</red>");
            return;
        }

        SavedHome target = loadHome(player.getUniqueId(), name);
        List<SavedHome> homes = List.of();
        if (target == null && context.args().length == 0) {
            homes = homes(player.getUniqueId());
            if (homes.size() == 1) target = homes.getFirst();
        }
        if (target == null) {
            if (homes.isEmpty()) homes = homes(player.getUniqueId());
            Text.send(player, homes.isEmpty()
                    ? "<red>You do not have any homes yet. Use <white>/sethome</white>.</red>"
                    : "<red>Home not found.</red> <gray>Your homes:</gray> <white>" + homeList(homes) + "</white>");
            return;
        }
        if (isLockedEndWorld(target.world()) && !hasEndBypass(player)) {
            Text.send(player, endLockMessage());
            return;
        }
        World world = Bukkit.getWorld(target.world());
        if (world == null) {
            Text.send(player, "<red>That home's world is not loaded:</red> <white>" + target.world() + "</white>");
            return;
        }
        player.teleport(new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch()));
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Teleported to home</gradient> <white>" + target.name() + "</white><gray>.</gray>");
    }

    public void homes(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        if (!player.hasPermission("3smpcore.home.use")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        List<SavedHome> homes = homes(player.getUniqueId());
        if (homes.isEmpty()) {
            Text.send(player, "<yellow>No homes set yet. Use <white>/sethome</white>.</yellow>");
            return;
        }
        int limit = homeLimit(player);
        String limitText = limit == Integer.MAX_VALUE ? "unlimited" : String.valueOf(limit);
        Text.send(player, "<gray>Homes:</gray> <white>" + homeList(homes) + "</white> <dark_gray>(" + homes.size() + "/" + limitText + ")</dark_gray>");
    }

    public void setHome(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        if (!player.hasPermission("3smpcore.home.set")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (!canSetAt(player)) {
            Text.send(player, "<red>Homes can only be set in survival, the Nether, or market.</red>");
            return;
        }

        String name = normalizeHomeName(context.args().length == 0 ? DEFAULT_HOME : context.arg(0));
        if (name.isBlank()) {
            Text.send(player, "<red>Home names can use letters, numbers, underscores, and dashes.</red>");
            return;
        }

        boolean replacing = loadHome(player.getUniqueId(), name) != null;
        int limit = homeLimit(player);
        if (!replacing && limit != Integer.MAX_VALUE && homeCount(player.getUniqueId()) >= limit) {
            Text.send(player, "<red>You have reached your home limit.</red> <gray>Delete one with <white>/delhome</white>.</gray>");
            return;
        }

        saveHome(player.getUniqueId(), name, player.getLocation());
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Home saved:</gradient> <white>" + name + "</white><gray>.</gray>");
    }

    public void deleteHome(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        if (!player.hasPermission("3smpcore.home.delete")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        String name = normalizeHomeName(context.args().length == 0 ? DEFAULT_HOME : context.arg(0));
        if (name.isBlank()) {
            Text.send(player, "<red>Home names can use letters, numbers, underscores, and dashes.</red>");
            return;
        }
        if (!deleteHome(player.getUniqueId(), name)) {
            Text.send(player, "<red>Home not found.</red>");
            return;
        }
        Text.send(player, "<yellow>Deleted home <white>" + name + "</white>.</yellow>");
    }

    public List<String> completeHome(CommandContext context) {
        if (!(context.sender() instanceof Player player)) return List.of();
        String prefix = context.args().length == 0 ? "" : context.arg(context.args().length - 1).toLowerCase(Locale.ROOT);
        return homes(player.getUniqueId()).stream()
                .map(SavedHome::name)
                .filter(name -> name.startsWith(prefix))
                .toList();
    }

    public List<String> completeSetHome(CommandContext context) {
        return context.args().length <= 1 ? List.of(DEFAULT_HOME) : List.of();
    }

    private boolean canUseFrom(Player player) {
        return isSpawnWorld(player.getWorld()) || isHomeWorld(player.getWorld(), player) || hasBypass(player);
    }

    private boolean canSetAt(Player player) {
        return isHomeWorld(player.getWorld(), player) || hasBypass(player);
    }

    private boolean isHomeWorld(World world, Player player) {
        if (world == null) return false;
        String name = world.getName().toLowerCase(Locale.ROOT);
        String base = configs.get("world/survival.yml").getString("world", "world").toLowerCase(Locale.ROOT);
        String market = configs.get("world/market.yml").getString("world.name", "market").toLowerCase(Locale.ROOT);
        boolean end = isEndWorldName(name);
        return name.equals(base)
                || name.equals(base + "_nether")
                || name.equals(market)
                || (Bukkit.getWorld(base) == null && name.equals("world"))
                || (end && (!endLocked() || hasEndBypass(player)));
    }

    private boolean isSpawnWorld(World world) {
        if (world == null) return false;
        String configured = configs.get("core/config.yml").getString("spawn.world", "spawn");
        return world.getName().equalsIgnoreCase(configured) || world.getName().equalsIgnoreCase("spawn");
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("3smpcore.command.bypass")
                || player.hasPermission("3smpcore.staff.sradmin")
                || player.hasPermission("3smpcore.staff.admin")
                || player.hasPermission("3smpcore.admin")
                || player.isOp();
    }

    private boolean hasEndBypass(Player player) {
        String permission = configs.get("world/survival.yml").getString("end-lock.bypass-permission", "3smpcore.survival.end.bypass");
        return player.hasPermission(permission) || hasBypass(player);
    }

    private boolean isLockedEndWorld(String worldName) {
        return endLocked() && isEndWorldName(worldName);
    }

    private boolean isEndWorldName(String worldName) {
        if (worldName == null) return false;
        String name = worldName.toLowerCase(Locale.ROOT);
        String base = configs.get("world/survival.yml").getString("world", "world").toLowerCase(Locale.ROOT);
        return name.equals(base + "_the_end") || name.equals("world_the_end");
    }

    private boolean endLocked() {
        return configs.get("world/survival.yml").getBoolean("end-lock.enabled", true);
    }

    private String endLockMessage() {
        return configs.get("world/survival.yml").getString("end-lock.message", "<red>The End is locked in survival.</red>");
    }

    private int homeLimit(Player player) {
        int limit = Math.max(1, configs.get("world/survival.yml").getInt("homes.default-limit", 3));
        List<String> permissionLimits = configs.get("world/survival.yml").getStringList("homes.permission-limits");
        if (permissionLimits.isEmpty()) {
            permissionLimits = List.of(
                    "3smpcore.home.limit.10:10",
                    "3smpcore.home.limit.30:30",
                    "3smpcore.home.limit.unlimited:-1"
            );
        }
        for (String entry : permissionLimits) {
            int split = entry.lastIndexOf(':');
            if (split <= 0 || split >= entry.length() - 1) continue;
            String permission = entry.substring(0, split).trim();
            int value = parseInt(entry.substring(split + 1).trim(), limit);
            if (!permission.isBlank() && player.hasPermission(permission)) {
                if (value < 0) return Integer.MAX_VALUE;
                limit = Math.max(limit, value);
            }
        }
        if (player.hasPermission("3smpcore.home.limit.unlimited")) return Integer.MAX_VALUE;
        return limit;
    }

    private String normalizeHomeName(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_HOME;
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.length() > 24) name = name.substring(0, 24);
        return HOME_NAME.matcher(name).matches() ? name : "";
    }

    private String homeList(List<SavedHome> homes) {
        return String.join(", ", homes.stream().map(SavedHome::name).toList());
    }

    private SavedHome loadHome(UUID uuid, String name) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT name, world, x, y, z, yaw, pitch FROM player_homes WHERE uuid = ? AND name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new SavedHome(rs.getString("name"), rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load home", e);
        }
    }

    private List<SavedHome> homes(UUID uuid) {
        List<SavedHome> homes = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT name, world, x, y, z, yaw, pitch FROM player_homes WHERE uuid = ? ORDER BY name ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    homes.add(new SavedHome(rs.getString("name"), rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list homes", e);
        }
        return homes;
    }

    private int homeCount(UUID uuid) {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT COUNT(*) FROM player_homes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count homes", e);
        }
    }

    private void saveHome(UUID uuid, String name, Location location) {
        if (location.getWorld() == null) return;
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO player_homes(uuid, name, world, x, y, z, yaw, pitch, created_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid, name) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, location.getWorld().getName());
            ps.setDouble(4, location.getX());
            ps.setDouble(5, location.getY());
            ps.setDouble(6, location.getZ());
            ps.setFloat(7, location.getYaw());
            ps.setFloat(8, location.getPitch());
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save home", e);
        }
    }

    private boolean deleteHome(UUID uuid, String name) {
        try (PreparedStatement ps = database.connection().prepareStatement("DELETE FROM player_homes WHERE uuid = ? AND name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete home", e);
        }
    }

    private int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record SavedHome(String name, String world, double x, double y, double z, float yaw, float pitch) {}
}
