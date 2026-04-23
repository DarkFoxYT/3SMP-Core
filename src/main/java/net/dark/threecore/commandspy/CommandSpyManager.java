package net.dark.threecore.commandspy;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class CommandSpyManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Set<UUID> spies = new HashSet<>();

    public CommandSpyManager(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void reload() { }

    public void toggle(Player player) {
        if (!spies.add(player.getUniqueId())) spies.remove(player.getUniqueId());
        Text.send(player, spies.contains(player.getUniqueId()) ? "<green>Command spy enabled.</green>" : "<gray>Command spy disabled.</gray>");
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage();
        if (ignored(command)) return;
        sendInGame(event.getPlayer(), command);
        sendDiscord(event.getPlayer(), command);
    }

    private void sendInGame(Player player, String command) {
        if (spies.isEmpty()) return;
        String msg = "<gray>[Spy]</gray> <white>" + player.getName() + "</white> <dark_gray>></dark_gray> <aqua>" + escape(command) + "</aqua>";
        Component hover = Text.mm("<gray>Player: " + player.getName() + "</gray>\n<gray>World: " + player.getWorld().getName() + "</gray>");
        Component line = Text.mm(msg).hoverEvent(HoverEvent.showText(hover));
        for (UUID uuid : spies) {
            Player spy = Bukkit.getPlayer(uuid);
            if (spy != null) spy.sendMessage(line);
        }
    }

    private void sendDiscord(Player player, String command) {
        var yaml = configs.get("admin/commandspy.yml");
        if (!yaml.getBoolean("discord.enabled", false)) return;
        if (yaml.getBoolean("discord.ops-only", false) && !player.isOp()) return;
        String webhook = yaml.getString("discord.webhook-url", "");
        if (webhook == null || webhook.isBlank() || webhook.contains("YOUR_WEBHOOK")) return;
        String content = yaml.getString("discord.format", "**{player}** used `{command}` in `{world}`")
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", player.getWorld().getName())
                .replace("{command}", command.replace("`", "'"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> post(webhook, content));
    }

    private void post(String webhook, String content) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(webhook).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            String body = "{\"content\":\"" + json(content) + "\"}";
            try (OutputStream out = conn.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            conn.getInputStream().close();
        } catch (Exception ex) {
            plugin.getLogger().warning("Discord command spy webhook failed: " + ex.getMessage());
        }
    }

    private boolean ignored(String command) {
        String lower = command.toLowerCase(Locale.ROOT);
        for (String ignored : configs.get("admin/commandspy.yml").getStringList("ignored-commands")) if (lower.startsWith("/" + ignored.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    private String escape(String input) { return input.replace("<", "&lt;").replace(">", "&gt;"); }
    private String json(String input) { return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
