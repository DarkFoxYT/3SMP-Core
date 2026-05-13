package net.dark.threecore.afk;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

public final class AfkZoneManager implements Listener {
    private static final String WAND_KEY = "3smpcore_afkzone_wand";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final VaultEconomyHook economyHook;
    private final AfkRewardService rewardService;
    private final Map<UUID, AfkPlayerState> states = new HashMap<>();
    private final Map<String, AfkZone> zones = new LinkedHashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, GameMode> returnGameModes = new HashMap<>();
    private final Map<UUID, Boolean> returnAllowFlight = new HashMap<>();
    private final Map<UUID, Boolean> returnFlying = new HashMap<>();
    private final Map<UUID, Long> inputGraceUntil = new HashMap<>();
    private final Map<UUID, Integer> taskIds = new HashMap<>();
    private BukkitTask task;

    public AfkZoneManager(JavaPlugin plugin, ConfigFiles configs, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.economyHook = new VaultEconomyHook(plugin);
        this.rewardService = new AfkRewardService(plugin, configs, economyHook);
        reload();
    }

    public void reload() {
        zones.clear();
        economyHook.reload();
        loadZones();
        start();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        states.clear();
        returnLocations.clear();
        returnGameModes.clear();
        returnAllowFlight.clear();
        returnFlying.clear();
        inputGraceUntil.clear();
        taskIds.clear();
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only.</red>");
            return;
        }
        if (args.length == 0) {
            openInfo(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> giveWand(player);
            case "create" -> createZone(player, args);
            case "delete" -> deleteZone(player, args);
            case "list" -> listZones(player);
            case "info" -> openInfo(player);
            default -> Text.send(player, "<gray>Use /3smpcore afkzone wand|create|delete|list</gray>");
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("wand", "create", "delete", "list", "info");
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info"))) return new ArrayList<>(zones.keySet());
        return List.of();
    }

    public boolean isInAfkZone(Player player) {
        if (player == null) return false;
        return zoneAt(player.getLocation()) != null;
    }

    public boolean isZoneBlocked(Player player) {
        if (player == null) return false;
        if (DuelService.isDuelPlayer(player)) return true;
        if (DungeonService.isDungeonPlayer(player)) return true;
        if (returnLocations.containsKey(player.getUniqueId())) return false;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return true;
        return !isInAfkZone(player);
    }

    private boolean shouldIgnoreAutoAfk(Player player) {
        if (player == null) return true;
        if (DuelService.isDuelPlayer(player) || DungeonService.isDungeonPlayer(player)) return true;
        if (returnLocations.containsKey(player.getUniqueId())) return false;
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    public void touch(Player player) {
        if (player == null || shouldIgnoreAutoAfk(player)) return;
        Long graceUntil = inputGraceUntil.get(player.getUniqueId());
        if (graceUntil != null && System.currentTimeMillis() < graceUntil) return;
        if (returnLocations.containsKey(player.getUniqueId())) {
            maintainAfkPlayer(player);
            return;
        }
        AfkZone zone = zoneAt(player.getLocation());
        if (zone == null) {
            AfkPlayerState state = state(player.getUniqueId());
            state.zoneId("");
            state.afk(false);
            state.lastRealMovementAt(System.currentTimeMillis());
            return;
        }
        AfkPlayerState state = state(player.getUniqueId());
        if (!zone.id().equalsIgnoreCase(state.zoneId())) {
            state.zoneId(zone.id());
            state.afk(false);
            state.lastRealMovementAt(System.currentTimeMillis());
            state.lastRewardAt(System.currentTimeMillis());
            Text.send(player, config("messages.entering", "<gradient:#1A2A4A:#D6E8F7>Entered AFK zone.</gradient>"));
        }
    }

    private void start() {
        if (task != null) task.cancel();
        long interval = Math.max(20L, configs.get("world/afk.yml").getLong("check-interval-seconds", 5L) * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void tick() {
        long afkAfter = Math.max(60L, configs.get("world/afk.yml").getLong("afk-after-minutes", 15L) * 60L * 1000L);
        long rewardEvery = Math.max(1L, configs.get("world/afk.yml").getLong("reward.interval-minutes", 30L)) * 60L * 1000L;
        long rewardAmount = Math.max(0L, configs.get("world/afk.yml").getLong("reward.amount", 1000L));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldIgnoreAutoAfk(player)) {
                if (states.containsKey(player.getUniqueId())) exitZone(player);
                continue;
            }
            if (returnLocations.containsKey(player.getUniqueId())) maintainAfkPlayer(player);
            AfkZone zone = zoneAt(player.getLocation());
            if (zone == null) {
                AfkPlayerState idle = state(player.getUniqueId());
                if (returnLocations.containsKey(player.getUniqueId())) {
                    maintainAfkPlayer(player);
                    continue;
                }
                if (System.currentTimeMillis() - idle.lastRealMovementAt() >= afkAfter) enterCommand(player);
                continue;
            }
            AfkPlayerState state = state(player.getUniqueId());
            if (!zone.id().equalsIgnoreCase(state.zoneId())) {
                state.zoneId(zone.id());
                state.afk(false);
                state.lastRealMovementAt(System.currentTimeMillis());
                state.lastRewardAt(System.currentTimeMillis());
                Text.send(player, config("messages.entering", "<gradient:#1A2A4A:#D6E8F7>Entered AFK zone.</gradient>"));
            }
            if (!state.afk() && System.currentTimeMillis() - state.lastRealMovementAt() >= afkAfter) {
                state.afk(true);
                state.lastRewardAt(System.currentTimeMillis());
                Text.send(player, config("messages.afk", "<yellow>You are now AFK.</yellow>"));
            }
            if (!state.afk()) continue;
            if (player.getGameMode() == GameMode.CREATIVE && !returnLocations.containsKey(player.getUniqueId())) continue;
            if (System.currentTimeMillis() - state.lastRewardAt() >= rewardEvery) {
                long payouts = Math.max(1L, (System.currentTimeMillis() - state.lastRewardAt()) / rewardEvery);
                for (long i = 0; i < payouts; i++) rewardService.reward(player, rewardAmount);
                state.lastRewardAt(state.lastRewardAt() + payouts * rewardEvery);
                Text.send(player, config("messages.reward", "<green>You received AFK rewards.</green>"));
            }
        }
    }

    public void enterCommand(Player player) {
        if (returnLocations.containsKey(player.getUniqueId())) {
            exitZone(player);
            return;
        }
        AfkZone zone = firstZone();
        if (zone == null) {
            Text.send(player, "<red>No AFK zone is configured.</red>");
            return;
        }
        World world = ensureZoneWorld(zone);
        if (world == null) {
            Text.send(player, "<red>AFK void world could not be loaded.</red>");
            return;
        }
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        returnGameModes.put(player.getUniqueId(), player.getGameMode());
        returnAllowFlight.put(player.getUniqueId(), player.getAllowFlight());
        returnFlying.put(player.getUniqueId(), player.isFlying());
        inputGraceUntil.put(player.getUniqueId(), System.currentTimeMillis() + 5000L);
        Location target = zone.center(world).clone().add(0.5, 0.5, 0.5);
        maintainAfkPlayer(player);
        player.teleport(target);
        AfkPlayerState state = state(player.getUniqueId());
        state.zoneId(zone.id());
        state.afk(true);
        state.lastRealMovementAt(System.currentTimeMillis());
        state.lastRewardAt(System.currentTimeMillis());
        maintainAfkPlayer(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> maintainAfkPlayer(player), 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> maintainAfkPlayer(player), 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> maintainAfkPlayer(player), 20L);
        Text.send(player, config("messages.entering", "<gradient:#1A2A4A:#D6E8F7>Entered AFK zone.</gradient>"));
    }

    private void maintainAfkPlayer(Player player) {
        if (player == null || !player.isOnline() || !returnLocations.containsKey(player.getUniqueId())) return;
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFallDistance(0.0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    private void exitZone(Player player) {
        AfkPlayerState removed = states.remove(player.getUniqueId());
        if (removed == null) return;
        Text.send(player, config("messages.leaving", "<gray>You left the AFK zone.</gray>"));
        Location back = returnLocations.remove(player.getUniqueId());
        inputGraceUntil.remove(player.getUniqueId());
        GameMode gameMode = returnGameModes.remove(player.getUniqueId());
        Boolean allowFlight = returnAllowFlight.remove(player.getUniqueId());
        Boolean flying = returnFlying.remove(player.getUniqueId());
        if (back != null && back.getWorld() != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (gameMode != null) player.setGameMode(gameMode);
                if (allowFlight != null) player.setAllowFlight(allowFlight);
                if (flying != null && player.getAllowFlight()) player.setFlying(flying);
                player.teleport(back);
            });
        }
    }

    private void createZone(Player player, String[] args) {
        if (!player.hasPermission("3smpcore.afkzone.admin")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /3smpcore afkzone create <name></red>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        Location loc = player.getLocation();
        AfkZone zone = new AfkZone(name, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        zones.put(name, zone);
        saveZones();
        Text.send(player, "<green>AFK zone created: </green><white>" + name + "</white>");
    }

    private void deleteZone(Player player, String[] args) {
        if (!player.hasPermission("3smpcore.afkzone.admin")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (args.length < 2) {
            Text.send(player, "<red>Usage: /3smpcore afkzone delete <name></red>");
            return;
        }
        zones.remove(args[1].toLowerCase(Locale.ROOT));
        saveZones();
        Text.send(player, "<green>AFK zone deleted.</green>");
    }

    private void listZones(Player player) {
        Text.send(player, "<gradient:#1A2A4A:#D6E8F7>AFK Zones</gradient> <gray>" + (zones.isEmpty() ? "none" : String.join(", ", zones.keySet())) + "</gray>");
    }

    private void openInfo(Player player) {
        Text.send(player, "<gradient:#1A2A4A:#D6E8F7>AFK Zones</gradient> <gray>" + String.join(", ", zones.keySet()) + "</gray>");
    }

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<gradient:#1A2A4A:#D6E8F7>AFK Zone Wand</gradient>"));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, WAND_KEY), PersistentDataType.STRING, "afkzone");
        item.setItemMeta(meta);
        return item;
    }

    private void giveWand(Player player) {
        if (!player.hasPermission("3smpcore.afkzone.admin")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        player.getInventory().addItem(wand());
        Text.send(player, "<green>AFK zone wand given.</green>");
    }

    private void loadZones() {
        var section = configs.get("world/afk.yml").getConfigurationSection("zones");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String world = section.getString(id + ".world", "spawn");
            zones.put(id.toLowerCase(Locale.ROOT), new AfkZone(id.toLowerCase(Locale.ROOT), world, section.getInt(id + ".pos1.x"), section.getInt(id + ".pos1.y"), section.getInt(id + ".pos1.z"), section.getInt(id + ".pos2.x"), section.getInt(id + ".pos2.y"), section.getInt(id + ".pos2.z")));
        }
    }

    private void saveZones() {
        var root = configs.get("world/afk.yml");
        root.set("zones", null);
        for (AfkZone zone : zones.values()) {
            String path = "zones." + zone.id();
            root.set(path + ".world", zone.world());
            root.set(path + ".pos1.x", zone.minX());
            root.set(path + ".pos1.y", zone.minY());
            root.set(path + ".pos1.z", zone.minZ());
            root.set(path + ".pos2.x", zone.maxX());
            root.set(path + ".pos2.y", zone.maxY());
            root.set(path + ".pos2.z", zone.maxZ());
        }
        try { root.save(new File(plugin.getDataFolder(), "world/afk.yml")); } catch (Exception ignored) {}
    }

    private AfkZone firstZone() {
        return zones.values().stream().findFirst().orElse(null);
    }

    private World ensureZoneWorld(AfkZone zone) {
        World existing = Bukkit.getWorld(zone.world());
        if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(zone.world());
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        String generator = configs.get("world/afk.yml").getString("zone.generator", "");
        if (generator != null && !generator.isBlank() && Bukkit.getPluginManager().getPlugin(generator) != null) {
            creator.generator(generator);
        } else {
            creator.generator(new VoidChunkGenerator());
        }
        World created = Bukkit.createWorld(creator);
        if (created != null) {
            created.setPVP(false);
            created.setAutoSave(false);
            Location spawn = zone.center(created);
            created.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
            if (configs.get("world/afk.yml").getBoolean("zone.import-to-multiverse", true)
                    && Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + created.getName() + " normal");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + created.getName() + " set hidden true");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + created.getName() + " set pvp false");
                });
            }
        }
        return created;
    }

    private AfkZone zoneAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (AfkZone zone : zones.values()) {
            if (!zone.world().equalsIgnoreCase(location.getWorld().getName())) continue;
            if (location.getBlockX() >= Math.min(zone.minX(), zone.maxX())
                    && location.getBlockX() <= Math.max(zone.minX(), zone.maxX())
                    && location.getBlockY() >= Math.min(zone.minY(), zone.maxY())
                    && location.getBlockY() <= Math.max(zone.minY(), zone.maxY())
                    && location.getBlockZ() >= Math.min(zone.minZ(), zone.maxZ())
                    && location.getBlockZ() <= Math.max(zone.minZ(), zone.maxZ())) {
                return zone;
            }
        }
        return null;
    }

    private AfkPlayerState state(UUID uuid) {
        return states.computeIfAbsent(uuid, AfkPlayerState::new);
    }

    private String config(String path, String fallback) {
        return configs.get("world/afk.yml").getString(path, fallback);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == null || event.getTo().getWorld() == null) return;
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            touch(event.getPlayer());
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY() && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        double distance = event.getFrom().distanceSquared(event.getTo());
        if (distance < 0.01D) return;
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
        returnLocations.remove(event.getPlayer().getUniqueId());
        returnGameModes.remove(event.getPlayer().getUniqueId());
        returnAllowFlight.remove(event.getPlayer().getUniqueId());
        returnFlying.remove(event.getPlayer().getUniqueId());
        inputGraceUntil.remove(event.getPlayer().getUniqueId());
    }

    private record AfkZone(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Location center(World loadedWorld) {
            World w = loadedWorld != null ? loadedWorld : Bukkit.getWorld(world);
            if (w == null) w = Bukkit.getWorlds().get(0);
            return new Location(w, (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        }
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {}
}
