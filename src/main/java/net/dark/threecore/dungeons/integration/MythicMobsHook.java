package net.dark.threecore.dungeons.integration;

import net.dark.threecore.dungeons.engine.DungeonMobSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MythicMobsHook {
    private final JavaPlugin plugin;

    public MythicMobsHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    public boolean isEnabled() {
        org.bukkit.plugin.Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        return mythic != null && mythic.isEnabled();
    }

    public boolean hasMob(String id) {
        return isEnabled() && id != null && !id.isBlank();
    }

    public Entity spawnMob(String id, Location location) {
        if (location == null || location.getWorld() == null || !hasMob(id)) return null;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm mobs spawn " + id + " 1 " + location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ());
            return location.getWorld().getNearbyEntities(location, 2.0D, 2.0D, 2.0D).stream()
                    .filter(entity -> !(entity instanceof org.bukkit.entity.Player))
                    .min(java.util.Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(location)))
                    .orElse(null);
        } catch (Throwable ex) {
            plugin.getLogger().fine("MythicMobs boulder spawn failed for " + id + ": " + ex.getMessage());
            return null;
        }
    }

    public void removeMob(Entity entity) {
        if (entity != null && entity.isValid()) entity.remove();
    }

    public void setNoAI(Entity entity) {
        if (entity instanceof org.bukkit.entity.Mob mob) mob.setAI(false);
        if (entity instanceof org.bukkit.entity.ArmorStand stand) {
            stand.setGravity(false);
            stand.setSilent(true);
        }
    }

    public void spawn(DungeonMobSpawn spawn, Location location) {
        if (location == null || location.getWorld() == null) return;
        if (available()) {
            for (int i = 0; i < spawn.amount(); i++) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm mobs spawn " + spawn.id() + ":" + spawn.level() + " 1 " + location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ());
            }
            return;
        }
        for (int i = 0; i < spawn.amount(); i++) {
            Entity entity = location.getWorld().spawnEntity(location, spawn.fallback(), CreatureSpawnEvent.SpawnReason.CUSTOM);
            entity.setPersistent(false);
        }
        plugin.getLogger().fine("MythicMobs missing; spawned vanilla fallback for " + spawn.id());
    }
}
