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
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, Long> afkEnteredAt = new HashMap<>();
    private final Map<UUID, Long> lastPayoutAt = new HashMap<>();
    private int taskId = -1;

    public AfkManager(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository) { this.plugin = plugin; this.configs = configs; this.repository = repository; start(); }
    public void reload() { stop(); start(); }
    public void shutdown() { stop(); activity.clear(); returnLocations.clear(); afkEnteredAt.clear(); lastPayoutAt.clear(); }

    private void start() {
        if (!configs.get("world/afk.yml").getBoolean("enabled", true)) return;
        long interval = Math.max(20L, configs.get("world/afk.yml").getLong("check-interval-seconds", 30L) * 20L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::check, interval, interval);
    }
    private void stop() { if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
    private void touch(Player player) {
        if (returnLocations.containsKey(player.getUniqueId())) { returnFromAfk(player); return; }
        activity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void sendToAfkWorld(Player player) {
        if (player.hasPermission(configs.get("world/afk.yml").getString("bypass-permission", "3smpcore.afk.bypass"))) return;
        World world = afkWorld();
        if (world == null) { player.kick(MiniMessage.miniMessage().deserialize(configs.get("world/afk.yml").getString("kick-message", "<red>You were kicked for being AFK.</red>"))); return; }
        returnLocations.put(player.getUniqueId(), player.getLocation());
        afkEnteredAt.put(player.getUniqueId(), System.currentTimeMillis());
        lastPayoutAt.put(player.getUniqueId(), System.currentTimeMillis());
        Location spawn = new Location(world, configs.get("world/afk.yml").getDouble("zone.spawn.x", 0.5), configs.get("world/afk.yml").getDouble("zone.spawn.y", 80.0), configs.get("world/afk.yml").getDouble("zone.spawn.z", 0.5), 0f, 0f);
        player.teleport(spawn);
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.sendActionBar(MiniMessage.miniMessage().deserialize(configs.get("world/afk.yml").getString("zone.actionbar", "<gray>AFK zone. Move or click to return to spawn.</gray>")));
    }

    private void returnFromAfk(Player player) {
        returnLocations.remove(player.getUniqueId());
        afkEnteredAt.remove(player.getUniqueId());
        lastPayoutAt.remove(player.getUniqueId());
        activity.put(player.getUniqueId(), System.currentTimeMillis());
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        World spawnWorld = Bukkit.getWorld(configs.get("core/config.yml").getString("spawn.world", "spawn"));
        Location spawn = spawnWorld == null ? player.getWorld().getSpawnLocation() : new Location(spawnWorld, configs.get("core/config.yml").getDouble("spawn.location.x", 0.5), configs.get("core/config.yml").getDouble("spawn.location.y", 65.0), configs.get("core/config.yml").getDouble("spawn.location.z", 0.5), (float) configs.get("core/config.yml").getDouble("spawn.location.yaw", 0.0), 0f);
        player.teleport(spawn);
    }

    private World afkWorld() {
        String name = configs.get("world/afk.yml").getString("zone.world", "afk_void");
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(name);
        String generator = configs.get("world/afk.yml").getString("zone.generator", "VoidGen");
        if (generator != null && !generator.isBlank()) creator.generator(generator);
        World world = Bukkit.createWorld(creator);
        if (world != null) { world.setSpawnLocation(0, configs.get("world/afk.yml").getInt("zone.spawn.y", 80), 0); world.setPVP(false); world.setAutoSave(false); }
        return world;
    }
    private void check() {
        long timeout = configs.get("world/afk.yml").getLong("kick-after-seconds", 900L) * 1000L;
        long payoutEvery = Math.max(1L, configs.get("world/afk.yml").getLong("payout-every-minutes", 30L)) * 60L * 1000L;
        long payoutAmount = Math.max(0L, configs.get("world/afk.yml").getLong("payout-amount", 1000L));
        String bypass = configs.get("world/afk.yml").getString("bypass-permission", "3smpcore.afk.bypass");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(bypass)) { touch(player); continue; }
            if (returnLocations.containsKey(player.getUniqueId())) continue;
            long last = activity.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
            if (System.currentTimeMillis() - last >= timeout) sendToAfkWorld(player);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!returnLocations.containsKey(player.getUniqueId())) continue;
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
    @EventHandler public void onMove(PlayerMoveEvent event) { if (event.getTo() != null && event.getFrom().distanceSquared(event.getTo()) > 0.0001D) touch(event.getPlayer()); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onPlace(BlockPlaceEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) { touch(event.getPlayer()); }
    @EventHandler public void onChat(AsyncPlayerChatEvent event) { touch(event.getPlayer()); }
}
