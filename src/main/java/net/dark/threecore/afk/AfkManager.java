package net.dark.threecore.afk;

import net.dark.threecore.config.ConfigFiles;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AfkManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, Long> activity = new HashMap<>();
    private int taskId = -1;

    public AfkManager(JavaPlugin plugin, ConfigFiles configs) { this.plugin = plugin; this.configs = configs; start(); }
    public void reload() { stop(); start(); }
    public void shutdown() { stop(); activity.clear(); }

    private void start() {
        if (!configs.get("afk.yml").getBoolean("enabled", true)) return;
        long interval = Math.max(20L, configs.get("afk.yml").getLong("check-interval-seconds", 30L) * 20L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::check, interval, interval);
    }
    private void stop() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
    private void touch(Player player) { activity.put(player.getUniqueId(), System.currentTimeMillis()); }
    private void check() {
        long timeout = configs.get("afk.yml").getLong("kick-after-seconds", 900L) * 1000L;
        String bypass = configs.get("afk.yml").getString("bypass-permission", "3smpcore.afk.bypass");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(bypass)) { touch(player); continue; }
            long last = activity.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
            if (System.currentTimeMillis() - last >= timeout) player.kick(MiniMessage.miniMessage().deserialize(configs.get("afk.yml").getString("kick-message", "<red>You were kicked for being AFK.</red>")));
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onMove(PlayerMoveEvent event) { if (event.getTo() != null && event.getFrom().distanceSquared(event.getTo()) > 0.0001D) touch(event.getPlayer()); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onPlace(BlockPlaceEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onChat(AsyncPlayerChatEvent event) { touch(event.getPlayer()); }
}
