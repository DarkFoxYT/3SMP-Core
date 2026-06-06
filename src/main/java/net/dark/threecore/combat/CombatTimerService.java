package net.dark.threecore.combat;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.text.Text;
import net.dark.threecore.zonepvp.ZonePvpService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatTimerService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private BukkitTask task;
    private boolean enabled;
    private long durationMillis;
    private String actionBarMessage;
    private String endedActionBarMessage;
    private boolean showEndedMessage;
    private boolean ignoreDuels;
    private boolean ignoreZonePvp;
    private List<String> worlds;

    public CombatTimerService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
        reload();
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void reload() {
        YamlConfiguration config = configs.get("world/survival.yml");
        this.enabled = config.getBoolean("combat-tag.enabled", true);
        this.durationMillis = Math.max(1L, config.getLong("combat-tag.duration-seconds", 15L)) * 1000L;
        this.actionBarMessage = config.getString("combat-tag.actionbar", "<red>In combat</red> <dark_gray>|</dark_gray> <white>{seconds}s</white>");
        this.endedActionBarMessage = config.getString("combat-tag.ended-actionbar", "<gray>You are no longer in combat.</gray>");
        this.showEndedMessage = config.getBoolean("combat-tag.show-ended-actionbar", true);
        this.ignoreDuels = config.getBoolean("combat-tag.ignore-duels", true);
        this.ignoreZonePvp = config.getBoolean("combat-tag.ignore-zonepvp", false);
        this.worlds = config.getStringList("combat-tag.worlds").stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        if (!enabled) combatUntil.clear();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        combatUntil.clear();
    }

    public boolean isInCombat(Player player) {
        if (player == null) return false;
        return combatUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (ignored(victim) || ignored(attacker)) return;

        long until = System.currentTimeMillis() + durationMillis;
        combatUntil.put(victim.getUniqueId(), until);
        combatUntil.put(attacker.getUniqueId(), until);
        showCountdown(victim, until);
        showCountdown(attacker, until);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combatUntil.remove(event.getPlayer().getUniqueId());
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean ignored(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return true;
        if (!worlds.isEmpty() && !worlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) return true;
        if (ignoreDuels && DuelService.isDuelPlayer(player)) return true;
        return ignoreZonePvp && ZonePvpService.isZonePlayer(player);
    }

    private void tick() {
        if (!enabled || combatUntil.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : combatUntil.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                combatUntil.remove(entry.getKey());
                continue;
            }
            long until = entry.getValue();
            if (until <= now) {
                combatUntil.remove(entry.getKey());
                if (showEndedMessage && endedActionBarMessage != null && !endedActionBarMessage.isBlank()) {
                    Text.actionBar(player, endedActionBarMessage);
                }
                continue;
            }
            showCountdown(player, until);
        }
    }

    private void showCountdown(Player player, long until) {
        if (actionBarMessage == null || actionBarMessage.isBlank()) return;
        long seconds = Math.max(1L, (until - System.currentTimeMillis() + 999L) / 1000L);
        Text.actionBar(player, actionBarMessage.replace("{seconds}", String.valueOf(seconds)));
    }
}
