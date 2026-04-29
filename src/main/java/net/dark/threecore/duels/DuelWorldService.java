package net.dark.threecore.duels;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.model.DuelMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DuelWorldService {
    private static final Set<String> SKIP = Set.of("uid.dat", "session.lock");
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public DuelWorldService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public boolean enabled() { return configs.get("duels/duels.yml").getBoolean("duels.world-instances.enabled", true); }

    public void cleanupStaleMatchWorlds() {
        String prefix = configs.get("duels/duels.yml").getString("duels.world-instances.prefix", "arena_");
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().startsWith(prefix) || !world.getName().contains("_match_")) continue;
            cleanup(world);
        }
        try {
            Path container = Bukkit.getWorldContainer().toPath();
            if (!Files.exists(container)) return;
            try (var paths = Files.list(container)) {
                paths.filter(Files::isDirectory)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith(prefix) && name.contains("_match_");
                        })
                        .forEach(this::delete);
            }
        } catch (IOException ignored) {
        }
    }

    public World createEditorWorld(String mapId, World sourceWorld) {
        String name = arenaEditorName(mapId);
        Path to = Bukkit.getWorldContainer().toPath().resolve(name);
        try {
            if (Bukkit.getWorld(name) != null) return Bukkit.getWorld(name);
            boolean copySource = configs.get("duels/duels.yml").getBoolean("duels.editor-worlds.copy-source", false);
            if (copySource && sourceWorld != null && !Files.exists(to)) copyWorld(sourceWorld.getWorldFolder().toPath(), to);
            WorldCreator creator = new WorldCreator(name);
            creator.type(org.bukkit.WorldType.FLAT);
            creator.generateStructures(false);
            String generator = configs.get("duels/duels.yml").getString("duels.editor-worlds.generator", "VoidGen");
            if (generator != null && !generator.isBlank()) creator.generator(generator);
            World world = Bukkit.createWorld(creator);
            if (world != null) {
                world.setAutoSave(configs.get("duels/duels.yml").getBoolean("duels.editor-worlds.auto-save", true));
                applyWorldRules(world, "duels.editor-worlds");
                world.setSpawnLocation(0, 64, 0);
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
        String name = arenaLiveName(mapId);
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
            WorldCreator creator = new WorldCreator(name);
            creator.type(org.bukkit.WorldType.FLAT);
            creator.generator(configs.get("duels/duels.yml").getString("duels.world-instances.generator", "VoidGen"));
            creator.generateStructures(false);
            World world = Bukkit.createWorld(creator);
            if (world != null) {
                world.setAutoSave(false);
                applyWorldRules(world, "duels.world-instances");
                world.setSpawnLocation(0, 64, 0);
                registerWithMultiverse(world);
            }
            return world;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to publish duel arena: " + ex.getMessage());
            return null;
        }
    }
    public void deleteEditorWorld(String mapId) {
        String name = arenaEditorName(mapId);
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
            template.save();
            World existing = Bukkit.getWorld(instanceName);
            if (existing != null) {
                unregisterFromMultiverse(existing.getName());
                Bukkit.unloadWorld(existing, false);
            }
            delete(to);
            copyWorld(from, to, true);
            WorldCreator creator = matchWorldCreator(instanceName, template);
            World world = Bukkit.createWorld(creator);
            if (world == null) return new InstancedMap(source, null);
            prepareMatchWorld(world, "duels.world-instances");
            registerWithMultiverse(world);
            DuelMap map = new DuelMap(source.id(), source.displayName(), source.enabled(), instanceName, translate(source.lobby(), world), translate(source.spawnA(), world), translate(source.spawnB(), world), translate(source.spectator(), world));
            preloadImportantChunks(world, map);
            return new InstancedMap(map, world);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create duel world instance: " + ex.getMessage());
            return new InstancedMap(source, null);
        }
    }

    public void createAsync(DuelMap source, UUID matchId, Consumer<InstancedMap> callback) {
        if (!enabled() || source == null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(new InstancedMap(source, null)));
            return;
        }
        World template = Bukkit.getWorld(source.worldName());
        if (template == null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(new InstancedMap(source, null)));
            return;
        }
        String instanceName = configs.get("duels/duels.yml").getString("duels.world-instances.prefix", "arena_") + source.id() + "_match_" + matchId.toString().substring(0, 8);
        Path from = template.getWorldFolder().toPath();
        Path to = Bukkit.getWorldContainer().toPath().resolve(instanceName);
        template.save();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                delete(to);
                copyWorld(from, to, true);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to copy duel world instance: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(new InstancedMap(source, null)));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> loadCopiedInstance(source, template, instanceName, callback));
        });
    }

    private void loadCopiedInstance(DuelMap source, World template, String instanceName, Consumer<InstancedMap> callback) {
        try {
            World existing = Bukkit.getWorld(instanceName);
            if (existing != null) {
                unregisterFromMultiverse(existing.getName());
                Bukkit.unloadWorld(existing, false);
            }
            WorldCreator creator = matchWorldCreator(instanceName, template);
            World world = Bukkit.createWorld(creator);
            if (world == null) {
                callback.accept(new InstancedMap(source, null));
                return;
            }
            prepareMatchWorld(world, "duels.world-instances");
            registerWithMultiverse(world);
            DuelMap map = new DuelMap(source.id(), source.displayName(), source.enabled(), instanceName, translate(source.lobby(), world), translate(source.spawnA(), world), translate(source.spawnB(), world), translate(source.spectator(), world));
            preloadImportantChunks(world, map);
            callback.accept(new InstancedMap(map, world));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load duel world instance: " + ex.getMessage());
            callback.accept(new InstancedMap(source, null));
        }
    }

    public void cleanup(World world) {
        if (world == null || !enabled()) return;
        String name = world.getName();
        for (Chunk chunk : world.getLoadedChunks()) {
            try { chunk.removePluginChunkTicket(plugin); } catch (NoSuchMethodError ignored) {}
        }
        unregisterFromMultiverse(name);
        Bukkit.unloadWorld(world, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> delete(Bukkit.getWorldContainer().toPath().resolve(name)));
    }

    private void applyWorldRules(World world, String path) {
        if (world == null) return;
        world.setTime(1000L);
        world.setStorm(false);
        world.setThundering(false);
        if (configs.get("duels/duels.yml").getBoolean(path + ".disable-mob-spawning", true)) {
            setGameRule(world, "doMobSpawning", false);
            setGameRule(world, "doPatrolSpawning", false);
            setGameRule(world, "doTraderSpawning", false);
            setGameRule(world, "doWardenSpawning", false);
        }
        if (configs.get("duels/duels.yml").getBoolean(path + ".disable-weather", true)) {
            setGameRule(world, "doWeatherCycle", false);
        }
        if (configs.get("duels/duels.yml").getBoolean(path + ".force-daylight", true)) {
            setGameRule(world, "doDaylightCycle", false);
        }
    }

    private void prepareMatchWorld(World world, String path) {
        if (world == null) return;
        world.setAutoSave(false);
        try { world.setKeepSpawnInMemory(configBoolean(path + ".keep-spawn-loaded-during-match", true)); } catch (NoSuchMethodError ignored) {}
        applyWorldRules(world, path);
        world.setSpawnLocation(0, 64, 0);
        world.setPVP(true);
    }

    private void preloadImportantChunks(World world, DuelMap map) {
        if (world == null || map == null) return;
        int radius = Math.max(0, configInt("duels.world-instances.preload-chunk-radius", 3));
        loadAround(world, map.spawnA(), radius);
        loadAround(world, map.spawnB(), radius);
        loadAround(world, map.lobby(), Math.min(radius, 1));
        loadAround(world, map.spectator(), Math.min(radius, 2));
    }

    private void loadAround(World world, Location location, int radius) {
        if (location == null) return;
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Chunk chunk = world.getChunkAt(x, z);
                if (!chunk.isLoaded()) chunk.load(true);
                try { chunk.addPluginChunkTicket(plugin); } catch (NoSuchMethodError ignored) {}
            }
        }
    }

    public void keepPlayerAreaLoaded(Player player) {
        if (player == null || player.getWorld() == null || !isTemporaryDuelWorld(player.getWorld().getName())) return;
        int radius = Math.max(1, configInt("duels.world-instances.player-chunk-ticket-radius", 4));
        loadAround(player.getWorld(), player.getLocation(), radius);
    }

    private void preloadRegionChunks(World world, Path worldFolder) {
        for (ChunkCoordinate coordinate : regionChunkCoordinates(worldFolder)) {
            loadChunk(world, coordinate.x(), coordinate.z());
        }
    }

    private void preloadRegionChunksBatched(World world, Path worldFolder, Runnable done) {
        List<ChunkCoordinate> chunks = regionChunkCoordinates(worldFolder);
        if (chunks.isEmpty()) {
            done.run();
            return;
        }
        int perTick = Math.max(1, configInt("duels.world-instances.preload-chunks-per-tick", 96));
        new org.bukkit.scheduler.BukkitRunnable() {
            int index = 0;
            @Override public void run() {
                if (world == null || Bukkit.getWorld(world.getName()) == null) {
                    cancel();
                    done.run();
                    return;
                }
                int end = Math.min(chunks.size(), index + perTick);
                while (index < end) {
                    ChunkCoordinate coordinate = chunks.get(index++);
                    loadChunk(world, coordinate.x(), coordinate.z());
                }
                if (index >= chunks.size()) {
                    cancel();
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private List<ChunkCoordinate> regionChunkCoordinates(Path worldFolder) {
        List<ChunkCoordinate> chunks = new ArrayList<>();
        if (!configBoolean("duels.world-instances.preload-all-region-chunks", true)) return chunks;
        Path region = worldFolder.resolve("region");
        if (!Files.isDirectory(region)) return chunks;
        int max = Math.max(1, configInt("duels.world-instances.max-preload-region-chunks", 100000));
        try (var paths = Files.list(region)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                if (chunks.size() >= max) return;
                Matcher matcher = REGION_FILE.matcher(path.getFileName().toString());
                if (!matcher.matches()) return;
                int regionX = Integer.parseInt(matcher.group(1));
                int regionZ = Integer.parseInt(matcher.group(2));
                for (int localX = 0; localX < 32 && chunks.size() < max; localX++) {
                    for (int localZ = 0; localZ < 32 && chunks.size() < max; localZ++) {
                        chunks.add(new ChunkCoordinate(regionX * 32 + localX, regionZ * 32 + localZ));
                    }
                }
            });
            if (chunks.size() >= max) {
                plugin.getLogger().warning("Duel arena preload hit max-preload-region-chunks=" + max + " for " + worldFolder.getFileName() + ". Increase it if this arena is larger.");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to scan duel arena region chunks: " + ex.getMessage());
        }
        return chunks;
    }

    private void loadChunk(World world, int x, int z) {
        Chunk chunk = world.getChunkAt(x, z);
        if (!chunk.isLoaded()) chunk.load(true);
        try { chunk.addPluginChunkTicket(plugin); } catch (NoSuchMethodError ignored) {}
    }

    private WorldCreator matchWorldCreator(String instanceName, World template) {
        WorldCreator creator = new WorldCreator(instanceName);
        if (template != null) {
            creator.environment(template.getEnvironment());
            creator.type(template.getWorldType());
        } else {
            creator.type(org.bukkit.WorldType.FLAT);
        }
        String generator = configs.get("duels/duels.yml").getString("duels.world-instances.generator", "");
        if (generator != null && !generator.isBlank()) creator.generator(generator);
        creator.generateStructures(false);
        return creator;
    }

    private boolean configBoolean(String path, boolean fallback) {
        var yaml = configs.get("duels/duels.yml");
        if (yaml.contains(path)) return yaml.getBoolean(path, fallback);
        String nested = path.replace("duels.world-instances.", "duels.world-instances.multiverse.");
        return yaml.getBoolean(nested, fallback);
    }

    private int configInt(String path, int fallback) {
        var yaml = configs.get("duels/duels.yml");
        if (yaml.contains(path)) return yaml.getInt(path, fallback);
        String nested = path.replace("duels.world-instances.", "duels.world-instances.multiverse.");
        return yaml.getInt(nested, fallback);
    }
    private <T> void setGameRule(World world, String ruleName, T value) {
        @SuppressWarnings("unchecked")
        GameRule<T> rule = (GameRule<T>) org.bukkit.Registry.GAME_RULE.get(org.bukkit.NamespacedKey.minecraft(toSnakeCase(ruleName)));
        if (rule != null) world.setGameRule(rule, value);
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }
    private void registerWithMultiverse(World world) {
        if (world == null || !shouldUseMultiverse(world.getName())) return;
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
                logMultiverseFailure("Failed to register duel world with Multiverse: " + ex.getMessage());
            }
        });
    }

    private String arenaLiveName(String mapId) {
        String normalized = mapId.toLowerCase(java.util.Locale.ROOT);
        String candidate = normalized + "_arena_1";
        if (!exists(candidate)) return candidate;
        int index = 2;
        while (exists(normalized + "_arena_" + index)) index++;
        return normalized + "_arena_" + index;
    }

    private String arenaEditorName(String mapId) {
        return mapId.toLowerCase(java.util.Locale.ROOT) + "_arena_edit";
    }

    private void unregisterFromMultiverse(String worldName) {
        if (worldName == null || !shouldUseMultiverse(worldName)) return;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv remove " + worldName);
        } catch (Exception ex) {
            logMultiverseFailure("Failed to unregister duel world from Multiverse: " + ex.getMessage());
        }
    }

    private boolean shouldUseMultiverse(String worldName) {
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) return false;
        if (!configs.get("duels/duels.yml").getBoolean("duels.world-instances.multiverse.register-worlds", true)) return false;
        if (isTemporaryDuelWorld(worldName)) {
            return configs.get("duels/duels.yml").getBoolean("duels.world-instances.multiverse.register-temporary-worlds", false);
        }
        return true;
    }

    private boolean isTemporaryDuelWorld(String worldName) {
        return worldName != null && worldName.contains("_match_");
    }

    private void logMultiverseFailure(String message) {
        if (configs.get("duels/duels.yml").getBoolean("duels.world-instances.multiverse.quiet-failures", true)) {
            plugin.getLogger().fine(message);
        } else {
            plugin.getLogger().warning(message);
        }
    }

    private Location translate(Location source, World world) {
        if (source == null) return null;
        return new Location(world, source.getX(), source.getY(), source.getZ(), source.getYaw(), source.getPitch());
    }

    private void copyWorld(Path from, Path to) throws IOException {
        copyWorld(from, to, false);
    }

    private void copyWorld(Path from, Path to, boolean fullCopy) throws IOException {
        Files.walk(from).forEach(path -> {
            try {
                Path relative = from.relativize(path);
                if (shouldSkipCopy(relative, fullCopy)) return;
                Path target = to.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException ex) { throw new RuntimeException(ex); }
        });
    }

    private boolean shouldSkipCopy(Path relative, boolean fullCopy) {
        if (relative.getNameCount() == 0) return false;
        String fileName = relative.getFileName().toString();
        if (SKIP.contains(fileName)) return true;
        if (fullCopy) return false;
        String first = relative.getName(0).toString();
        if (configs.get("duels/duels.yml").getBoolean("duels.world-instances.fast-copy.skip-player-data", true)
                && Set.of("playerdata", "stats", "advancements").contains(first)) return true;
        if (configs.get("duels/duels.yml").getBoolean("duels.world-instances.fast-copy.skip-poi", true)
                && first.equals("poi")) return true;
        if (configs.get("duels/duels.yml").getBoolean("duels.world-instances.fast-copy.skip-entities", true)
                && first.equals("entities")) return true;
        if (configs.get("duels/duels.yml").getBoolean("duels.world-instances.fast-copy.skip-plugin-data", true)
                && first.equals("data")) return true;
        return false;
    }

    private void delete(Path path) {
        if (!Files.exists(path)) return;
        try {
            Files.walk(path).sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private boolean exists(String worldName) {
        if (Bukkit.getWorld(worldName) != null) return true;
        return Files.exists(Bukkit.getWorldContainer().toPath().resolve(worldName));
    }

    public record InstancedMap(DuelMap map, World world) {}
    private record ChunkCoordinate(int x, int z) {}
}

