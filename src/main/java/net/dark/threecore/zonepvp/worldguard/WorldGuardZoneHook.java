package net.dark.threecore.zonepvp.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionType;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class WorldGuardZoneHook {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public WorldGuardZoneHook(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public boolean available() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null && Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    }

    public void sync(Location first, Location second) {
        if (!configs.get("world/zonepvp.yml").getBoolean("worldguard.enabled", true)) return;
        if (!available() || first == null || second == null || first.getWorld() == null || second.getWorld() == null) return;
        if (!first.getWorld().equals(second.getWorld())) return;
        try {
            World world = first.getWorld();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) return;
            String id = configs.get("world/zonepvp.yml").getString("worldguard.region-id", "3smp_zonepvp").toLowerCase(java.util.Locale.ROOT);
            BlockVector3 min = BlockVector3.at(Math.min(first.getBlockX(), second.getBlockX()), Math.min(first.getBlockY(), second.getBlockY()), Math.min(first.getBlockZ(), second.getBlockZ()));
            BlockVector3 max = BlockVector3.at(Math.max(first.getBlockX(), second.getBlockX()), Math.max(first.getBlockY(), second.getBlockY()), Math.max(first.getBlockZ(), second.getBlockZ()));
            ProtectedRegion region = manager.getRegion(id);
            if (region == null || region.getType() != RegionType.CUBOID) {
                region = new ProtectedCuboidRegion(id, min, max);
                manager.addRegion(region);
            } else if (region instanceof ProtectedCuboidRegion cuboid) {
                cuboid.setMinimumPoint(min);
                cuboid.setMaximumPoint(max);
            }
            region.setPriority(configs.get("world/zonepvp.yml").getInt("worldguard.priority", 50));
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);
            region.setFlag(Flags.DAMAGE_ANIMALS, StateFlag.State.ALLOW);
            if (configs.get("world/zonepvp.yml").getBoolean("worldguard.deny-natural-mob-spawning", true)) region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
            if (configs.get("world/zonepvp.yml").getBoolean("worldguard.passthrough", false)) region.setFlag(Flags.PASSTHROUGH, StateFlag.State.ALLOW);
            manager.save();
            plugin.getLogger().info("Synced WorldGuard ZonePvP region '" + id + "' in world " + world.getName());
        } catch (Throwable ex) {
            plugin.getLogger().warning("Failed to sync WorldGuard ZonePvP region: " + ex.getMessage());
        }
    }

    public boolean allowsPvp(Player player) {
        if (!configs.get("world/zonepvp.yml").getBoolean("worldguard.respect-region-query", true)) return true;
        if (!available() || player == null) return true;
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));
            StateFlag.State state = set.queryState(null, Flags.PVP);
            return state != StateFlag.State.DENY;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
