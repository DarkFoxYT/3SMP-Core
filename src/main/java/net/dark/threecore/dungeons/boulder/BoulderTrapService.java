package net.dark.threecore.dungeons.boulder;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.dungeons.engine.PlacedDungeonRoom;
import net.dark.threecore.dungeons.event.BoulderTrapCompleteEvent;
import net.dark.threecore.dungeons.event.BoulderTrapKillEntityEvent;
import net.dark.threecore.dungeons.event.BoulderTrapRemoveEvent;
import net.dark.threecore.dungeons.event.BoulderTrapSpawnEvent;
import net.dark.threecore.dungeons.event.BoulderTrapTriggerEvent;
import net.dark.threecore.dungeons.integration.ModelEngineHook;
import net.dark.threecore.dungeons.integration.MythicMobsHook;
import net.dark.threecore.dungeons.event.DungeonEndEvent;
import net.dark.threecore.dungeons.event.DungeonStartEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BoulderTrapService implements Listener {
    private enum State {
        IDLE,
        ROLLING
    }

    private record BoulderVisual(Entity anchor, BlockDisplay display, boolean modelAttached) {
    }

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MythicMobsHook mythicMobs;
    private final ModelEngineHook modelEngine;
    private final Map<UUID, List<Instance>> bySession = new HashMap<>();
    private BukkitTask task;

    public BoulderTrapService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
        this.mythicMobs = new MythicMobsHook(plugin);
        this.modelEngine = new ModelEngineHook(plugin);
        BoulderTrapAPI.install(this);
    }

    public void spawnSession(UUID sessionId, List<PlacedDungeonRoom> rooms) {
        if (!enabled() || sessionId == null || rooms == null) return;
        List<Instance> instances = bySession.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
        for (PlacedDungeonRoom room : rooms) {
            for (BoulderTrapDefinition trap : room.definition().boulderTraps()) {
                Instance instance = create(sessionId, room, trap);
                if (instance != null) {
                    instances.add(instance);
                    Bukkit.getPluginManager().callEvent(new BoulderTrapSpawnEvent(instance));
                }
            }
        }
        if (!instances.isEmpty()) startTask();
    }

    public void activate(Instance instance) {
        if (instance == null || instance.state != State.IDLE || instance.complete) return;
        instance.state = State.ROLLING;
        if (instance.modelAttached) {
            modelEngine.stopAnimation(instance.anchor, idleAnimation());
            if (!modelEngine.playAnimation(instance.anchor, rollingAnimation(), true)) {
                instance.modelAttached = false;
                ensureDisplayFallback(instance);
            }
        }
        play(instance.location, "dungeons.boulder-traps.sounds.start", Sound.BLOCK_STONE_BREAK);
        instance.location.getWorld().spawnParticle(Particle.DUST, instance.location, 40, 0.8, 0.8, 0.8, new Particle.DustOptions(Color.fromRGB(250, 204, 21), 1.2F));
    }

    public void removeSession(UUID sessionId) {
        List<Instance> instances = bySession.remove(sessionId);
        if (instances != null) instances.forEach(this::remove);
    }

    public void killAll() {
        for (UUID session : new ArrayList<>(bySession.keySet())) removeSession(session);
    }

    public void preview(Player player, PlacedDungeonRoom room, BoulderTrapDefinition trap) {
        if (player == null || room == null || trap == null) return;
        draw(player, world(room, trap.spawn()), Color.fromRGB(250, 204, 21), 24);
        BoundingBox trigger = transformedBox(room, trap.triggerMin(), trap.triggerMax());
        drawBox(player, trigger, Color.fromRGB(248, 113, 113));
        Location previous = null;
        for (Vector point : trap.path()) {
            Location current = world(room, point);
            draw(player, current, Color.fromRGB(96, 165, 250), 12);
            if (previous != null) drawLine(player, previous, current, Color.fromRGB(219, 234, 254));
            previous = current;
        }
    }

    public Instance test(Player player, PlacedDungeonRoom room, BoulderTrapDefinition trap) {
        if (player == null || room == null || trap == null) return null;
        Instance instance = create(UUID.randomUUID(), room, trap);
        if (instance == null) return null;
        bySession.computeIfAbsent(instance.sessionId, ignored -> new ArrayList<>()).add(instance);
        Bukkit.getPluginManager().callEvent(new BoulderTrapSpawnEvent(instance));
        activate(instance);
        startTask();
        return instance;
    }

    public int activeCount() {
        return bySession.values().stream().mapToInt(List::size).sum();
    }

    @EventHandler
    public void onDungeonStart(DungeonStartEvent event) {
        spawnSession(event.session().id(), event.session().layout().rooms());
    }

    @EventHandler
    public void onDungeonEnd(DungeonEndEvent event) {
        removeSession(event.session().id());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled() || !DungeonService.isDungeonPlayer(event.getPlayer())) return;
        Location to = event.getTo();
        if (to == null) return;
        for (List<Instance> instances : bySession.values()) {
            for (Instance instance : instances) {
                if (instance.state == State.IDLE && !instance.complete && instance.trigger.contains(to.toVector())) {
                    Bukkit.getPluginManager().callEvent(new BoulderTrapTriggerEvent(instance, event.getPlayer()));
                    activate(instance);
                }
            }
        }
    }

    private Instance create(UUID sessionId, PlacedDungeonRoom room, BoulderTrapDefinition trap) {
        World world = Bukkit.getWorld(room.world());
        if (world == null) return null;
        List<Location> path = new ArrayList<>();
        if (trap.path().isEmpty()) path.add(world(room, trap.spawn()));
        else for (Vector point : trap.path()) path.add(world(room, point));
        Location spawn = world(room, trap.spawn());
        if (!path.isEmpty()) spawn = path.get(0).clone();
        BoulderVisual visual = spawnVisual(spawn, trap);
        BoundingBox trigger = transformedBox(room, trap.triggerMin(), trap.triggerMax());
        return new Instance(sessionId, room.definition().id(), trap, visual.anchor(), visual.display(), visual.modelAttached(), spawn, path, trigger);
    }

    private void startTask() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, Math.max(1L, configs.get("dungeons/dungeons.yml").getLong("dungeons.boulder-traps.movement.tick-rate", 1L)));
    }

    private void tick() {
        boolean any = false;
        for (Iterator<Map.Entry<UUID, List<Instance>>> iterator = bySession.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, List<Instance>> entry = iterator.next();
            entry.getValue().removeIf(instance -> {
                if (instance.complete) {
                    remove(instance);
                    return true;
                }
                if (instance.state == State.ROLLING) move(instance);
                return false;
            });
            if (entry.getValue().isEmpty()) iterator.remove();
            else any = true;
        }
        if (!any && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void move(Instance instance) {
        if (instance.segment >= instance.path.size()) {
            complete(instance);
            return;
        }
        Location target = instance.path.get(instance.segment);
        Vector delta = target.toVector().subtract(instance.location.toVector());
        if (delta.length() <= Math.max(0.05D, instance.speed)) {
            instance.location = target.clone();
            instance.segment++;
            if (instance.segment >= instance.path.size()) {
                complete(instance);
                return;
            }
        } else {
            instance.speed = Math.min(instance.trap.maxSpeed(), instance.speed + instance.trap.acceleration());
            Vector step = delta.normalize().multiply(instance.speed);
            instance.location.add(step);
            instance.location.setYaw((float) Math.toDegrees(Math.atan2(-step.getX(), step.getZ())));
        }
        if (instance.anchor != null && instance.anchor.isValid()) instance.anchor.teleport(instance.location);
        if (instance.display != null && instance.display.isValid() && !instance.display.equals(instance.anchor)) instance.display.teleport(instance.location);
        modelEngine.setModelRotation(instance.anchor, instance.location.getYaw(), instance.location.getPitch());
        damage(instance);
        if (instance.ticks++ % configs.get("dungeons/dungeons.yml").getInt("dungeons.boulder-traps.sounds.loop-interval-ticks", 12) == 0) play(instance.location, "dungeons.boulder-traps.sounds.rolling-loop", Sound.BLOCK_DEEPSLATE_STEP);
        if (configs.get("dungeons/dungeons.yml").getBoolean("dungeons.boulder-traps.particles.rolling-dust", true)) instance.location.getWorld().spawnParticle(Particle.BLOCK, instance.location, 4, 0.8, 0.35, 0.8, Material.DEEPSLATE.createBlockData());
    }

    private void damage(Instance instance) {
        double radius = instance.trap.killRadius();
        double vertical = instance.trap.verticalRadius();
        for (Entity entity : instance.location.getWorld().getNearbyEntities(instance.location, radius, vertical, radius)) {
            if (entity.equals(instance.display)) continue;
            if (entity.equals(instance.anchor)) continue;
            if (entity instanceof Player player && player.hasPermission(configs.get("dungeons/dungeons.yml").getString("dungeons.boulder-traps.damage.bypass-permission", "3smpcore.dungeon.boulder.bypass"))) continue;
            if (entity instanceof LivingEntity living) {
                if (entity instanceof Player player && !DungeonService.isDungeonPlayer(player)) continue;
                Bukkit.getPluginManager().callEvent(new BoulderTrapKillEntityEvent(instance, entity));
                living.damage(configs.get("dungeons/dungeons.yml").getDouble("dungeons.boulder-traps.damage.damage-amount", 9999.0D));
                if (!living.isDead()) living.setHealth(0.0D);
            }
        }
    }

    private void complete(Instance instance) {
        play(instance.location, "dungeons.boulder-traps.sounds.impact", Sound.ENTITY_GENERIC_EXPLODE);
        instance.location.getWorld().spawnParticle(Particle.EXPLOSION, instance.location, 1);
        instance.location.getWorld().spawnParticle(Particle.BLOCK, instance.location, 50, 1.2, 0.6, 1.2, Material.DEEPSLATE.createBlockData());
        if (instance.modelAttached) modelEngine.stopAnimation(instance.anchor, rollingAnimation());
        instance.complete = true;
        Bukkit.getPluginManager().callEvent(new BoulderTrapCompleteEvent(instance));
    }

    private void remove(Instance instance) {
        if (instance.modelAttached) {
            modelEngine.stopAnimation(instance.anchor, idleAnimation());
            modelEngine.stopAnimation(instance.anchor, rollingAnimation());
        }
        if (instance.display != null && instance.display.isValid()) instance.display.remove();
        if (instance.anchor != null && instance.anchor.isValid() && !instance.anchor.equals(instance.display)) mythicMobs.removeMob(instance.anchor);
        modelEngine.removeModel(instance.anchor);
        Bukkit.getPluginManager().callEvent(new BoulderTrapRemoveEvent(instance));
    }

    private BoulderVisual spawnVisual(Location spawn, BoulderTrapDefinition trap) {
        Entity anchor = null;
        if (configs.get("dungeons/dungeons.yml").getBoolean("dungeons.boulder-traps.mythicmobs.enabled", true)) {
            anchor = spawnModelAnchor(spawn, trap);
            if (anchor != null) {
                String modelId = modelEngineId(trap);
                if (configs.get("dungeons/dungeons.yml").getBoolean("dungeons.boulder-traps.modelengine.enabled", true)
                        && modelEngine.attachModel(anchor, modelId)) {
                    modelEngine.setModelScale(anchor, configs.get("dungeons/dungeons.yml").getDouble("dungeons.boulder-traps.modelengine.scale", 1.0D));
                    if (modelEngine.playAnimation(anchor, idleAnimation(), true)) {
                        return new BoulderVisual(anchor, null, true);
                    }
                    modelEngine.removeModel(anchor);
                }
                mythicMobs.removeMob(anchor);
            }
        }
        BlockDisplay display = spawnDisplay(spawn);
        return new BoulderVisual(display, display, false);
    }

    private Entity spawnModelAnchor(Location spawn, BoulderTrapDefinition trap) {
        String mobId = mythicMobId(trap);
        Entity entity = mythicMobs.spawnMob(mobId, spawn);
        if (entity == null) return null;
        mythicMobs.setNoAI(entity);
        return entity;
    }

    private BlockDisplay spawnDisplay(Location spawn) {
        BlockData visual = fallbackBlock();
        return spawn.getWorld().spawn(spawn, BlockDisplay.class, entity -> {
            entity.setBlock(visual);
            entity.setPersistent(false);
            entity.setInterpolationDuration(1);
            entity.setTeleportDuration(1);
            entity.setBrightness(new Display.Brightness(
                    clampLight(configs.get("dungeons/dungeons.yml").getInt("dungeons.boulder-traps.fallback-visual.brightness.block-light", 15)),
                    clampLight(configs.get("dungeons/dungeons.yml").getInt("dungeons.boulder-traps.fallback-visual.brightness.sky-light", 15))
            ));
        });
    }

    private void ensureDisplayFallback(Instance instance) {
        if (instance.display != null && instance.display.isValid()) return;
        instance.display = spawnDisplay(instance.location);
    }

    private Location world(PlacedDungeonRoom room, Vector local) {
        Vector transformed = room.transform().localToWorld(local);
        World world = Bukkit.getWorld(room.world());
        return new Location(world, transformed.getX() + 0.5D, transformed.getY(), transformed.getZ() + 0.5D);
    }

    private BoundingBox transformedBox(PlacedDungeonRoom room, Vector first, Vector second) {
        Vector a = room.transform().localToWorld(first);
        Vector b = room.transform().localToWorld(second);
        return BoundingBox.of(a, b).expand(0.5D);
    }

    private BlockData fallbackBlock() {
        String raw = configs.get("dungeons/dungeons.yml").getString("dungeons.boulder-traps.fallback-visual.block", configs.get("dungeons/dungeons.yml").getString("dungeons.boulder-traps.fallback-visual-block", "DEEPSLATE"));
        try {
            Material material = Material.valueOf(raw.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
            return material.createBlockData();
        } catch (Exception ignored) {
            return Material.DEEPSLATE.createBlockData();
        }
    }

    private void play(Location location, String path, Sound fallback) {
        if (location == null || location.getWorld() == null) return;
        String raw = configs.get("dungeons/dungeons.yml").getString(path, fallback.name());
        Sound sound = fallback;
        try {
            sound = Sound.valueOf(raw.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "").replace('.', '_').replace(':', '_'));
        } catch (Exception ignored) {
        }
        location.getWorld().playSound(location, sound, (float) configs.get("dungeons/dungeons.yml").getDouble("dungeons.boulder-traps.sounds.volume", 1.4D), (float) configs.get("dungeons/dungeons.yml").getDouble("dungeons.boulder-traps.sounds.pitch", 0.65D));
    }

    private void draw(Player player, Location location, Color color, int count) {
        if (location == null) return;
        player.spawnParticle(Particle.DUST, location.clone().add(0.0D, 0.5D, 0.0D), count, 0.25D, 0.25D, 0.25D, new Particle.DustOptions(color, 1.1F));
    }

    private void drawLine(Player player, Location from, Location to, Color color) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(1, (int) Math.ceil(delta.length() * 2.0D));
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.8F);
        for (int i = 0; i <= steps; i++) {
            Location point = from.clone().add(delta.clone().multiply(i / (double) steps));
            player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, dust);
        }
    }

    private void drawBox(Player player, BoundingBox box, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9F);
        for (double x = box.getMinX(); x <= box.getMaxX(); x += 1.0D) {
            for (double y = box.getMinY(); y <= box.getMaxY(); y += Math.max(1.0D, box.getHeight())) {
                player.spawnParticle(Particle.DUST, new Location(player.getWorld(), x, y, box.getMinZ()), 1, dust);
                player.spawnParticle(Particle.DUST, new Location(player.getWorld(), x, y, box.getMaxZ()), 1, dust);
            }
        }
        for (double z = box.getMinZ(); z <= box.getMaxZ(); z += 1.0D) {
            for (double y = box.getMinY(); y <= box.getMaxY(); y += Math.max(1.0D, box.getHeight())) {
                player.spawnParticle(Particle.DUST, new Location(player.getWorld(), box.getMinX(), y, z), 1, dust);
                player.spawnParticle(Particle.DUST, new Location(player.getWorld(), box.getMaxX(), y, z), 1, dust);
            }
        }
    }

    private int clampLight(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private boolean enabled() {
        return configs.get("dungeons/dungeons.yml").getBoolean("dungeons.boulder-traps.enabled", true);
    }

    private String mythicMobId(BoulderTrapDefinition trap) {
        if (!trap.mythicMobId().isBlank()) return trap.mythicMobId();
        String legacy = configs.get("dungeons/dungeons.yml").getString("dungeons.boulder-traps.mythicmobs.default-mob-id", "DungeonBoulder");
        return configs.get("dungeons/dungeons.yml").getString("boulder.mythicmob-id", legacy);
    }

    private String modelEngineId(BoulderTrapDefinition trap) {
        if (!trap.modelEngineId().isBlank()) return trap.modelEngineId();
        String legacy = configs.get("dungeons/dungeons.yml").getString("dungeons.boulder-traps.modelengine.default-model-id", "boulder");
        return configs.get("dungeons/dungeons.yml").getString("boulder.modelengine-id", legacy);
    }

    private String idleAnimation() {
        return configs.get("dungeons/dungeons.yml").getString("boulder.animations.idle", "idle");
    }

    private String rollingAnimation() {
        return configs.get("dungeons/dungeons.yml").getString("boulder.animations.rolling", "rolling");
    }

    public static final class Instance {
        private final UUID sessionId;
        private final String roomId;
        private final BoulderTrapDefinition trap;
        private final Entity anchor;
        private BlockDisplay display;
        private final List<Location> path;
        private final BoundingBox trigger;
        private int segment = 1;
        private double speed;
        private int ticks;
        private State state = State.IDLE;
        private boolean modelAttached;
        private boolean complete;
        private Location location;

        private Instance(UUID sessionId, String roomId, BoulderTrapDefinition trap, Entity anchor, BlockDisplay display, boolean modelAttached, Location location, List<Location> path, BoundingBox trigger) {
            this.sessionId = sessionId;
            this.roomId = roomId;
            this.trap = trap;
            this.anchor = anchor;
            this.display = display;
            this.modelAttached = modelAttached;
            this.location = location;
            this.path = path;
            this.trigger = trigger;
            this.speed = trap.speed();
        }

        public UUID sessionId() { return sessionId; }
        public String roomId() { return roomId; }
        public String trapId() { return trap.id(); }
    }
}
