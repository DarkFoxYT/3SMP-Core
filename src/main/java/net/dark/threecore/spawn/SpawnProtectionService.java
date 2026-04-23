package net.dark.threecore.spawn;

import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Particle;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SpawnProtectionService implements Listener {
    private final JavaPlugin plugin;
    private net.dark.threecore.config.ConfigFiles configs;
    private final Set<UUID> protectedPlayers = new HashSet<>();

    public SpawnProtectionService(JavaPlugin plugin) { this.plugin = plugin; }
    public SpawnProtectionService(JavaPlugin plugin, net.dark.threecore.config.ConfigFiles configs) { this.plugin = plugin; this.configs = configs; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applySpawnState(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applySpawnState(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> applySpawnState(event.getPlayer()));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!isProtected(event.getTo())) return;
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, true, false, false));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlockPlaced().getLocation()) && !canBuild(event.getPlayer())) deny(event.getPlayer(), event.getBlockPlaced().getLocation(), event);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getLocation()) && !canBuild(event.getPlayer())) deny(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler public void onBucketEmpty(PlayerBucketEmptyEvent event) { if (isProtected(event.getBlockClicked().getLocation()) && !canBuild(event.getPlayer())) deny(event.getPlayer(), event.getBlockClicked().getLocation(), event); }
    @EventHandler public void onBucketFill(PlayerBucketFillEvent event) { if (isProtected(event.getBlockClicked().getLocation()) && !canBuild(event.getPlayer())) deny(event.getPlayer(), event.getBlockClicked().getLocation(), event); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (event.getClickedBlock() != null && isProtected(event.getClickedBlock().getLocation()) && !canBuild(event.getPlayer()) && event.getAction().isRightClick()) { Material type = event.getClickedBlock().getType(); if (type.name().contains("DOOR") || type.name().contains("TRAPDOOR") || type.name().contains("FENCE_GATE") || type.name().contains("BUTTON") || type.name().contains("LEVER") || type.name().contains("PRESSURE_PLATE") || type.name().contains("SIGN") || type.name().contains("CHEST") || type.name().contains("BARREL") || type.name().contains("SHULKER")) deny(event.getPlayer(), event.getClickedBlock().getLocation(), event); } }
    @EventHandler public void onEntityChange(EntityChangeBlockEvent event) { if (isProtected(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler public void onEntityExplode(EntityExplodeEvent event) { event.blockList().removeIf(block -> isProtected(block.getLocation())); }
    @EventHandler public void onBlockExplode(BlockExplodeEvent event) { event.blockList().removeIf(block -> isProtected(block.getLocation())); }
    @EventHandler public void onBurn(BlockBurnEvent event) { if (isProtected(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler public void onIgnite(BlockIgniteEvent event) { if (isProtected(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler public void onFade(BlockFadeEvent event) { if (isProtected(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler public void onGrow(BlockGrowEvent event) { if (isProtected(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler public void onLeaves(LeavesDecayEvent event) { if (isProtected(event.getBlock().getLocation())) event.setCancelled(true); }
    @EventHandler public void onFlow(BlockFromToEvent event) { if (isProtected(event.getBlock().getLocation()) || isProtected(event.getToBlock().getLocation())) event.setCancelled(true); }
    @EventHandler public void onPhysics(BlockPhysicsEvent event) { if (configs != null && configs.get("core/config.yml").getBoolean("spawn.protection.cancel-physics", false) && isProtected(event.getBlock().getLocation())) event.setCancelled(true); }

    public void applySpawnState(Player player) {
        if (!isProtected(player.getLocation())) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    private boolean isProtected(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (configs == null) return isSpawnWorld(loc.getWorld().getName());
        String world = configs.get("core/config.yml").getString("spawn.world", "spawn");
        if (!loc.getWorld().getName().equalsIgnoreCase(world)) return false;
        if (configs.get("core/config.yml").getBoolean("spawn.protection.entire-world", true)) return true;
        double radius = configs.get("core/config.yml").getDouble("spawn.radius", 100.0);
        org.bukkit.Location center = new org.bukkit.Location(loc.getWorld(), configs.get("core/config.yml").getDouble("spawn.location.x", 0.5), configs.get("core/config.yml").getDouble("spawn.location.y", loc.getY()), configs.get("core/config.yml").getDouble("spawn.location.z", 0.5));
        return loc.distanceSquared(center) <= radius * radius;
    }

    private boolean canBuild(Player player) { return player.isOp() || player.hasPermission(configs == null ? "3smpcore.spawn.build" : configs.get("core/config.yml").getString("spawn.protection.bypass-permission", "3smpcore.spawn.build")); }
    private void deny(Player player, org.bukkit.Location location, org.bukkit.event.Cancellable event) { event.setCancelled(true); player.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0.5, 0.5, 0.5), 12, 0.25, 0.25, 0.25, 0.02); Text.send(player, "<red>Spawn world is protected.</red>"); }

    public boolean isSpawnWorld(String worldName) {
        return worldName.equalsIgnoreCase(Bukkit.getWorlds().get(0).getName()) || worldName.equalsIgnoreCase("spawn");
    }
}
