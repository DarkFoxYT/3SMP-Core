package net.dark.threecore.welcome;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;

public final class WelcomeService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public WelcomeService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void reload() { }

    public void send(org.bukkit.entity.Player player) { sendDelayed(player); }

    private void sendDelayed(org.bukkit.entity.Player player) {
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

    private String apply(String input, String player) { return input == null ? "" : input.replace("{player}", player); }
}
