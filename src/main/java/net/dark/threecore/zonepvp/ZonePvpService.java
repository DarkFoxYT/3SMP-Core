package net.dark.threecore.zonepvp;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.zonepvp.worldguard.WorldGuardZoneHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public final class ZonePvpService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final WorldGuardZoneHook worldGuard;
    private final PlayerDataRepository repository;
    private static final Set<UUID> ACTIVE_ZONE_PLAYERS = new HashSet<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Set<String>> upgradedPieces = new HashMap<>();
    private Consumer<Player> cosmeticsItemRefresher = player -> {};

    public ZonePvpService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository) { this.plugin = plugin; this.configs = configs; this.repository = repository; this.worldGuard = new WorldGuardZoneHook(plugin, configs); ensureWorldPvp(); }
    public void setCosmeticsItemRefresher(Consumer<Player> cosmeticsItemRefresher) { this.cosmeticsItemRefresher = cosmeticsItemRefresher == null ? player -> {} : cosmeticsItemRefresher; }
    public void reload() { ensureWorldPvp(); }
    public boolean isActive(Player player) { return player != null && snapshots.containsKey(player.getUniqueId()); }
    public static boolean isZonePlayer(Player player) { return player != null && ACTIVE_ZONE_PLAYERS.contains(player.getUniqueId()); }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (!player.hasPermission("3smpcore.zonepvp.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        if (args.length == 0) { Text.send(player, "<gray>/zonepvp pos1|pos2|respawn|toggle|reload</gray>"); return; }
        var yaml = configs.get("world/zonepvp.yml");
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos1", "pos2" -> { writeLocation(yaml, "zone." + args[0].toLowerCase(Locale.ROOT), player.getLocation()); save(yaml); ensureWorldPvp(); Text.send(player, "<green>ZonePvP " + args[0] + " set.</green>"); }
            case "respawn" -> { writeLocation(yaml, "respawn", player.getLocation()); save(yaml); Text.send(player, "<green>ZonePvP respawn set.</green>"); }
            case "toggle" -> { yaml.set("enabled", !yaml.getBoolean("enabled", true)); save(yaml); Text.send(player, yaml.getBoolean("enabled") ? "<green>ZonePvP enabled.</green>" : "<red>ZonePvP disabled.</red>"); }
            case "reload" -> { configs.reload(); ensureWorldPvp(); Text.send(player, "<green>ZonePvP reloaded.</green>"); }
            default -> Text.send(player, "<gray>/zonepvp pos1|pos2|respawn|toggle|reload</gray>");
        }
    }
    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("pos1", "pos2", "respawn", "toggle", "reload") : List.of(); }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        boolean was = snapshots.containsKey(event.getPlayer().getUniqueId());
        boolean now = inZone(event.getTo());
        if (!was && now && worldGuard.allowsPvp(event.getPlayer())) enter(event.getPlayer());
        else if (was && !now) exit(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.HIGH) public void onFall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && snapshots.containsKey(player.getUniqueId()) && event.getCause() == EntityDamageEvent.DamageCause.FALL) event.setCancelled(true);
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (!snapshots.containsKey(dead.getUniqueId())) return;
        event.getDrops().clear();
        Player killer = dead.getKiller();
        exit(dead, false);
        streaks.remove(dead.getUniqueId());
        Location respawn = readLocation("respawn");
        Bukkit.getScheduler().runTask(plugin, () -> { if (respawn != null) dead.spigot().respawn(); });
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (respawn != null && dead.isOnline()) dead.teleport(respawn); }, 2L);
        if (killer != null && snapshots.containsKey(killer.getUniqueId())) {
            int streak = streaks.merge(killer.getUniqueId(), 1, Integer::sum);
            applyKit(killer, streak);
            rewardKillSupplies(killer);
            rewardUpgradeIfNeeded(killer, streak);
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.35f);
            Text.actionBar(killer, "<gradient:#f59e0b:#f97316>Kill Streak</gradient> <white>" + streak + "</white>");
        }
    }

    private void ensureWorldPvp() {
        if (!configs.get("world/zonepvp.yml").getBoolean("pvp.enabled", true)) return;
        Location pos = readLocation("zone.pos1");
        if (pos != null && pos.getWorld() != null) pos.getWorld().setPVP(true);
        worldGuard.sync(readLocation("zone.pos1"), readLocation("zone.pos2"));
    }
    private void enter(Player player) {
        ACTIVE_ZONE_PLAYERS.add(player.getUniqueId());
        snapshots.put(player.getUniqueId(), Snapshot.capture(player));
        player.setGameMode(GameMode.SURVIVAL);
        clearSpawnBuffs(player);
        streaks.put(player.getUniqueId(), 0);
        upgradedPieces.put(player.getUniqueId(), new HashSet<>());
        applyKit(player, 0);
        Text.send(player, "<green>Entered ZonePvP.</green>");
    }
    private void exit(Player player, boolean restore) {
        ACTIVE_ZONE_PLAYERS.remove(player.getUniqueId());
        Snapshot snap = snapshots.remove(player.getUniqueId());
        streaks.remove(player.getUniqueId());
        upgradedPieces.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        if (restore && snap != null) snap.restoreInventory(player); else player.getInventory().clear();
        restoreSpawnStateIfNeeded(player);
        cosmeticsItemRefresher.accept(player);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        Text.send(player, "<gray>Left ZonePvP.</gray>");
    }
    private void applyKit(Player player, int streak) {
        player.getInventory().clear();
        List<String> items = configs.get("world/zonepvp.yml").getStringList("kits.iron.items");
        for (String item : items) add(player, item);
        ItemStack[] armor = baseArmor(player);
        Set<String> upgraded = upgradedPieces.getOrDefault(player.getUniqueId(), Set.of());
        applyArmorUpgrade(armor, upgraded);
        if (upgraded.contains("sword")) replaceSword(player, Material.DIAMOND_SWORD);
        player.getInventory().setArmorContents(armor);
        player.setExp(Math.min(0.999f, streak / 10.0f));
        player.setLevel(streak);
        player.setHealth(Math.min(20.0, player.getMaxHealth())); player.setFoodLevel(20); player.setSaturation(0);
        Text.actionBar(player, "<gradient:#f59e0b:#f97316>Kill Streak</gradient> <white>" + streak + "</white>");
    }

    private ItemStack[] baseArmor(Player player) {
        List<String> armor = configs.get("world/zonepvp.yml").getStringList("kits.iron.armor");
        ItemStack[] contents = new ItemStack[4];
        for (int i=0;i<armor.size() && i<4;i++) contents[i] = stack(armor.get(i));
        return contents;
    }

    private void rewardKillSupplies(Player player) {
        int arrows = Math.max(0, configs.get("world/zonepvp.yml").getInt("rewards.per-kill.arrow", 1));
        int goldenApples = Math.max(0, configs.get("world/zonepvp.yml").getInt("rewards.per-kill.golden-apple", 1));
        if (arrows > 0) player.getInventory().addItem(new ItemStack(Material.ARROW, arrows));
        if (goldenApples > 0) player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, goldenApples));
    }
    private void rewardUpgradeIfNeeded(Player player, int streak) {
        int interval = Math.max(1, configs.get("world/zonepvp.yml").getInt("upgrade.interval-kills", 10));
        if (streak <= 0 || streak % interval != 0) return;
        Set<String> upgraded = upgradedPieces.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashSet<>());
        List<String> order = new ArrayList<>(configs.get("world/zonepvp.yml").getStringList("upgrade.order"));
        if (order.isEmpty()) order = new ArrayList<>(List.of("boots", "leggings", "chestplate", "helmet", "sword"));
        List<String> remaining = order.stream().filter(piece -> !upgraded.contains(piece)).toList();
        if (remaining.isEmpty()) return;
        String piece = remaining.get(new Random().nextInt(remaining.size()));
        upgraded.add(piece);
        applyKit(player, streak);
        double reward = configs.get("world/zonepvp.yml").getDouble("upgrade.money-reward", 100.0D);
        repository.setMoneyBalance(player.getUniqueId(), repository.getMoneyBalance(player.getUniqueId()) + reward);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        Text.send(player, "<gradient:#f59e0b:#f97316>Zone upgrade!</gradient> <gray>Upgraded:</gray> <white>" + piece + "</white> <gray>+$" + Math.round(reward) + "</gray>");
    }

    private void applyArmorUpgrade(ItemStack[] armor, Set<String> upgraded) {
        if (upgraded.contains("boots")) armor[0] = new ItemStack(Material.DIAMOND_BOOTS);
        if (upgraded.contains("leggings")) armor[1] = new ItemStack(Material.DIAMOND_LEGGINGS);
        if (upgraded.contains("chestplate")) armor[2] = new ItemStack(Material.DIAMOND_CHESTPLATE);
        if (upgraded.contains("helmet")) armor[3] = new ItemStack(Material.DIAMOND_HELMET);
    }

    private void replaceSword(Player player, Material material) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current != null && current.getType().name().endsWith("_SWORD")) { player.getInventory().setItem(i, new ItemStack(material)); return; }
        }
        player.getInventory().addItem(new ItemStack(material));
    }
    private void clearSpawnBuffs(Player player) { player.removePotionEffect(PotionEffectType.SPEED); player.removePotionEffect(PotionEffectType.SATURATION); }
    private void restoreSpawnStateIfNeeded(Player player) {
        clearSpawnBuffs(player);
        if (!inSpawnProtection(player.getLocation())) return;
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, org.bukkit.potion.PotionEffect.INFINITE_DURATION, 1, true, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SATURATION, org.bukkit.potion.PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }
    private boolean inSpawnProtection(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String world = configs.get("core/config.yml").getString("spawn.world", "spawn");
        if (!loc.getWorld().getName().equalsIgnoreCase(world)) return false;
        double radius = configs.get("core/config.yml").getDouble("spawn.radius", 100.0);
        Location center = new Location(loc.getWorld(), configs.get("core/config.yml").getDouble("spawn.location.x", 0.5), loc.getY(), configs.get("core/config.yml").getDouble("spawn.location.z", 0.5));
        return loc.distanceSquared(center) <= radius * radius;
    }
    private void add(Player p, String mat) { ItemStack s = stack(mat); if (s != null) p.getInventory().addItem(s); }
    private ItemStack stack(String mat) { try { return new ItemStack(Material.valueOf(mat.toUpperCase(Locale.ROOT))); } catch(Exception e){ return null; } }

    public boolean inZone(Location loc) {
        if (!configs.get("world/zonepvp.yml").getBoolean("enabled", true) || loc.getWorld() == null) return false;
        Location a = readLocation("zone.pos1"), b = readLocation("zone.pos2");
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(loc.getWorld())) return false;
        return loc.getX() >= Math.min(a.getX(), b.getX()) && loc.getX() <= Math.max(a.getX(), b.getX()) && loc.getY() >= Math.min(a.getY(), b.getY()) && loc.getY() <= Math.max(a.getY(), b.getY()) && loc.getZ() >= Math.min(a.getZ(), b.getZ()) && loc.getZ() <= Math.max(a.getZ(), b.getZ());
    }
    private Location readLocation(String path) { var s = configs.get("world/zonepvp.yml").getConfigurationSection(path); if (s == null) return null; World w = Bukkit.getWorld(s.getString("world", "spawn")); return w == null ? null : new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")); }
    private void writeLocation(org.bukkit.configuration.file.YamlConfiguration y, String p, Location l) { y.set(p+".world", l.getWorld().getName()); y.set(p+".x", l.getX()); y.set(p+".y", l.getY()); y.set(p+".z", l.getZ()); y.set(p+".yaw", l.getYaw()); y.set(p+".pitch", l.getPitch()); }
    private void save(org.bukkit.configuration.file.YamlConfiguration y) { try { y.save(new File(plugin.getDataFolder(), "world/zonepvp.yml")); } catch(Exception ignored){} }
    private record Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, Location location) { static Snapshot capture(Player p){ return new Snapshot(p.getInventory().getContents(), p.getInventory().getArmorContents(), p.getInventory().getItemInOffHand(), p.getLocation()); } void restoreInventory(Player p){ p.getInventory().setContents(contents); p.getInventory().setArmorContents(armor); p.getInventory().setItemInOffHand(offhand); } }
}


