package net.dark.threecore.market;

import net.dark.threecore.text.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MarketProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final MarketStorage storage;
    private final MarketWorldManager worldManager;

    public MarketProtectionListener(JavaPlugin plugin, MarketStorage storage, MarketWorldManager worldManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.worldManager = worldManager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You can only build inside your owned market plot.</red>");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>You can only build inside your owned market plot.</red>");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!isMarketWorld(event.getClickedBlock().getLocation())) return;
        Material type = event.getClickedBlock().getType();
        if (type.name().contains("DOOR") || type.name().contains("TRAPDOOR") || type.name().contains("BUTTON") || type.name().contains("LEVER") || type.name().contains("CHEST") || type.name().contains("SIGN")) {
            if (!canBuild(event.getPlayer(), event.getClickedBlock().getLocation()) && !isOwned(event.getPlayer(), event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    private boolean canBuild(Player player, Location location) {
        if (player.isOp() || player.hasPermission("3smpcore.market.admin")) return true;
        if (!isMarketWorld(location)) return true;
        MarketPlot plot = plot(location);
        return plot != null && (player.getUniqueId().equals(plot.owner()) || plot.trusted().contains(player.getUniqueId()));
    }

    private boolean isOwned(Player player, Location location) {
        MarketPlot plot = plot(location);
        return plot != null && (player.getUniqueId().equals(plot.owner()) || plot.trusted().contains(player.getUniqueId()));
    }

    private boolean isMarketWorld(Location location) {
        return location != null && location.getWorld() != null && location.getWorld().getName().equalsIgnoreCase(worldManager.worldName());
    }

    private MarketPlot plot(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (MarketPlot plot : storage.loadAll()) {
            if (!plot.world().equalsIgnoreCase(location.getWorld().getName())) continue;
            double minX = Math.min(plot.pos1x(), plot.pos2x());
            double maxX = Math.max(plot.pos1x(), plot.pos2x());
            double minY = Math.min(plot.pos1y(), plot.pos2y());
            double maxY = Math.max(plot.pos1y(), plot.pos2y());
            double minZ = Math.min(plot.pos1z(), plot.pos2z());
            double maxZ = Math.max(plot.pos1z(), plot.pos2z());
            if (location.getX() >= minX && location.getX() <= maxX && location.getY() >= minY && location.getY() <= maxY && location.getZ() >= minZ && location.getZ() <= maxZ) return plot;
        }
        return null;
    }
}
