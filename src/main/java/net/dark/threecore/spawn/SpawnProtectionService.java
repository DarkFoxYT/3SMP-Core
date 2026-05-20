package net.dark.threecore.spawn;

import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!event.getAction().isRightClick()) return;
        if (!isProtected(event.getClickedBlock().getLocation())) return;
        if (canBuild(event.getPlayer())) return;
        if (configs != null && !configs.get("core/config.yml").getBoolean("spawn.protection.block-interactions", true)) return;
        if (isBlockedSpawnInteraction(event.getClickedBlock().getType())) {
            deny(event.getPlayer(), event.getClickedBlock().getLocation(), event);
        }
    }

    @EventHandler
    public void onProtectedSpawnDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isProtected(player.getLocation())) return;
        if (configs != null && !configs.get("core/config.yml").getBoolean("spawn.protection.disable-damage", true)) return;
        event.setCancelled(true);
        player.setRemainingAir(player.getMaximumAir());
        player.setFireTicks(0);
    }

    @EventHandler
    public void onProtectedSpawnPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null) return;
        if (!isProtected(victim.getLocation()) && !isProtected(attacker.getLocation())) return;
        if (configs != null && configs.get("core/config.yml").getBoolean("spawn.protection.pvp", false)) return;
        event.setCancelled(true);
        Text.send(attacker, configs == null
                ? "<red>PvP is disabled in spawn.</red>"
                : configs.get("core/config.yml").getString("spawn.zone.pvp-deny-message", "<red>PvP is disabled in spawn.</red>"));
    }

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
        if (!isProtected(player.getLocation())) {
            if (configs != null && configs.get("core/config.yml").getBoolean("spawn.effects.speed.remove-on-exit", true)) {
                player.removePotionEffect(PotionEffectType.SPEED);
                player.removePotionEffect(PotionEffectType.SATURATION);
            }
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
        player.setRemainingAir(player.getMaximumAir());
        player.setFireTicks(0);
    }

    private boolean isBlockedSpawnInteraction(Material type) {
        String name = type.name();
        return name.contains("DOOR")
                || name.contains("TRAPDOOR")
                || name.contains("FENCE_GATE")
                || name.contains("BUTTON")
                || name.contains("LEVER")
                || name.contains("PRESSURE_PLATE")
                || name.contains("SIGN")
                || name.contains("CHEST")
                || name.contains("BARREL")
                || name.contains("SHULKER")
                || name.contains("SHELF")
                || name.contains("BOOKSHELF")
                || name.equals("LECTERN")
                || name.equals("DECORATED_POT")
                || name.equals("HOPPER")
                || name.equals("DISPENSER")
                || name.equals("DROPPER")
                || name.equals("FURNACE")
                || name.equals("BLAST_FURNACE")
                || name.equals("SMOKER")
                || name.equals("BREWING_STAND")
                || name.equals("ENCHANTING_TABLE")
                || name.equals("ANVIL")
                || name.equals("CHIPPED_ANVIL")
                || name.equals("DAMAGED_ANVIL")
                || name.equals("GRINDSTONE")
                || name.equals("STONECUTTER")
                || name.equals("LOOM")
                || name.equals("CARTOGRAPHY_TABLE")
                || name.equals("SMITHING_TABLE")
                || name.equals("CRAFTING_TABLE")
                || name.equals("BEACON");
    }

    private Player attacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isProtected(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (configs == null) return isSpawnWorld(loc.getWorld().getName());
        if (!configs.get("core/config.yml").getBoolean("spawn.protection.enabled", true)) return false;
        String world = configs.get("core/config.yml").getString("spawn.world", "spawn");
        if (!loc.getWorld().getName().equalsIgnoreCase(world)) return false;
        if (isGameplayBuildWorld(loc.getWorld().getName()) && !configs.get("core/config.yml").getBoolean("spawn.protection.allow-gameplay-world", false)) return false;
        if (configs.get("core/config.yml").getBoolean("spawn.protection.entire-world", false)) return true;
        double radius = configs.get("core/config.yml").getDouble("spawn.radius", 100.0);
        org.bukkit.Location center = new org.bukkit.Location(loc.getWorld(), configs.get("core/config.yml").getDouble("spawn.location.x", 0.5), configs.get("core/config.yml").getDouble("spawn.location.y", loc.getY()), configs.get("core/config.yml").getDouble("spawn.location.z", 0.5));
        return loc.distanceSquared(center) <= radius * radius;
    }

    private boolean isGameplayBuildWorld(String worldName) {
        String survival = configs.get("world/survival.yml").getString("world", "world");
        String market = configs.get("world/market.yml").getString("world.name", "market");
        return worldName.equalsIgnoreCase(survival)
                || worldName.equalsIgnoreCase(survival + "_nether")
                || worldName.equalsIgnoreCase(market)
                || worldName.equalsIgnoreCase("world")
                || worldName.equalsIgnoreCase("world_nether");
    }

    private boolean canBuild(Player player) { return player.isOp() || player.hasPermission(configs == null ? "3smpcore.spawn.build" : configs.get("core/config.yml").getString("spawn.protection.bypass-permission", "3smpcore.spawn.build")); }
    private void deny(Player player, org.bukkit.Location location, org.bukkit.event.Cancellable event) { event.setCancelled(true); player.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0.5, 0.5, 0.5), 12, 0.25, 0.25, 0.25, 0.02); Text.send(player, "<red>Spawn world is protected.</red>"); }

    public boolean isSpawnWorld(String worldName) {
        return worldName.equalsIgnoreCase(Bukkit.getWorlds().get(0).getName()) || worldName.equalsIgnoreCase("spawn");
    }
}
