package net.dark.threecore.spawn;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import net.dark.threecore.zonepvp.ZonePvpService;

public final class SpawnZoneManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private ZonePvpService zonePvpService;

    public SpawnZoneManager(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void reload() { }
    public void zonePvpService(ZonePvpService service) { this.zonePvpService = service; }

    public boolean inSpawnZone(Player player) { return inZone(player.getLocation()); }

    public boolean inZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        var yaml = configs.get("config.yml");
        if (!yaml.getBoolean("spawn.zone.enabled", true)) return false;
        String world = yaml.getString("spawn.zone.world", yaml.getString("spawn.world", "spawn"));
        if (!loc.getWorld().getName().equalsIgnoreCase(world)) return false;
        double minX = Math.min(yaml.getDouble("spawn.zone.min.x", -100), yaml.getDouble("spawn.zone.max.x", 100));
        double maxX = Math.max(yaml.getDouble("spawn.zone.min.x", -100), yaml.getDouble("spawn.zone.max.x", 100));
        double minY = Math.min(yaml.getDouble("spawn.zone.min.y", -64), yaml.getDouble("spawn.zone.max.y", 320));
        double maxY = Math.max(yaml.getDouble("spawn.zone.min.y", -64), yaml.getDouble("spawn.zone.max.y", 320));
        double minZ = Math.min(yaml.getDouble("spawn.zone.min.z", -100), yaml.getDouble("spawn.zone.max.z", 100));
        double maxZ = Math.max(yaml.getDouble("spawn.zone.min.z", -100), yaml.getDouble("spawn.zone.max.z", 100));
        return loc.getX() >= minX && loc.getX() <= maxX && loc.getY() >= minY && loc.getY() <= maxY && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    public void refresh(Player player) {
        if (zonePvpService != null && zonePvpService.isActive(player)) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.SATURATION);
            return;
        }
        if (inSpawnZone(player) && configs.get("config.yml").getBoolean("spawn.effects.speed.enabled", true)) {
            int amplifier = Math.max(0, configs.get("config.yml").getInt("spawn.effects.speed.level", configs.get("config.yml").getInt("spawn.speed-level", 2)) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, amplifier, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
            player.setFoodLevel(20); player.setSaturation(20.0f);
        } else if (configs.get("config.yml").getBoolean("spawn.effects.speed.remove-on-exit", true)) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null && effect.getDuration() > 20 * 30) player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.SATURATION);
        }
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (zonePvpService != null && zonePvpService.isActive(player)) return;
        if (inSpawnZone(player) && configs.get("config.yml").getBoolean("spawn.zone.disable-damage", true)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = event.getDamager() instanceof Player p ? p : null;
        if (victim == null || attacker == null) return;
        if (zonePvpService != null && (zonePvpService.isActive(victim) || zonePvpService.isActive(attacker))) return;
        if (!configs.get("config.yml").getBoolean("spawn.zone.pvp.enabled", false) && (inSpawnZone(victim) || inSpawnZone(attacker))) {
            event.setCancelled(true);
            Text.send(attacker, configs.get("config.yml").getString("spawn.zone.pvp-deny-message", "<red>PvP is disabled in spawn.</red>"));
        }
    }
}
