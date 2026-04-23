package net.dark.threecore.particle;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ParticleManager {
    private final JavaPlugin plugin; private final ConfigFiles configs; private final Map<UUID, String> selected = new HashMap<>(); private BukkitTask task;
    public ParticleManager(JavaPlugin plugin, ConfigFiles configs) { this.plugin = plugin; this.configs = configs; }
    public void reload() { if (task != null) task.cancel(); task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); }
    public void shutdown() { if (task != null) task.cancel(); selected.clear(); }
    public void set(UUID uuid, String id) { selected.put(uuid, id); }
    public String get(UUID uuid) { return selected.getOrDefault(uuid, ""); }
    private void tick() { for (Player player : Bukkit.getOnlinePlayers()) { String id = selected.get(player.getUniqueId()); if (id == null || id.isBlank()) continue; String type = configs.get("cosmetics/particles.yml").getString("particles." + id + ".type", "ENCHANTMENT_TABLE"); if (type == null || type.equalsIgnoreCase("NONE")) continue; Particle particle; try { particle = Particle.valueOf(type); } catch (IllegalArgumentException ex) { continue; } player.getWorld().spawnParticle(particle, player.getLocation().add(0,1,0), 8, 0.4, 0.6, 0.4, 0.01); } }
}
