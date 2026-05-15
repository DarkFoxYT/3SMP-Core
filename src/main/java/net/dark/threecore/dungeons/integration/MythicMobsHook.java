package net.dark.threecore.dungeons.integration;

import net.dark.threecore.dungeons.engine.DungeonMobSpawn;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MythicMobsHook {
    private final JavaPlugin plugin;

    public MythicMobsHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return Bukkit.getPluginManager().getPlugin("MythicMobs") != null
            || Bukkit.getPluginManager().getPlugin("MythicBukkit") != null
            || Bukkit.getPluginManager().getPlugin("Mythic") != null;
    }

    public boolean isEnabled() {
        org.bukkit.plugin.Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythic == null) mythic = Bukkit.getPluginManager().getPlugin("MythicBukkit");
        if (mythic == null) mythic = Bukkit.getPluginManager().getPlugin("Mythic");
        return mythic != null && mythic.isEnabled();
    }

    public boolean hasMob(String id) {
        return isEnabled() && id != null && !id.isBlank();
    }

    public Entity spawnMob(String id, Location location) {
        if (location == null || location.getWorld() == null || !hasMob(id)) return null;
        try {
            Entity apiSpawned = spawnMobApi(id, location);
            if (apiSpawned != null) return apiSpawned;
            String coordinate = location.getWorld().getName() + "," + location.getX() + "," + location.getY() + "," + location.getZ();
            String normalized = id.trim();
            String noLevel = normalized.contains(":") ? normalized.substring(0, normalized.indexOf(':')) : normalized;
            String[] candidates = normalized.equals(noLevel) ? new String[]{normalized} : new String[]{normalized, noLevel};
            for (String mobId : candidates) {
                Set<UUID> before = nearbyEntityIds(location);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm mobs spawn " + mobId + " 1 " + coordinate);
                Entity found = nearestSpawned(location, before);
                if (found != null) return found;
                before = nearbyEntityIds(location);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mythicmobs mobs spawn " + mobId + " 1 " + coordinate);
                found = nearestSpawned(location, before);
                if (found != null) return found;
            }
            return null;
        } catch (Throwable ex) {
            plugin.getLogger().fine("MythicMobs boulder spawn failed for " + id + ": " + ex.getMessage());
            return null;
        }
    }

    private Entity spawnMobApi(String id, Location location) {
        if (!isEnabled()) return null;
        String normalized = id == null ? "" : id.trim();
        if (normalized.isBlank()) return null;
        String mobId = normalized;
        double level = 1.0D;
        int levelSeparator = normalized.indexOf(':');
        if (levelSeparator > 0 && levelSeparator < normalized.length() - 1) {
            mobId = normalized.substring(0, levelSeparator);
            try {
                level = Math.max(1.0D, Double.parseDouble(normalized.substring(levelSeparator + 1)));
            } catch (NumberFormatException ignored) {
                level = 1.0D;
            }
        }
        try {
            io.lumine.mythic.core.mobs.ActiveMob activeMob = MythicBukkit.inst().getMobManager().spawnMob(mobId, location, level);
            if (activeMob == null || activeMob.getEntity() == null) return null;
            Entity entity = io.lumine.mythic.bukkit.BukkitAdapter.adapt(activeMob.getEntity());
            if (entity != null) entity.teleport(location);
            return entity;
        } catch (Throwable ex) {
            plugin.getLogger().fine("MythicMobs API spawn failed for " + normalized + ": " + ex.getMessage());
            return null;
        }
    }

    private Set<UUID> nearbyEntityIds(Location location) {
        return location.getWorld().getNearbyEntities(location, 8.0D, 8.0D, 8.0D).stream()
                .map(Entity::getUniqueId)
                .collect(Collectors.toSet());
    }

    private Entity nearestSpawned(Location location, Set<UUID> before) {
        return location.getWorld().getNearbyEntities(location, 4.0D, 4.0D, 4.0D).stream()
                .filter(entity -> !(entity instanceof org.bukkit.entity.Player))
                .filter(entity -> before == null || !before.contains(entity.getUniqueId()))
                .min(java.util.Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(location)))
                .orElse(null);
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
