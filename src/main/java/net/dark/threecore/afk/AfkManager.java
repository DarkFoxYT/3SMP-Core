package net.dark.threecore.afk;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AfkManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final Map<UUID, Long> activity = new HashMap<>();
    private final Map<UUID, AfkState> afkStates = new HashMap<>();
    private final Map<UUID, Long> afkEnteredAt = new HashMap<>();
    private final Map<UUID, Long> lastPayoutAt = new HashMap<>();
    private int taskId = -1;

    public AfkManager(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository) { this.plugin = plugin; this.configs = configs; this.repository = repository; start(); }
    public void reload() { stop(); start(); }
    public void shutdown() { stop(); activity.clear(); afkStates.clear(); afkEnteredAt.clear(); lastPayoutAt.clear(); }

    private void start() {
        if (!configs.get("world/afk.yml").getBoolean("enabled", true)) return;
        long interval = Math.max(20L, configs.get("world/afk.yml").getLong("check-interval-seconds", 30L) * 20L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::check, interval, interval);
    }
    private void stop() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
    private void touch(Player player) {
        if (afkStates.containsKey(player.getUniqueId())) { returnFromAfk(player); return; }
        activity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void sendToAfkWorld(Player player) {
        World world = afkWorld();
        if (world == null) { player.kick(MiniMessage.miniMessage().deserialize(configs.get("world/afk.yml").getString("kick-message", "<red>You were kicked for being AFK.</red>"))); return; }
        if (afkStates.containsKey(player.getUniqueId())) {
            maintainAfkState(player);
            return;
        }
        afkStates.put(player.getUniqueId(), AfkState.capture(player));
        long now = System.currentTimeMillis();
        afkEnteredAt.put(player.getUniqueId(), now);
        lastPayoutAt.put(player.getUniqueId(), now);
        Location spawn = afkSpawn(world);
        player.teleport(spawn);
        maintainAfkState(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> maintainAfkState(player), 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> maintainAfkState(player), 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> maintainAfkState(player), 20L);
        player.sendActionBar(MiniMessage.miniMessage().deserialize(configs.get("world/afk.yml").getString("zone.actionbar", "<gray>AFK zone. Move or click to return to spawn.</gray>")));
    }

    private void returnFromAfk(Player player) {
        AfkState state = afkStates.remove(player.getUniqueId());
        afkEnteredAt.remove(player.getUniqueId());
        lastPayoutAt.remove(player.getUniqueId());
        activity.put(player.getUniqueId(), System.currentTimeMillis());
        World spawnWorld = Bukkit.getWorld(configs.get("core/config.yml").getString("spawn.world", "spawn"));
        Location spawn = spawnWorld == null ? player.getWorld().getSpawnLocation() : new Location(spawnWorld, configs.get("core/config.yml").getDouble("spawn.location.x", 0.5), configs.get("core/config.yml").getDouble("spawn.location.y", 65.0), configs.get("core/config.yml").getDouble("spawn.location.z", 0.5), (float) configs.get("core/config.yml").getDouble("spawn.location.yaw", 0.0), 0f);
        player.teleport(spawn);
        if (state != null) state.restore(player);
    }

    private World afkWorld() {
        String name = configs.get("world/afk.yml").getString("zone.world", configs.get("world/afk.yml").getString("zones.default.world", "afk_void"));
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(name);
        String generator = configs.get("world/afk.yml").getString("zone.generator", "VoidGen");
        if (generator != null && !generator.isBlank()) creator.generator(generator);
        World world = Bukkit.createWorld(creator);
        if (world != null) { world.setSpawnLocation(0, configs.get("world/afk.yml").getInt("zone.spawn.y", 80), 0); world.setPVP(false); world.setAutoSave(false); }
        return world;
    }

    private Location afkSpawn(World world) {
        var yaml = configs.get("world/afk.yml");
        double x = yaml.contains("zone.spawn.x") ? yaml.getDouble("zone.spawn.x", 0.5) : (yaml.getDouble("zones.default.pos1.x", -10.0) + yaml.getDouble("zones.default.pos2.x", 10.0)) / 2.0D + 0.5D;
        double y = yaml.contains("zone.spawn.y") ? yaml.getDouble("zone.spawn.y", 80.0) : Math.max(yaml.getDouble("zones.default.pos1.y", 60.0), yaml.getDouble("zones.default.pos2.y", 90.0)) - 1.0D;
        double z = yaml.contains("zone.spawn.z") ? yaml.getDouble("zone.spawn.z", 0.5) : (yaml.getDouble("zones.default.pos1.z", -10.0) + yaml.getDouble("zones.default.pos2.z", 10.0)) / 2.0D + 0.5D;
        return new Location(world, x, y, z, 0f, 0f);
    }

    private void maintainAfkState(Player player) {
        if (player == null || !player.isOnline() || !afkStates.containsKey(player.getUniqueId())) return;
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    private void check() {
        long timeout = afkTimeoutMillis();
        long payoutEvery = Math.max(1L, configs.get("world/afk.yml").getLong("payout-every-minutes", 30L)) * 60L * 1000L;
        long payoutAmount = Math.max(0L, configs.get("world/afk.yml").getLong("payout-amount", 1000L));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (afkStates.containsKey(player.getUniqueId())) continue;
            long last = activity.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
            if (System.currentTimeMillis() - last >= timeout) sendToAfkWorld(player);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!afkStates.containsKey(player.getUniqueId())) continue;
            maintainAfkState(player);
            long enteredAt = afkEnteredAt.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
            long elapsed = System.currentTimeMillis() - enteredAt;
            long lastPaid = lastPayoutAt.getOrDefault(player.getUniqueId(), enteredAt);
            if (System.currentTimeMillis() - lastPaid < payoutEvery) continue;
            if (payoutAmount <= 0L) continue;
            long payouts = Math.max(1L, (System.currentTimeMillis() - lastPaid) / payoutEvery);
            long reward = payouts * payoutAmount;
            double current = repository.getMoneyBalance(player.getUniqueId());
            repository.setMoneyBalance(player.getUniqueId(), current + reward);
            lastPayoutAt.put(player.getUniqueId(), lastPaid + payouts * payoutEvery);
            player.sendActionBar(MiniMessage.miniMessage().deserialize(configs.get("world/afk.yml").getString("zone.reward-actionbar", "<green>AFK reward received.</green>")));
        }
    }
    public void sendCommand(Player player) { sendToAfkWorld(player); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        if (afkStates.containsKey(player.getUniqueId())) {
            long enteredAt = afkEnteredAt.getOrDefault(player.getUniqueId(), 0L);
            boolean grace = System.currentTimeMillis() - enteredAt < 1500L;
            boolean changedWorld = event.getFrom().getWorld() != null && event.getTo().getWorld() != null && !event.getFrom().getWorld().equals(event.getTo().getWorld());
            double horizontal = Math.pow(event.getFrom().getX() - event.getTo().getX(), 2) + Math.pow(event.getFrom().getZ() - event.getTo().getZ(), 2);
            if (!grace && !changedWorld && horizontal > 0.0001D) returnFromAfk(player);
            else maintainAfkState(player);
            return;
        }
        if (event.getFrom().getWorld() == null || event.getTo().getWorld() == null || !event.getFrom().getWorld().equals(event.getTo().getWorld()) || event.getFrom().distanceSquared(event.getTo()) > 0.0001D) touch(player);
    }
    @EventHandler public void onInteract(PlayerInteractEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onPlace(BlockPlaceEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onChat(AsyncPlayerChatEvent event) { touch(event.getPlayer()); }

    private record AfkState(GameMode gameMode, boolean allowFlight, boolean flying, boolean invulnerable) {
        private static AfkState capture(Player player) {
            return new AfkState(player.getGameMode(), player.getAllowFlight(), player.isFlying(), player.isInvulnerable());
        }

        private void restore(Player player) {
            player.setGameMode(gameMode);
            player.setInvulnerable(invulnerable);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.setFallDistance(0.0f);
        }
    }

    private long afkTimeoutMillis() {
        var yaml = configs.get("world/afk.yml");
        if (yaml.contains("kick-after-seconds")) return Math.max(1L, yaml.getLong("kick-after-seconds", 900L)) * 1000L;
        return Math.max(1L, yaml.getLong("afk-after-minutes", 5L)) * 60L * 1000L;
    }
}
