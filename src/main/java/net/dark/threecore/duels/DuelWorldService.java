package net.dark.threecore.duels;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.model.DuelMap;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
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

    public boolean enabled() { return configs.get("duels/duels.yml").getBoolean("duels.world-instances.enabled", true); }

    public World createEditorWorld(String mapId, World sourceWorld) {
        String name = "arena_" + mapId.toLowerCase(java.util.Locale.ROOT) + "_edit";
        Path to = Bukkit.getWorldContainer().toPath().resolve(name);
        try {
            if (Bukkit.getWorld(name) != null) return Bukkit.getWorld(name);
            boolean copySource = configs.get("duels/duels.yml").getBoolean("duels.editor-worlds.copy-source", false);
            if (copySource && sourceWorld != null && !Files.exists(to)) copyWorld(sourceWorld.getWorldFolder().toPath(), to);
            WorldCreator creator = new WorldCreator(name);
            String generator = configs.get("duels/duels.yml").getString("duels.editor-worlds.generator", "VoidGen");
            if (generator != null && !generator.isBlank()) creator.generator(generator);
            World world = Bukkit.createWorld(creator);
            if (world != null) {
                world.setAutoSave(configs.get("duels/duels.yml").getBoolean("duels.editor-worlds.auto-save", true));
                applyWorldRules(world, "duels.editor-worlds");
                registerWithMultiverse(world);
            }
            return world;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create duel editor world: " + ex.getMessage());
            return null;
        }
    }

    public World publishArena(String mapId, World editorWorld) {
        if (editorWorld == null) return null;
        String name = "arena_" + mapId.toLowerCase(java.util.Locale.ROOT) + "_live";
        Path to = Bukkit.getWorldContainer().toPath().resolve(name);
        try {
            World existing = Bukkit.getWorld(name);
            if (existing != null) {
                unregisterFromMultiverse(existing.getName());
                Bukkit.unloadWorld(existing, true);
            }
            delete(to);
            editorWorld.save();
            copyWorld(editorWorld.getWorldFolder().toPath(), to);
            World world = Bukkit.createWorld(new WorldCreator(name));
            if (world != null) {
                world.setAutoSave(false);
                applyWorldRules(world, "duels.world-instances");
                registerWithMultiverse(world);
            }
            return world;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to publish duel arena: " + ex.getMessage());
            return null;
        }
    }
    public void deleteEditorWorld(String mapId) {
        String name = "arena_" + mapId.toLowerCase(java.util.Locale.ROOT) + "_edit";
        World world = Bukkit.getWorld(name);
        if (world != null) {
            unregisterFromMultiverse(world.getName());
            Bukkit.unloadWorld(world, true);
        }
        delete(Bukkit.getWorldContainer().toPath().resolve(name));
    }

    public InstancedMap create(DuelMap source, UUID matchId) {
        if (!enabled() || source == null) return new InstancedMap(source, null);
        World template = Bukkit.getWorld(source.worldName());
        if (template == null) return new InstancedMap(source, null);
        String instanceName = configs.get("duels/duels.yml").getString("duels.world-instances.prefix", "arena_") + source.id() + "_match_" + matchId.toString().substring(0, 8);
        Path from = template.getWorldFolder().toPath();
        Path to = Bukkit.getWorldContainer().toPath().resolve(instanceName);
        try {
            copyWorld(from, to);
            World world = Bukkit.createWorld(new WorldCreator(instanceName));
            if (world == null) return new InstancedMap(source, null);
            world.setAutoSave(false);
            applyWorldRules(world, "duels.world-instances");
            registerWithMultiverse(world);
            return new InstancedMap(new DuelMap(source.id(), source.displayName(), source.enabled(), instanceName, translate(source.lobby(), world), translate(source.spawnA(), world), translate(source.spawnB(), world), translate(source.spectator(), world)), world);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create duel world instance: " + ex.getMessage());
            return new InstancedMap(source, null);
        }
    }

    public void cleanup(World world) {
        if (world == null || !enabled()) return;
        String name = world.getName();
        unregisterFromMultiverse(name);
        Bukkit.unloadWorld(world, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> delete(Bukkit.getWorldContainer().toPath().resolve(name)));
    }

    private void applyWorldRules(World world, String path) {
        if (world == null) return;
        if (configs.get("duels/duels.yml").getBoolean(path + ".disable-mob-spawning", true)) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
            world.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
        }
        if (configs.get("duels/duels.yml").getBoolean(path + ".disable-weather", true)) {
            world.setStorm(false);
            world.setThundering(false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        }
    }
    private void registerWithMultiverse(World world) {
        if (world == null || Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) return;
        if (!configs.get("duels/duels.yml").getBoolean("duels.world-instances.multiverse.register-worlds", true)) return;
        String env = switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "normal";
        };
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + world.getName() + " " + env);
                if (configs.get("duels/duels.yml").getBoolean("duels.world-instances.multiverse.hide-worlds", true)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set hidden true");
                }
                boolean pvp = configs.get("duels/duels.yml").getBoolean("duels.world-instances.multiverse.pvp", true);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set pvp " + pvp);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to register duel world with Multiverse: " + ex.getMessage());
            }
        });
    }

    private void unregisterFromMultiverse(String worldName) {
        if (worldName == null || Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) return;
        if (!configs.get("duels/duels.yml").getBoolean("duels.world-instances.multiverse.register-worlds", true)) return;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv remove " + worldName);
        } catch (Exception ex) {
            plugin.getLogger().fine("Failed to unregister duel world from Multiverse: " + ex.getMessage());
        }
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

