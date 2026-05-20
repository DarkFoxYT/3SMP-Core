package net.dark.threecore.essentials;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BackLocationService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, Location> previousLocations = new HashMap<>();

    public BackLocationService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void handle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only.</red>");
            return;
        }
        if (!player.hasPermission("3smpcore.back.use")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (!isSurvivalLike(player.getWorld()) && !hasBypass(player)) {
            Text.send(player, "<red>/back can only be used from survival or market worlds.</red>");
            return;
        }
        Location target = previousLocations.get(player.getUniqueId());
        if (target == null || target.getWorld() == null) {
            Text.send(player, "<red>No previous location saved.</red>");
            return;
        }
        if (!isSurvivalLike(target.getWorld()) && !hasBypass(player)) {
            Text.send(player, "<red>Your saved /back location is no longer allowed.</red>");
            previousLocations.remove(player.getUniqueId());
            return;
        }
        Location current = player.getLocation().clone();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        player.teleport(target);
        previousLocations.put(player.getUniqueId(), current);
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Returned to your previous location.</gradient>");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !player.isOnline()) return;
        if (!isMeaningfulMove(from, to)) return;
        if (!isSurvivalLike(from.getWorld())) return;
        if (DuelService.isDuelPlayer(player)) return;
        previousLocations.put(player.getUniqueId(), from.clone());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isSurvivalLike(player.getWorld())) return;
        previousLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    private boolean isMeaningfulMove(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) return false;
        if (!from.getWorld().getUID().equals(to.getWorld().getUID())) return true;
        return from.distanceSquared(to) > 16.0D;
    }

    private boolean isSurvivalLike(World world) {
        if (world == null) return false;
        String name = world.getName().toLowerCase(Locale.ROOT);
        String base = configs.get("world/survival.yml").getString("world", "world").toLowerCase(Locale.ROOT);
        String market = configs.get("world/market.yml").getString("world.name", "market").toLowerCase(Locale.ROOT);
        boolean end = name.equals(base + "_the_end") || name.equals("world_the_end");
        return name.equals(base)
                || name.equals(base + "_nether")
                || name.equals(market)
                || (end && !endLocked())
                || (Bukkit.getWorld(base) == null && name.equals("world"));
    }

    private boolean endLocked() {
        return configs.get("world/survival.yml").getBoolean("end-lock.enabled", true);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("3smpcore.command.bypass") || player.hasPermission("3smpcore.staff.sradmin") || player.hasPermission("3smpcore.admin");
    }
}
