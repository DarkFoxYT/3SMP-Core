package net.dark.threecore.joinqueue;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.text.Text;
import net.dark.threecore.welcome.WelcomeService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class JoinQueueService implements Listener {
    private static final Set<UUID> QUEUED = new HashSet<>();
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final SpawnService spawnService;
    private final WelcomeService welcomeService;
    private final List<UUID> queue = new ArrayList<>();
    private BukkitTask task;

    public JoinQueueService(JavaPlugin plugin, ConfigFiles configs, SpawnService spawnService, WelcomeService welcomeService) {
        this.plugin = plugin;
        this.configs = configs;
        this.spawnService = spawnService;
        this.welcomeService = welcomeService;
        start();
    }

    public static boolean isQueued(UUID uuid) { return QUEUED.contains(uuid); }
    public void reload() { stop(); start(); }
    public void shutdown() { stop(); queue.clear(); QUEUED.clear(); }

    private void start() {
        if (!configs.get("core/join-queue.yml").getBoolean("enabled", true)) return;
        long interval = Math.max(20L, configs.get("core/join-queue.yml").getLong("release.interval-ticks", 40L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void stop() { if (task != null) task.cancel(); task = null; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!configs.get("core/join-queue.yml").getBoolean("enabled", true)) return;
        Player player = event.getPlayer();
        clearQueueEffects(player);
        if (player.hasPermission(configs.get("core/join-queue.yml").getString("bypass-permission", "3smpcore.queue.bypass"))) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> releaseDirect(player), 2L);
            return;
        }
        queue.add(player.getUniqueId());
        sortQueue();
        QUEUED.add(player.getUniqueId());
        sendToQueueWorld(player);
        updateActionBars();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { queue.remove(event.getPlayer().getUniqueId()); QUEUED.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!configs.get("core/join-queue.yml").getBoolean("enabled", true)) return;
        if (!QUEUED.contains(event.getPlayer().getUniqueId())) clearQueueEffects(event.getPlayer());
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (!QUEUED.contains(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) event.setTo(event.getFrom());
    }

    private void tick() {
        if (queue.isEmpty()) return;
        int online = Bukkit.getOnlinePlayers().size() - queue.size();
        int maxOnline = configs.get("core/join-queue.yml").getInt("release.max-online-before-queue", -1);
        int perTick = Math.max(1, configs.get("core/join-queue.yml").getInt("release.players-per-interval", 1));
        int released = 0;
        while (!queue.isEmpty() && released < perTick && (maxOnline < 0 || online < maxOnline)) {
            UUID uuid = queue.remove(0);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            release(player);
            released++;
            online++;
        }
        updateActionBars();
    }

    private void releaseDirect(Player player) { clearQueueEffects(player); spawnService.sendToSpawn(player); welcomeService.send(player); }
    private void release(Player player) { QUEUED.remove(player.getUniqueId()); clearQueueEffects(player); spawnService.sendToSpawn(player); welcomeService.send(player); }

    private void sendToQueueWorld(Player player) {
        World world = queueWorld();
        if (world == null) return;
        Location loc = new Location(world, configs.get("core/join-queue.yml").getDouble("world.spawn.x", 0.5), configs.get("core/join-queue.yml").getDouble("world.spawn.y", 80.0), configs.get("core/join-queue.yml").getDouble("world.spawn.z", 0.5));
        player.teleport(loc);
        player.setGameMode(GameMode.ADVENTURE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 255, true, false, false));
    }

    private void clearQueueEffects(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    private World queueWorld() {
        String name = configs.get("core/join-queue.yml").getString("world.name", "queue_void");
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(name);
        String generator = configs.get("core/join-queue.yml").getString("world.generator", "VoidGen");
        if (generator != null && !generator.isBlank()) creator.generator(generator);
        World world = Bukkit.createWorld(creator);
        if (world != null) {
            world.setPVP(false);
            world.setAutoSave(false);
            registerMultiverse(world);
        }
        return world;
    }

    private void registerMultiverse(World world) {
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + world.getName() + " normal");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set hidden true");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set pvp false");
        });
    }

    private void updateActionBars() {
        for (int i = 0; i < queue.size(); i++) {
            Player player = Bukkit.getPlayer(queue.get(i));
            if (player == null) continue;
            Text.actionBar(player, configs.get("core/join-queue.yml").getString("messages.actionbar", "<gradient:#1A2A4A:#D6E8F7>Queue</gradient> <gray>Position:</gray> <white>{position}</white><gray>/{total}</gray>").replace("{position}", String.valueOf(i + 1)).replace("{total}", String.valueOf(queue.size())));
        }
    }

    private void sortQueue() {
        queue.sort(Comparator.comparingInt(uuid -> -priority(Bukkit.getPlayer(uuid))));
    }

    private int priority(Player player) {
        if (player == null) return 0;
        int best = 0;
        for (String node : configs.get("core/join-queue.yml").getStringList("priority-permissions")) {
            String[] parts = node.split(":");
            if (parts.length != 2) continue;
            try { if (player.hasPermission(parts[0])) best = Math.max(best, Integer.parseInt(parts[1])); } catch (NumberFormatException ignored) {}
        }
        return best;
    }
}
