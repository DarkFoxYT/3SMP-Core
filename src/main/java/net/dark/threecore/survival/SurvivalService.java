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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
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
    private SpawnService spawnService;

    public SurvivalService(JavaPlugin plugin, ConfigFiles configs, RtpManager rtpManager, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.configs = configs;
        this.rtpManager = rtpManager;
        this.repository = repository;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("rtp")) { rtpManager.teleport(player); return; }
        teleport(player);
    }

    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("rtp") : List.of(); }
    public void setSpawnService(SpawnService spawnService) { this.spawnService = spawnService; }

    public void teleport(Player player) {
        saveCurrentProfile(player);
        var yaml = configs.get("world/survival.yml");
        String worldName = yaml.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) { Text.send(player, "<red>Survival world is not loaded: " + worldName + ".</red>"); return; }
        Location loc;
        if (yaml.getBoolean("spawn.use-world-spawn", true)) loc = world.getSpawnLocation().add(0.5, 0.0, 0.5);
        else loc = new Location(world, yaml.getDouble("spawn.x"), yaml.getDouble("spawn.y"), yaml.getDouble("spawn.z"), (float) yaml.getDouble("spawn.yaw"), (float) yaml.getDouble("spawn.pitch"));
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        player.setGameMode(GameMode.SURVIVAL);
        clearPlayerState(player);
        player.teleport(loc);
        clearPlayerState(player);
        loadProfile(player, "survival");
        rtpManager.giveItem(player);
        Text.send(player, yaml.getString("messages.teleported", "<green>Sent to survival.</green>"));
    }

    public void saveCurrentProfile(Player player) {
        if (player == null) return;
        String profile = isSurvivalWorld(player.getWorld()) ? "survival" : "spawn";
        repository.saveInventoryProfile(player.getUniqueId(), profile, player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand());
    }

    public void loadProfile(Player player, String profile) {
        if (player == null) return;
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
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isSurvivalWorld(player.getWorld())) {
            loadProfile(player, "survival");
            rtpManager.giveItem(player);
        } else {
            loadProfile(player, "spawn");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
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
            if (isSurvivalWorld(player.getWorld())) loadProfile(player, "survival");
            else loadProfile(player, "spawn");
        });
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isSurvivalWorld(event.getPlayer().getWorld())) return;
        String label = event.getMessage().split(" ")[0].toLowerCase(java.util.Locale.ROOT);
        if (label.startsWith("/")) label = label.substring(1);
        if (label.equals("3smpcore")) return;
        if (label.equals("help") || label.equals("?")) return;
        if (label.equals("spawn") || label.equals("survival") || label.equals("rtp") || label.equals("market") || label.equals("warp")
                || label.equals("party") || label.equals("p") || label.equals("sapphire") || label.equals("sapphires") || label.equals("sap")
                || label.equals("gem") || label.equals("gems") || label.equals("souls") || label.equals("bal") || label.equals("balance")
                || label.equals("pay") || label.equals("shop") || label.equals("ah") || label.equals("auction") || label.equals("auctionhouse")) {
            return;
        }
        event.setCancelled(true);
        Text.send(event.getPlayer(), "<red>That command is disabled in survival.</red>");
    }

    private boolean isSurvivalWorld(World world) {
        if (world == null) return false;
        String configured = configs.get("world/survival.yml").getString("world", "world");
        String market = configs.get("world/market.yml").getString("world.name", "market");
        String name = world.getName();
        return name.equalsIgnoreCase(configured)
                || name.equalsIgnoreCase(configured + "_nether")
                || name.equalsIgnoreCase(configured + "_the_end")
                || name.equalsIgnoreCase(market)
                || name.equalsIgnoreCase("world")
                || name.equalsIgnoreCase("world_nether")
                || name.equalsIgnoreCase("world_the_end");
    }

    private void clearPlayerState(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(org.bukkit.Material.AIR));
        player.setItemOnCursor(null);
        player.updateInventory();
    }
}

