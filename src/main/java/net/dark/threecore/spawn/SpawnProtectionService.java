package net.dark.threecore.spawn;

import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Particle;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SpawnProtectionService implements Listener {
    private final JavaPlugin plugin;
    private net.dark.threecore.config.ConfigFiles configs;
    private final Set<UUID> protectedPlayers = new HashSet<>();

    public SpawnProtectionService(JavaPlugin plugin) { this.plugin = plugin; }
    public SpawnProtectionService(JavaPlugin plugin, net.dark.threecore.config.ConfigFiles configs) { this.plugin = plugin; this.configs = configs; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applySpawnState(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applySpawnState(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> applySpawnState(event.getPlayer()));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!isProtected(event.getTo())) return;
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, true, false, false));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlockPlaced().getLocation()) && !event.getPlayer().hasPermission("3smpcore.spawn.build")) {
            event.setCancelled(true);
            event.getPlayer().getWorld().spawnParticle(Particle.END_ROD, event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5), 12, 0.25, 0.25, 0.25, 0.02);
            Text.send(event.getPlayer(), "<red>Building is disabled in spawn.</red>");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getLocation()) && !event.getPlayer().hasPermission("3smpcore.spawn.build")) {
            event.setCancelled(true);
            event.getPlayer().getWorld().spawnParticle(Particle.END_ROD, event.getBlock().getLocation().add(0.5, 0.5, 0.5), 12, 0.25, 0.25, 0.25, 0.02);
            Text.send(event.getPlayer(), "<red>Building is disabled in spawn.</red>");
        }
    }

    public void applySpawnState(Player player) {
        if (!isProtected(player.getLocation())) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    private boolean isProtected(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (configs == null) return isSpawnWorld(loc.getWorld().getName());
        String world = configs.get("config.yml").getString("spawn.world", "spawn");
        if (!loc.getWorld().getName().equalsIgnoreCase(world)) return false;
        double radius = configs.get("config.yml").getDouble("spawn.radius", 100.0);
        org.bukkit.Location center = new org.bukkit.Location(loc.getWorld(), configs.get("config.yml").getDouble("spawn.location.x", 0.5), configs.get("config.yml").getDouble("spawn.location.y", loc.getY()), configs.get("config.yml").getDouble("spawn.location.z", 0.5));
        return loc.distanceSquared(center) <= radius * radius;
    }

    public boolean isSpawnWorld(String worldName) {
        return worldName.equalsIgnoreCase(Bukkit.getWorlds().get(0).getName()) || worldName.equalsIgnoreCase("spawn");
    }
}
