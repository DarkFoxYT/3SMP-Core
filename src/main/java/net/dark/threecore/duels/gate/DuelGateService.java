package net.dark.threecore.duels.gate;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.event.DuelGateFullyOpenedEvent;
import net.dark.threecore.duels.event.DuelGateOpenEvent;
import net.dark.threecore.duels.event.DuelGateResetEvent;
import net.dark.threecore.duels.model.DuelGateRegion;
import net.dark.threecore.duels.model.DuelMap;
import net.dark.threecore.duels.model.DuelMatch;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DuelGateService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, DuelGateInstance> instances = new HashMap<>();

    public DuelGateService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public boolean enabled() {
        return configs.get("duels/duels.yml").getBoolean("duels.gates.enabled", true);
    }

    private boolean required() {
        return configs.get("duels/duels.yml").getBoolean("duels.gates.required", false);
    }

    public List<String> validate(DuelMap arena) {
        List<String> failures = new ArrayList<>();
        if (!enabled()) return failures;
        if (arena == null) {
            failures.add("missing arena");
            return failures;
        }
        if (arena.spawnA() == null) failures.add("missing red spawn");
        if (arena.spawnB() == null) failures.add("missing blue spawn");
        if (required() || arena.redGate() != null) validateGate(arena.redGate(), arena.worldName(), arena.spawnA(), arena.spawnB(), "red", failures);
        if (required() || arena.blueGate() != null) validateGate(arena.blueGate(), arena.worldName(), arena.spawnB(), arena.spawnA(), "blue", failures);
        if (!required() && (arena.redGate() == null) != (arena.blueGate() == null)) failures.add("only one gate is configured; set both gates or clear both");
        if (arena.redGate() != null && arena.blueGate() != null && arena.redGate().worldName().equalsIgnoreCase(arena.blueGate().worldName()) && arena.redGate().box().overlaps(arena.blueGate().box())) {
            failures.add("red and blue gates overlap");
        }
        return failures;
    }

    private void validateGate(DuelGateRegion gate, String arenaWorld, Location ownSpawn, Location otherSpawn, String id, List<String> failures) {
        if (gate == null) {
            failures.add("missing " + id + " gate region");
            return;
        }
        if (arenaWorld != null && !gate.worldName().equalsIgnoreCase(arenaWorld)) failures.add(id + " gate is in the wrong world");
        World world = Bukkit.getWorld(gate.worldName());
        if (world == null) {
            failures.add(id + " gate world is not loaded");
            return;
        }
        if (ownSpawn != null && gate.contains(ownSpawn)) failures.add(id + " gate overlaps its spawn");
        if (otherSpawn != null && gate.contains(otherSpawn)) failures.add(id + " gate overlaps the other spawn");
        int solid = 0;
        for (int x = gate.minX(); x <= gate.maxX(); x++) {
            for (int y = gate.minY(); y <= gate.maxY(); y++) {
                for (int z = gate.minZ(); z <= gate.maxZ(); z++) {
                    if (world.getBlockAt(x, y, z).getType().isSolid()) solid++;
                }
            }
        }
        if (solid <= 0) failures.add(id + " gate has no solid blocks");
    }

    public void closeGates(DuelMatch match, DuelMap arena) {
        if (!enabled() || match == null || arena == null) return;
        if (arena.redGate() == null && arena.blueGate() == null) return;
        resetGates(match);
        DuelGateInstance instance = new DuelGateInstance(match.id(), arena);
        capture(instance, "red", arena.redGate());
        capture(instance, "blue", arena.blueGate());
        if (instance.blocks.isEmpty()) return;
        instances.put(match.id(), instance);
    }

    public void openGates(DuelMatch match) {
        if (!enabled() || match == null) return;
        DuelGateInstance instance = instances.get(match.id());
        if (instance == null || instance.opening) return;
        Bukkit.getPluginManager().callEvent(new DuelGateOpenEvent(match));
        instance.opening = true;
        int delay = Math.max(0, configs.get("duels/duels.yml").getInt("duels.gates.countdown-open-delay-ticks", 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> startOpening(match, instance), delay);
    }

    public boolean enforceCollision(Player player, Location target) {
        if (player == null || target == null || player.getWorld() == null) return false;
        for (DuelGateInstance instance : instances.values()) {
            if (!instance.collisionActive) continue;
            DuelGateRegion red = instance.arena.redGate();
            DuelGateRegion blue = instance.arena.blueGate();
            if (red != null && (red.contains(target) || regionContainsPlayer(red, player))) {
                return true;
            }
            if (blue != null && (blue.contains(target) || regionContainsPlayer(blue, player))) {
                return true;
            }
        }
        return false;
    }

    private void startOpening(DuelMatch match, DuelGateInstance instance) {
        int distance = Math.max(1, configs.get("duels/duels.yml").getInt("duels.gates.slide-distance-blocks", 5));
        int duration = Math.max(1, configs.get("duels/duels.yml").getInt("duels.gates.slide-duration-ticks", 50));
        int releaseDelay = Math.max(0, configs.get("duels/duels.yml").getInt("duels.gates.collision-release-delay-ticks", 5));
        int releaseTick = duration + releaseDelay;
        boolean autoClose = configs.get("duels/duels.yml").getBoolean("duels.gates.auto-close", true);
        boolean despawnAfterOpen = configs.get("duels/duels.yml").getBoolean("duels.gates.despawn-displays-after-open", false) && !autoClose;
        boolean particles = false;
        Sound start = sound("duels.gates.sound-start", Sound.BLOCK_PISTON_EXTEND);
        Sound loop = sound("duels.gates.sound-open-loop", sound("duels.gates.sound-loop", Sound.BLOCK_STONE_PLACE));
        Sound end = sound("duels.gates.sound-full-open", sound("duels.gates.sound-end", Sound.BLOCK_IRON_DOOR_OPEN));
        float volume = (float) configs.get("duels/duels.yml").getDouble("duels.gates.sound-volume", 1.0D);
        float pitch = (float) configs.get("duels/duels.yml").getDouble("duels.gates.sound-pitch", 0.75D);
        play(instance, start, volume, pitch);
        instance.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick = 0;
            boolean released = false;
            @Override
            public void run() {
                if (!instances.containsKey(match.id())) return;
                double progress = Math.min(1.0D, tick / (double) duration);
                double y = easeOut(progress) * distance;
                for (GateBlock block : instance.blocks) {
                    Location next = block.location.clone().add(0.0D, y, 0.0D);
                    block.display.teleport(next);
                    if (particles && tick % 4 == 0) block.location.getWorld().spawnParticle(Particle.DUST, next.clone().add(0.5, 0.5, 0.5), 1, new Particle.DustOptions(Color.fromRGB(250, 204, 21), 0.8F));
                }
                if (!released && tick >= releaseTick) {
                    released = true;
                    instance.collisionActive = false;
                }
                if (tick % Math.max(1, configs.get("duels/duels.yml").getInt("duels.gates.loop-sound-interval-ticks", 8)) == 0) play(instance, loop, volume * 0.35F, pitch);
                if (tick++ >= releaseTick) {
                    if (instance.task != null) instance.task.cancel();
                    if (despawnAfterOpen) removeDisplays(instance);
                    play(instance, end, volume, pitch);
                    instance.opening = false;
                    instance.open = true;
                    instance.openDistance = distance;
                    Bukkit.getPluginManager().callEvent(new DuelGateFullyOpenedEvent(match));
                    if (autoClose) {
                        int delay = Math.max(0, configs.get("duels/duels.yml").getInt("duels.gates.auto-close-delay-ticks", 100));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> startClosing(match, instance), delay);
                    }
                }
            }
        }, 0L, 1L);
    }

    private void startClosing(DuelMatch match, DuelGateInstance instance) {
        if (!instances.containsKey(match.id()) || instance.closing || instance.restored) return;
        instance.closing = true;
        instance.collisionActive = true;
        int duration = Math.max(1, configs.get("duels/duels.yml").getInt("duels.gates.close-slide-duration-ticks", configs.get("duels/duels.yml").getInt("duels.gates.slide-duration-ticks", 50)));
        boolean particles = false;
        Sound closeStart = sound("duels.gates.sound-close-start", Sound.BLOCK_PISTON_CONTRACT);
        Sound closeEnd = sound("duels.gates.sound-close-end", Sound.BLOCK_STONE_PLACE);
        Sound loop = sound("duels.gates.sound-close-loop", sound("duels.gates.sound-loop", Sound.BLOCK_STONE_PLACE));
        float volume = (float) configs.get("duels/duels.yml").getDouble("duels.gates.sound-volume", 1.0D);
        float pitch = (float) configs.get("duels/duels.yml").getDouble("duels.gates.sound-pitch", 0.75D);
        pushPlayersOutOfGateRegions(match, instance);
        Bukkit.getScheduler().runTaskLater(plugin, () -> pushPlayersOutOfGateRegions(match, instance), 1L);
        play(instance, closeStart, volume, pitch);
        instance.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!instances.containsKey(match.id())) return;
                pushPlayersOutOfGateRegions(match, instance);
                double progress = Math.min(1.0D, tick / (double) duration);
                double y = instance.openDistance * (1.0D - easeOut(progress));
                for (GateBlock block : instance.blocks) {
                    Location next = block.location.clone().add(0.0D, y, 0.0D);
                    if (block.display != null && block.display.isValid()) block.display.teleport(next);
                    if (particles && tick % 4 == 0) block.location.getWorld().spawnParticle(Particle.DUST, next.clone().add(0.5, 0.5, 0.5), 1, new Particle.DustOptions(Color.fromRGB(96, 165, 250), 0.8F));
                }
                if (tick % Math.max(1, configs.get("duels/duels.yml").getInt("duels.gates.loop-sound-interval-ticks", 8)) == 0) play(instance, loop, volume * 0.35F, pitch);
                if (tick++ >= duration) {
                    if (instance.task != null) instance.task.cancel();
                    restoreOriginalBlocks(instance);
                    removeDisplays(instance);
                    pushPlayersOutOfGateRegions(match, instance);
                    instance.restored = true;
                    instance.closing = false;
                    instance.open = false;
                    instance.collisionActive = false;
                    play(instance, closeEnd, volume, pitch);
                }
            }
        }, 0L, 1L);
    }

    public void resetGates(DuelMatch match) {
        if (match == null) return;
        DuelGateInstance instance = instances.remove(match.id());
        if (instance == null) return;
        reset(instance);
        Bukkit.getPluginManager().callEvent(new DuelGateResetEvent(match));
    }

    public void resetGates(DuelMap arena) {
        if (arena == null) return;
        for (Iterator<Map.Entry<UUID, DuelGateInstance>> iterator = instances.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, DuelGateInstance> entry = iterator.next();
            if (!entry.getValue().arena.id().equalsIgnoreCase(arena.id())) continue;
            reset(entry.getValue());
            iterator.remove();
        }
    }

    public void resetAll() {
        for (DuelGateInstance instance : new ArrayList<>(instances.values())) reset(instance);
        instances.clear();
    }

    public void previewGates(org.bukkit.entity.Player editor, DuelMap arena) {
        if (editor == null || arena == null) return;
        preview(editor, arena.redGate(), Color.fromRGB(248, 113, 113));
        preview(editor, arena.blueGate(), Color.fromRGB(96, 165, 250));
        preview(editor, arena.redGateCloseZone(), Color.fromRGB(255, 180, 180));
        preview(editor, arena.blueGateCloseZone(), Color.fromRGB(147, 197, 253));
    }

    private void preview(org.bukkit.entity.Player player, DuelGateRegion gate, Color color) {
        if (gate == null || player.getWorld() == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1F);
        for (int x = gate.minX(); x <= gate.maxX(); x++) {
            for (int y = gate.minY(); y <= gate.maxY(); y++) {
                for (int z = gate.minZ(); z <= gate.maxZ(); z++) {
                    player.spawnParticle(Particle.DUST, new Location(player.getWorld(), x + 0.5, y + 0.5, z + 0.5), 1, 0, 0, 0, dust);
                }
            }
        }
    }

    private void capture(DuelGateInstance instance, String team, DuelGateRegion region) {
        if (region == null) return;
        World world = Bukkit.getWorld(region.worldName());
        if (world == null) return;
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    BlockState state = block.getState();
                    BlockData data = block.getBlockData().clone();
                    Location location = block.getLocation();
                    BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                        entity.setBlock(data);
                        entity.setPersistent(false);
                        entity.setInterpolationDuration(1);
                        entity.setTeleportDuration(1);
                        if (configs.get("duels/duels.yml").getBoolean("duels.gates.prevent-dark-displays", true)) {
                            int blockLight = clampLight(configs.get("duels/duels.yml").getInt("duels.gates.display-brightness.block-light", 15));
                            int skyLight = clampLight(configs.get("duels/duels.yml").getInt("duels.gates.display-brightness.sky-light", 15));
                            entity.setBrightness(new Display.Brightness(blockLight, skyLight));
                        }
                    });
                    block.setType(Material.AIR, false);
                    instance.blocks.add(new GateBlock(team, location, state, data, display));
                }
            }
        }
    }

    private void clearCollision(DuelGateInstance instance) {
        for (GateBlock block : instance.blocks) {
            Block worldBlock = block.location.getBlock();
            if (worldBlock.getType() == Material.BARRIER) worldBlock.setType(Material.AIR, false);
        }
    }

    private void removeDisplays(DuelGateInstance instance) {
        for (GateBlock block : instance.blocks) {
            if (block.display != null && block.display.isValid()) block.display.remove();
        }
    }

    private void reset(DuelGateInstance instance) {
        if (instance.task != null) instance.task.cancel();
        restoreOriginalBlocks(instance);
        removeDisplays(instance);
        instance.blocks.clear();
    }

    private void restoreOriginalBlocks(DuelGateInstance instance) {
        for (GateBlock block : instance.blocks) {
            try {
                block.location.getBlock().setBlockData(block.data, false);
                block.state.update(true, false);
            } catch (Exception ignored) {
                block.location.getBlock().setBlockData(block.data, false);
            }
        }
    }

    private void pushPlayersOutOfGateRegions(DuelMatch match, DuelGateInstance instance) {
        if (!configs.get("duels/duels.yml").getBoolean("duels.gates.teleport-players-before-close", true)) return;
        DuelGateRegion redZone = closeZone(instance, "red");
        DuelGateRegion blueZone = closeZone(instance, "blue");
        Location redExit = preferredExit(instance, "red");
        Location blueExit = preferredExit(instance, "blue");
        for (UUID uuid : players(match)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getWorld() == null) continue;
            if (redZone != null && regionContainsPlayer(redZone, player)) {
                movePlayerToSafeSide(match, player, redZone, redExit, "red");
            } else if (blueZone != null && regionContainsPlayer(blueZone, player)) {
                movePlayerToSafeSide(match, player, blueZone, blueExit, "blue");
            }
        }
    }

    private DuelGateRegion closeZone(DuelGateInstance instance, String team) {
        if (instance == null || instance.arena == null) return null;
        if ("red".equalsIgnoreCase(team)) return instance.arena.redGateCloseZone();
        return instance.arena.blueGateCloseZone();
    }

    private Location preferredExit(DuelGateInstance instance, String team) {
        if (instance == null || instance.arena == null) return null;
        if ("red".equalsIgnoreCase(team)) return instance.arena.redGateExit() == null ? instance.arena.spawnA() : instance.arena.redGateExit();
        return instance.arena.blueGateExit() == null ? instance.arena.spawnB() : instance.arena.blueGateExit();
    }

    private List<UUID> players(DuelMatch match) {
        List<UUID> out = new ArrayList<>();
        out.addAll(match.teamOne());
        out.addAll(match.teamTwo());
        return out;
    }

    private void movePlayerToSafeSide(DuelMatch match, Player player, DuelGateRegion gate, Location preferredSide, String team) {
        Location before = player.getLocation();
        Location destination = safeGateDestination(player, gate, preferredSide);
        player.teleport(destination);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0.0F);
        debugCloseZone(match, player, team, gate, before, destination);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.getWorld() == null || !regionContainsPlayer(gate, player)) return;
            Location retry = safeGateDestination(player, gate, preferredSide);
            player.teleport(retry);
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0.0F);
            debugCloseZone(match, player, team + " retry", gate, player.getLocation(), retry);
        }, 1L);
    }

    private Location safeGateDestination(Player player, DuelGateRegion gate, Location preferredSide) {
        Location preferred = normalizeToPlayerWorld(player, preferredSide);
        if (preferred != null) return preferred;
        if (gate == null) return player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        double safeDistance = Math.max(0.5D, configs.get("duels/duels.yml").getDouble("duels.gates.safe-teleport-distance", 2.0D));
        Location reference = player.getLocation();
        Vector center = new Vector((gate.minX() + gate.maxX() + 1) / 2.0D, reference.getY(), (gate.minZ() + gate.maxZ() + 1) / 2.0D);
        Vector away = reference.toVector().subtract(center);
        away.setY(0.0D);
        if (away.lengthSquared() < 0.01D) away = new Vector(0, 0, 1);
        away.normalize().multiply(safeDistance);
        Location safe = reference.clone().add(away);
        safe.setY(reference.getY());
        return safe;
    }

    private Location normalizeToPlayerWorld(Player player, Location location) {
        if (player == null || player.getWorld() == null || location == null) return null;
        Location normalized = location.clone();
        if (normalized.getWorld() == null || !normalized.getWorld().equals(player.getWorld())) normalized.setWorld(player.getWorld());
        return normalized;
    }

    private boolean regionContainsPlayer(DuelGateRegion region, Player player) {
        if (region == null || player == null || player.getWorld() == null || !sameOrCompatibleWorld(region, player)) return false;
        Location feet = player.getLocation();
        if (containsCoordinates(region, feet) || containsCoordinates(region, feet.clone().add(0.0D, 1.0D, 0.0D)) || containsCoordinates(region, player.getEyeLocation())) return true;
        return region.box().expand(0.35D, 0.15D, 0.35D).overlaps(player.getBoundingBox());
    }

    private boolean sameOrCompatibleWorld(DuelGateRegion region, Player player) {
        String playerWorld = player.getWorld().getName();
        if (playerWorld.equalsIgnoreCase(region.worldName())) return true;
        String prefix = configs.get("duels/duels.yml").getString("duels.world-instances.prefix", "arena_");
        return playerWorld.startsWith(prefix) && playerWorld.contains("_match_");
    }

    private boolean containsCoordinates(DuelGateRegion region, Location location) {
        if (region == null || location == null) return false;
        return location.getBlockX() >= region.minX() && location.getBlockX() <= region.maxX()
            && location.getBlockY() >= region.minY() && location.getBlockY() <= region.maxY()
            && location.getBlockZ() >= region.minZ() && location.getBlockZ() <= region.maxZ();
    }

    private void debugCloseZone(DuelMatch match, Player player, String team, DuelGateRegion region, Location from, Location to) {
        if (!configs.get("duels/duels.yml").getBoolean("duels.gates.debug-close-zones", false)) return;
        plugin.getLogger().info("[Duels] Gate zone " + team + " moved " + player.getName()
            + " in match " + (match == null ? "unknown" : match.id())
            + " from " + formatLocation(from)
            + " to " + formatLocation(to)
            + " zone=" + formatRegion(region));
    }

    private String formatLocation(Location location) {
        if (location == null) return "null";
        return (location.getWorld() == null ? "null" : location.getWorld().getName())
            + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private String formatRegion(DuelGateRegion region) {
        if (region == null) return "null";
        return region.worldName() + ":" + region.minX() + "," + region.minY() + "," + region.minZ()
            + " -> " + region.maxX() + "," + region.maxY() + "," + region.maxZ();
    }

    private int clampLight(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private void play(DuelGateInstance instance, Sound sound, float volume, float pitch) {
        if (sound == null || instance.blocks.isEmpty()) return;
        Location location = instance.blocks.get(0).location;
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    private Sound sound(String path, Sound fallback) {
        String raw = configs.get("duels/duels.yml").getString(path, "");
        if (raw == null || raw.isBlank()) return fallback;
        String key = raw.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('.', '_').replace(':', '_');
        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private double easeOut(double t) {
        return 1.0D - Math.pow(1.0D - t, 3.0D);
    }

    private static final class DuelGateInstance {
        private final UUID matchId;
        private final DuelMap arena;
        private final List<GateBlock> blocks = new ArrayList<>();
        private BukkitTask task;
        private boolean opening;
        private boolean open;
        private boolean closing;
        private boolean restored;
        private boolean collisionActive = true;
        private double openDistance;

        private DuelGateInstance(UUID matchId, DuelMap arena) {
            this.matchId = matchId;
            this.arena = arena;
        }
    }

    private record GateBlock(String team, Location location, BlockState state, BlockData data, BlockDisplay display) {
    }
}
