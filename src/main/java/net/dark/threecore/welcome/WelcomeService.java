package net.dark.threecore.welcome;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WelcomeService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Set<UUID> sentThisSession = ConcurrentHashMap.newKeySet();

    public WelcomeService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void reload() { }

    public void send(Player player) {
        if (player == null) return;
        if (!sentThisSession.add(player.getUniqueId())) return;
        sendDelayed(player);
    }

    private void sendDelayed(Player player) {
        var yaml = configs.get("core/welcome.yml");
        if (!yaml.getBoolean("welcome.enabled", true)) return;
        long delay = Math.max(0L, yaml.getLong("welcome.delay-ticks", 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (String line : yaml.getStringList("welcome.lines")) Text.raw(player, apply(line, player.getName()));
            String title = yaml.getString("welcome.title", "");
            if (!title.isBlank()) player.showTitle(net.kyori.adventure.title.Title.title(Text.mm(apply(title, player.getName())), Text.mm(apply(yaml.getString("welcome.subtitle", ""), player.getName()))));
            if (yaml.getBoolean("welcome.sound.enabled", true)) {
                try { player.playSound(player.getLocation(), Sound.valueOf(yaml.getString("welcome.sound.name", "ENTITY_PLAYER_LEVELUP")), 0.8f, 1.2f); } catch (Exception ignored) {}
            }
        }, delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sentThisSession.remove(event.getPlayer().getUniqueId());
    }

    private String apply(String input, String player) { return input == null ? "" : input.replace("{player}", player); }
}
