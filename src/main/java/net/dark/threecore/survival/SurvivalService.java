package net.dark.threecore.survival;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.rtp.RtpManager;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SurvivalService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final RtpManager rtpManager;
    private final PlayerDataRepository repository;
    private final ConcurrentHashMap<UUID, Boolean> profileLoaded = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> pendingPlacementInventorySync = new ConcurrentHashMap<>();
    private SpawnService spawnService;

    public SurvivalService(JavaPlugin plugin, ConfigFiles configs, RtpManager rtpManager, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.configs = configs;
        this.rtpManager = rtpManager;
        this.repository = repository;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (!configs.get("world/survival.yml").getBoolean("command.enabled", true)) {
            Text.send(player, configs.get("world/survival.yml").getString("command.disabled-message", "<red>/survival is disabled. Use /rtp when you are ready to enter survival.</red>"));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("rtp")) { rtpManager.teleport(player); return; }
        teleport(player);
    }

    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("rtp") : List.of(); }
    public void setSpawnService(SpawnService spawnService) { this.spawnService = spawnService; }

    public boolean sharedInventoryEnabled() {
        return configs.get("world/survival.yml").getBoolean("inventory.shared-spawn-survival-market", true);
    }

    public void teleport(Player player) {
        var yaml = configs.get("world/survival.yml");
        String worldName = yaml.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) { Text.send(player, "<red>Survival world is not loaded: " + worldName + ".</red>"); return; }
        boolean sharedInventory = sharedInventoryEnabled();
        if (!sharedInventory) saveCurrentProfile(player);
        Location loc;
        if (yaml.getBoolean("spawn.use-world-spawn", true)) loc = world.getSpawnLocation().add(0.5, 0.0, 0.5);
        else loc = new Location(world, yaml.getDouble("spawn.x"), yaml.getDouble("spawn.y"), yaml.getDouble("spawn.z"), (float) yaml.getDouble("spawn.yaw"), (float) yaml.getDouble("spawn.pitch"));
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        player.setGameMode(GameMode.SURVIVAL);
        restoreSurvivalDamageState(player);
        if (!sharedInventory) clearPlayerState(player);
        player.teleport(loc);
        if (!sharedInventory) {
            clearPlayerState(player);
            loadProfile(player, "survival");
        }
        rtpManager.giveItem(player);
        Text.send(player, yaml.getString("messages.teleported", "<green>Sent to survival.</green>"));
    }

    public void saveCurrentProfile(Player player) {
        if (player == null) return;
        if (sharedInventoryEnabled()) return;
        String profile = isSurvivalWorld(player.getWorld()) ? "survival" : "spawn";
        repository.saveInventoryProfile(player.getUniqueId(), profile, player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand());
    }

    public void loadProfile(Player player, String profile) {
        if (player == null) return;
        if (sharedInventoryEnabled()) return;
        var data = repository.loadInventoryProfile(player.getUniqueId(), profile);
        player.getInventory().setContents(data.contents());
        player.getInventory().setArmorContents(data.armor());
        player.getInventory().setItemInOffHand(data.offhand() == null ? new ItemStack(org.bukkit.Material.AIR) : data.offhand());
        player.updateInventory();
        profileLoaded.put(player.getUniqueId(), true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profileLoaded.remove(event.getPlayer().getUniqueId());
        if (isSurvivalWorld(event.getPlayer().getWorld())) {
            Bukkit.getScheduler().runTask(plugin, () -> restoreSurvivalDamageState(event.getPlayer()));
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (sharedInventoryEnabled()) {
            if (isSurvivalWorld(player.getWorld())) {
                if (player.getGameMode() == GameMode.ADVENTURE) player.setGameMode(GameMode.SURVIVAL);
                restoreSurvivalDamageState(player);
                rtpManager.giveItem(player);
            }
            return;
        }
        if (isSurvivalWorld(player.getWorld())) {
            if (player.getGameMode() == GameMode.ADVENTURE) player.setGameMode(GameMode.SURVIVAL);
            restoreSurvivalDamageState(player);
            loadProfile(player, "survival");
            rtpManager.giveItem(player);
        } else {
            loadProfile(player, "spawn");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingPlacementInventorySync.remove(event.getPlayer().getUniqueId());
        saveCurrentProfile(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!isSurvivalWorld(player.getWorld())) return;
        if (player.getRespawnLocation() == null && spawnService != null && spawnService.getSpawnLocation() != null) {
            event.setRespawnLocation(spawnService.getSpawnLocation());
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (sharedInventoryEnabled()) return;
            if (isSurvivalWorld(player.getWorld())) loadProfile(player, "survival");
            else loadProfile(player, "spawn");
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) return;
        if (!isLockedEndWorld(event.getTo().getWorld())) return;
        if (hasEndBypass(event.getPlayer())) return;
        event.setCancelled(true);
        Text.send(event.getPlayer(), endLockMessage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent) return;
        if (event.getTo() == null || event.getTo().getWorld() == null) return;
        if (!isLockedEndWorld(event.getTo().getWorld())) return;
        if (hasEndBypass(event.getPlayer())) return;
        event.setCancelled(true);
        Text.send(event.getPlayer(), endLockMessage());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSurvivalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isSurvivalWorld(player.getWorld())) return;
        if (!player.isInvulnerable()) return;
        restoreSurvivalDamageState(player);
        event.setCancelled(false);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var yaml = configs.get("world/survival.yml");
        if (!yaml.getBoolean("command-lock.enabled", false)) return;
        if (!isSurvivalWorld(event.getPlayer().getWorld())) return;
        for (String permission : yaml.getStringList("command-lock.bypass-permissions")) {
            if (!permission.isBlank() && event.getPlayer().hasPermission(permission)) return;
        }
        String label = event.getMessage().split(" ")[0].toLowerCase(java.util.Locale.ROOT);
        if (label.startsWith("/")) label = label.substring(1);
        String commandLabel = label;
        if (yaml.getStringList("command-lock.allowed").stream().anyMatch(command -> command.equalsIgnoreCase(commandLabel))) {
            return;
        }
        event.setCancelled(true);
        Text.send(event.getPlayer(), yaml.getString("command-lock.deny-message", "<red>That command is disabled in survival.</red>"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCancelledVanillaSurvivalBlockInteract(PlayerInteractEvent event) {
        if (!event.isCancelled() && event.useItemInHand() != Event.Result.DENY && event.useInteractedBlock() != Event.Result.DENY) return;
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) return;
        if (!isVanillaBuildWorld(event.getPlayer().getWorld())) return;
        if (!isNormalPlaceableBlock(event.getItem())) return;
        schedulePlacementInventorySync(event.getPlayer(), event.getItem(), event.getHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCancelledVanillaSurvivalBlockPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) return;
        if (!isVanillaBuildWorld(event.getPlayer().getWorld())) return;
        if (!isNormalPlaceableBlock(event.getItemInHand())) return;
        schedulePlacementInventorySync(event.getPlayer(), event.getItemInHand(), event.getHand());
    }

    private boolean isSurvivalWorld(World world) {
        if (world == null) return false;
        String configured = configs.get("world/survival.yml").getString("world", "world");
        String market = configs.get("world/market.yml").getString("world.name", "market");
        String name = world.getName();
        boolean end = name.equalsIgnoreCase(configured + "_the_end") || name.equalsIgnoreCase("world_the_end");
        return name.equalsIgnoreCase(configured)
                || name.equalsIgnoreCase(configured + "_nether")
                || name.equalsIgnoreCase(market)
                || name.equalsIgnoreCase("world")
                || name.equalsIgnoreCase("world_nether")
                || (end && !endLocked());
    }

    private boolean isVanillaBuildWorld(World world) {
        if (world == null) return false;
        String configured = configs.get("world/survival.yml").getString("world", "world");
        String name = world.getName();
        boolean end = name.equalsIgnoreCase(configured + "_the_end") || name.equalsIgnoreCase("world_the_end");
        return name.equalsIgnoreCase(configured)
                || name.equalsIgnoreCase(configured + "_nether")
                || name.equalsIgnoreCase("world")
                || name.equalsIgnoreCase("world_nether")
                || (end && !endLocked());
    }

    private boolean isNormalPlaceableBlock(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.getType().isBlock()) return false;
        if (!item.hasItemMeta()) return true;
        var meta = item.getItemMeta();
        return meta.getPersistentDataContainer().isEmpty();
    }

    private void schedulePlacementInventorySync(Player player, ItemStack expected, EquipmentSlot hand) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (pendingPlacementInventorySync.putIfAbsent(uuid, Boolean.TRUE) != null) return;
        ItemStack snapshot = expected == null ? null : expected.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingPlacementInventorySync.remove(uuid);
            if (!player.isOnline()) return;
            restoreCancelledPlacementStack(player, snapshot, hand);
            player.updateInventory();
        });
    }

    private void restoreCancelledPlacementStack(Player player, ItemStack expected, EquipmentSlot hand) {
        if (expected == null || expected.getType().isAir() || hand == null) return;
        ItemStack current = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (current == null || current.getType().isAir()) {
            if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(expected);
            else player.getInventory().setItemInMainHand(expected);
            return;
        }
        if (current.isSimilar(expected) && current.getAmount() < expected.getAmount()) {
            current.setAmount(expected.getAmount());
        }
    }

    private void restoreSurvivalDamageState(Player player) {
        if (player == null || !player.isOnline()) return;
        player.setInvulnerable(false);
        player.setNoDamageTicks(0);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    private boolean isLockedEndWorld(World world) {
        if (world == null || !endLocked()) return false;
        String configured = configs.get("world/survival.yml").getString("world", "world");
        String name = world.getName();
        return name.equalsIgnoreCase(configured + "_the_end") || name.equalsIgnoreCase("world_the_end");
    }

    private boolean endLocked() {
        return configs.get("world/survival.yml").getBoolean("end-lock.enabled", true);
    }

    private boolean hasEndBypass(Player player) {
        String permission = configs.get("world/survival.yml").getString("end-lock.bypass-permission", "3smpcore.survival.end.bypass");
        return player.hasPermission(permission)
                || player.hasPermission("3smpcore.command.bypass")
                || player.hasPermission("3smpcore.staff.sradmin")
                || player.hasPermission("3smpcore.staff.admin")
                || player.hasPermission("3smpcore.admin")
                || player.isOp();
    }

    private String endLockMessage() {
        return configs.get("world/survival.yml").getString("end-lock.message", "<red>The End is locked in survival.</red>");
    }

    private void clearPlayerState(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
        player.setItemOnCursor(null);
        player.updateInventory();
    }
}

