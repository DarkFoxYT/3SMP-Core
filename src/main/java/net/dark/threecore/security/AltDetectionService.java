package net.dark.threecore.security;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class AltDetectionService implements Listener {
    private final ConfigFiles configs;

    public AltDetectionService(ConfigFiles configs) {
        this.configs = configs;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        var yaml = configs.get("admin/permissions.yml");
        if (!yaml.getBoolean("alt-detection.enabled", true)) return;
        int maxAccounts = yaml.getInt("alt-detection.max-accounts-per-ip", 5);
        if (maxAccounts <= 0) return;
        Player joining = event.getPlayer();
        if (joining.hasPermission("3smpcore.altlimit.bypass") || joining.hasPermission("3smpcore.admin")) return;
        String host = host(event.getAddress());
        if (host.isBlank()) return;

        int matchingOnline = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(joining.getUniqueId())) continue;
            if (host.equalsIgnoreCase(host(online))) matchingOnline++;
        }
        if (matchingOnline < maxAccounts) return;

        String message = yaml.getString("alt-detection.limit-message", "<red>You can only have <white>{max}</white> accounts online from the same connection.</red>")
                .replace("{max}", String.valueOf(maxAccounts));
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Text.mm(message));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var yaml = configs.get("admin/permissions.yml");
        if (!yaml.getBoolean("alt-detection.enabled", true)) return;
        Player joined = event.getPlayer();
        String host = host(joined);
        if (host.isBlank()) return;

        Set<String> matches = new LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(joined.getUniqueId())) continue;
            if (host.equalsIgnoreCase(host(online))) matches.add(online.getName());
        }
        if (matches.isEmpty()) return;

        String displayIp = yaml.getBoolean("alt-detection.show-ip-to-staff", true) ? host : "hidden";
        String message = yaml.getString("alt-detection.message", "<yellow>[Alt Check]</yellow> <white>{player}</white> <gray>may be an alt of</gray> <white>{matches}</white> <dark_gray>({ip})</dark_gray>")
                .replace("{player}", joined.getName())
                .replace("{matches}", String.join(", ", matches))
                .replace("{ip}", displayIp);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (canSee(staff)) Text.send(staff, message);
        }
    }

    private boolean canSee(Player player) {
        for (String permission : configs.get("admin/permissions.yml").getStringList("alt-detection.permissions")) {
            if (permission != null && !permission.isBlank() && player.hasPermission(permission)) return true;
        }
        return player.isOp() || player.hasPermission("3smpcore.admin");
    }

    private String host(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) return "";
        return host(address.getAddress());
    }

    private String host(java.net.InetAddress address) {
        if (address == null) return "";
        return address.getHostAddress().toLowerCase(Locale.ROOT);
    }
}
