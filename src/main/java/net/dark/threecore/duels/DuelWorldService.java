package net.dark.threecore.duels;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.model.DuelMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

public final class DuelWorldService {
    private static final Set<String> SKIP = Set.of("uid.dat", "session.lock");
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public DuelWorldService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public boolean enabled() { return configs.get("duels.yml").getBoolean("duels.world-instances.enabled", false); }

    public World createEditorWorld(String mapId, World sourceWorld) {
        if (sourceWorld == null) return null;
        String name = "arena_" + mapId.toLowerCase(java.util.Locale.ROOT) + "_edit";
        Path to = Bukkit.getWorldContainer().toPath().resolve(name);
        try {
            if (Bukkit.getWorld(name) != null) return Bukkit.getWorld(name);
            if (!Files.exists(to)) copyWorld(sourceWorld.getWorldFolder().toPath(), to);
            World world = Bukkit.createWorld(new WorldCreator(name));
            if (world != null) world.setAutoSave(true);
            return world;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create duel editor world: " + ex.getMessage());
            return null;
        }
    }

    public void deleteEditorWorld(String mapId) {
        String name = "arena_" + mapId.toLowerCase(java.util.Locale.ROOT) + "_edit";
        World world = Bukkit.getWorld(name);
        if (world != null) Bukkit.unloadWorld(world, true);
        delete(Bukkit.getWorldContainer().toPath().resolve(name));
    }

    public InstancedMap create(DuelMap source, UUID matchId) {
        if (!enabled() || source == null) return new InstancedMap(source, null);
        World template = Bukkit.getWorld(source.worldName());
        if (template == null) return new InstancedMap(source, null);
        String instanceName = configs.get("duels.yml").getString("duels.world-instances.prefix", "arena_") + source.id() + "_match_" + matchId.toString().substring(0, 8);
        Path from = template.getWorldFolder().toPath();
        Path to = Bukkit.getWorldContainer().toPath().resolve(instanceName);
        try {
            copyWorld(from, to);
            World world = Bukkit.createWorld(new WorldCreator(instanceName));
            if (world == null) return new InstancedMap(source, null);
            world.setAutoSave(false);
            return new InstancedMap(new DuelMap(source.id(), source.displayName(), source.enabled(), instanceName, translate(source.lobby(), world), translate(source.spawnA(), world), translate(source.spawnB(), world), translate(source.spectator(), world)), world);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create duel world instance: " + ex.getMessage());
            return new InstancedMap(source, null);
        }
    }

    public void cleanup(World world) {
        if (world == null || !enabled()) return;
        String name = world.getName();
        Bukkit.unloadWorld(world, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> delete(Bukkit.getWorldContainer().toPath().resolve(name)));
    }

    private Location translate(Location source, World world) {
        if (source == null) return null;
        return new Location(world, source.getX(), source.getY(), source.getZ(), source.getYaw(), source.getPitch());
    }

    private void copyWorld(Path from, Path to) throws IOException {
        Files.walk(from).forEach(path -> {
            try {
                Path relative = from.relativize(path);
                if (relative.getNameCount() > 0 && SKIP.contains(relative.getFileName().toString())) return;
                Path target = to.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException ex) { throw new RuntimeException(ex); }
        });
    }

    private void delete(Path path) {
        if (!Files.exists(path)) return;
        try {
            Files.walk(path).sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    public record InstancedMap(DuelMap map, World world) {}
}
