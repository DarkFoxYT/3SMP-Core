package net.dark.threecore.command;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class CommandRestrictionService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public CommandRestrictionService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!configs.get("admin/permissions.yml").getBoolean("world-command-rules.enabled", true)) return;
        Player player = event.getPlayer();
        if (hasBypass(player)) return;
        String command = root(event.getMessage());
        if (command.isBlank()) return;
        if (isAdminOnly(command) && !hasAdminCommandPermission(player, command)) {
            event.setCancelled(true);
            Text.send(player, configs.get("admin/permissions.yml").getString("admin-only-commands.deny-message", "<red>No permission.</red>"));
            return;
        }
        String group = groupFor(player.getWorld());
        if (group.isBlank()) return;
        List<String> allowed = configs.get("admin/permissions.yml").getStringList("world-command-rules.groups." + group + ".allowed");
        if (allowed.isEmpty() || matchesAny(command, allowed)) return;
        if (isUpgradeSafeAllowed(command, group)) return;
        event.setCancelled(true);
        Text.send(player, configs.get("admin/permissions.yml").getString("world-command-rules.deny-message", "<red>You cannot use that command in this world.</red>"));
    }

    private String groupFor(World world) {
        if (world == null) return "";
        String name = world.getName().toLowerCase(Locale.ROOT);
        var root = configs.get("admin/permissions.yml").getConfigurationSection("world-command-rules.groups");
        if (root == null) return "";
        for (String group : root.getKeys(false)) {
            for (String exact : configs.get("admin/permissions.yml").getStringList("world-command-rules.groups." + group + ".worlds")) {
                if (name.equals(exact.toLowerCase(Locale.ROOT))) return group;
            }
            for (String prefix : configs.get("admin/permissions.yml").getStringList("world-command-rules.groups." + group + ".world-prefixes")) {
                if (name.startsWith(prefix.toLowerCase(Locale.ROOT))) return group;
            }
        }
        return "";
    }

    private String root(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.startsWith("/") ? raw.substring(1) : raw;
        int space = trimmed.indexOf(' ');
        return (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
    }

    private boolean matchesAny(String command, List<String> allowed) {
        String bare = command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;
        for (String entry : allowed) {
            String normalized = entry == null ? "" : entry.toLowerCase(Locale.ROOT).replace("/", "").trim();
            if (normalized.isBlank()) continue;
            if (command.equals(normalized) || bare.equals(normalized) || command.startsWith(normalized + ":")) return true;
        }
        return false;
    }

    private boolean isUpgradeSafeAllowed(String command, String group) {
        String bare = command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;
        if (group.equalsIgnoreCase("spawn")) {
            return List.of("survival", "rtp", "back", "backpack", "home", "homes", "sethome", "homeset", "delhome", "deletehome", "removehome").contains(bare);
        }
        if (group.equalsIgnoreCase("survival") || group.equalsIgnoreCase("market")) {
            return List.of("home", "homes", "sethome", "homeset", "delhome", "deletehome", "removehome").contains(bare);
        }
        return false;
    }

    private boolean hasBypass(Player player) {
        for (String permission : configs.get("admin/permissions.yml").getStringList("world-command-rules.bypass-permissions")) {
            if (!permission.isBlank() && player.hasPermission(permission)) return true;
        }
        return player.hasPermission("3smpcore.admin") || player.isOp();
    }

    private boolean isAdminOnly(String command) {
        String bare = command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;
        for (String entry : configs.get("admin/permissions.yml").getStringList("admin-only-commands.commands")) {
            String normalized = entry == null ? "" : entry.toLowerCase(Locale.ROOT).replace("/", "").trim();
            if (!normalized.isBlank() && (command.equals(normalized) || bare.equals(normalized))) return true;
        }
        return false;
    }

    private boolean hasAdminCommandPermission(Player player, String command) {
        String bare = command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;
        String specific = configs.get("admin/permissions.yml").getString("admin-only-commands.permissions." + bare, "");
        if (specific != null && !specific.isBlank() && player.hasPermission(specific)) return true;
        for (String permission : configs.get("admin/permissions.yml").getStringList("admin-only-commands.bypass-permissions")) {
            if (!permission.isBlank() && player.hasPermission(permission)) return true;
        }
        return false;
    }
}
