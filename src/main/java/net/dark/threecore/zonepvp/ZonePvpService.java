package net.dark.threecore.zonepvp;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.zonepvp.worldguard.WorldGuardZoneHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.enchantments.Enchantment;
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
    private final PartyService partyService;
    private static final Set<UUID> ACTIVE_ZONE_PLAYERS = new HashSet<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Set<String>> upgradedPieces = new HashMap<>();
    private Consumer<Player> cosmeticsItemRefresher = player -> {};
    private int particleTaskId = -1;

    public ZonePvpService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, PartyService partyService) { this.plugin = plugin; this.configs = configs; this.repository = repository; this.partyService = partyService; this.worldGuard = new WorldGuardZoneHook(plugin, configs); ensureWorldPvp(); }
    public void setCosmeticsItemRefresher(Consumer<Player> cosmeticsItemRefresher) { this.cosmeticsItemRefresher = cosmeticsItemRefresher == null ? player -> {} : cosmeticsItemRefresher; }
    public void reload() { ensureWorldPvp(); startParticles(); }
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPartyDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!snapshots.containsKey(victim.getUniqueId())) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null || !snapshots.containsKey(attacker.getUniqueId())) return;
        if (partyService == null) return;
        UUID victimLeader = partyService.partyLeader(victim.getUniqueId());
        if (victimLeader != null && victimLeader.equals(partyService.partyLeader(attacker.getUniqueId()))) {
            event.setCancelled(true);
            Text.actionBar(attacker, "<yellow>You cannot attack party members in ZonePvP.</yellow>");
        }
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
            Text.actionBar(killer, "<gradient:#f4cd2a:#eda323:#d28d0d>Kill Streak</gradient> <white>" + streak + "</white>");
        }
    }

    private void ensureWorldPvp() {
        if (!configs.get("world/zonepvp.yml").getBoolean("pvp.enabled", true)) return;
        Location pos = readLocation("zone.pos1");
        if (pos != null && pos.getWorld() != null) pos.getWorld().setPVP(true);
        worldGuard.sync(readLocation("zone.pos1"), readLocation("zone.pos2"));
    }
    private void enter(Player player) {
        startParticles();
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
        if (restore && snap != null) snap.restoreInventory(player); else { player.getInventory().clear(); player.setGlowing(false); }
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
        if (streak >= netheriteStreak()) {
            armor[0] = new ItemStack(Material.NETHERITE_BOOTS);
            armor[1] = new ItemStack(Material.NETHERITE_LEGGINGS);
            armor[2] = new ItemStack(Material.NETHERITE_CHESTPLATE);
            armor[3] = new ItemStack(Material.NETHERITE_HELMET);
            replaceAllSwords(player, Material.NETHERITE_SWORD);
        } else if (upgraded.contains("sword")) replaceSword(player, Material.DIAMOND_SWORD);
        int enchantLevel = zoneEnchantLevel(streak);
        if (enchantLevel > 0) {
            enchantArmor(armor, enchantLevel);
            enchantWeapon(player, enchantLevel);
        }
        player.getInventory().setArmorContents(armor);
        restackGoldenApples(player, streak);
        player.setExp(Math.min(0.999f, streak / 10.0f));
        player.setLevel(streak);
        player.setHealth(Math.min(20.0, player.getMaxHealth())); player.setFoodLevel(20); player.setSaturation(0);
        Text.actionBar(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Kill Streak</gradient> <white>" + streak + "</white>");
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
        String piece;
        if (streak >= netheriteStreak()) {
            upgraded.addAll(order);
            piece = "netherite kit";
        } else {
            if (remaining.isEmpty()) return;
            piece = remaining.get(new Random().nextInt(remaining.size()));
            upgraded.add(piece);
        }
        applyKit(player, streak);
        double reward = configs.get("world/zonepvp.yml").getDouble("upgrade.money-reward", 100.0D);
        repository.setMoneyBalance(player.getUniqueId(), repository.getMoneyBalance(player.getUniqueId()) + reward);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Zone upgrade!</gradient> <gray>Upgraded:</gray> <white>" + piece + "</white> <gray>+$" + Math.round(reward) + "</gray>");
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

    private void replaceAllSwords(Player player, Material material) {
        boolean replaced = false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current == null || !current.getType().name().endsWith("_SWORD")) continue;
            ItemStack upgraded = new ItemStack(material, Math.max(1, current.getAmount()));
            player.getInventory().setItem(i, upgraded);
            replaced = true;
        }
        if (!replaced) player.getInventory().addItem(new ItemStack(material));
    }

    private void startParticles() {
        if (particleTaskId != -1) return;
        particleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tickParticles, 10L, Math.max(1L, configs.get("world/zonepvp.yml").getLong("particles.interval-ticks", 10L)));
    }

    private void tickParticles() {
        if (snapshots.isEmpty()) return;
        UUID leader = topStreakPlayer();
        for (UUID uuid : new ArrayList<>(snapshots.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            int streak = streaks.getOrDefault(uuid, 0);
            boolean leading = uuid.equals(leader) && streak > 0;
            player.setGlowing(leading);
            if (leading) spawnLeaderGlow(player, streak);
            if (streak >= netheriteStreak()) spawnNetheriteAura(player);
        }
    }

    private UUID topStreakPlayer() {
        UUID best = null;
        int bestStreak = 0;
        for (UUID uuid : snapshots.keySet()) {
            int streak = streaks.getOrDefault(uuid, 0);
            if (streak > bestStreak) {
                best = uuid;
                bestStreak = streak;
            }
        }
        return best;
    }

    private void spawnLeaderGlow(Player player, int streak) {
        double progress = Math.min(1.0D, Math.max(0.0D, streak / (double) netheriteStreak()));
        int red = 250;
        int green = (int) Math.round(204.0D * (1.0D - progress) + 68.0D * progress);
        int blue = (int) Math.round(21.0D * (1.0D - progress) + 68.0D * progress);
        Location center = player.getLocation().add(0.0D, 1.15D, 0.0D);
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(red, green, blue), 1.25F);
        player.getWorld().spawnParticle(org.bukkit.Particle.DUST, center, 12, 0.42D, 0.75D, 0.42D, 0.0D, dust);
        player.getWorld().spawnParticle(org.bukkit.Particle.GLOW, center, 3, 0.35D, 0.55D, 0.35D, 0.01D);
    }

    private void spawnNetheriteAura(Player player) {
        Location center = player.getLocation().add(0.0D, 1.0D, 0.0D);
        player.getWorld().spawnParticle(org.bukkit.Particle.DUST, center, 8, 0.45D, 0.75D, 0.45D, 0.0D, new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(239, 68, 68), 1.15F));
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIMSON_SPORE, center, 5, 0.35D, 0.6D, 0.35D, 0.01D);
    }

    private void restackGoldenApples(Player player, int streak) {
        int base = countConfiguredMaterial("kits.iron.items", Material.GOLDEN_APPLE);
        int perKill = Math.max(0, configs.get("world/zonepvp.yml").getInt("rewards.per-kill.golden-apple", 1));
        int amount = Math.max(0, base + (streak * perKill));
        removeMaterial(player, Material.GOLDEN_APPLE);
        while (amount > 0) {
            int stack = Math.min(64, amount);
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, stack));
            amount -= stack;
        }
    }

    private int countConfiguredMaterial(String path, Material material) {
        int amount = 0;
        for (String raw : configs.get("world/zonepvp.yml").getStringList(path)) {
            try {
                if (Material.valueOf(raw.toUpperCase(Locale.ROOT)) == material) amount++;
            } catch (Exception ignored) {}
        }
        return amount;
    }

    private void removeMaterial(Player player, Material material) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current != null && current.getType() == material) player.getInventory().setItem(i, null);
        }
    }

    private int netheriteStreak() {
        return Math.max(1, configs.get("world/zonepvp.yml").getInt("upgrade.netherite-at-kills", 100));
    }

    private int zoneEnchantLevel(int streak) {
        int netheriteAt = netheriteStreak();
        if (streak < netheriteAt) return 0;
        int interval = Math.max(1, configs.get("world/zonepvp.yml").getInt("upgrade.enchant-interval-kills", 10));
        int max = Math.max(1, configs.get("world/zonepvp.yml").getInt("upgrade.max-enchant-level", 5));
        return Math.min(max, 1 + Math.max(0, streak - netheriteAt) / interval);
    }

    private void enchantArmor(ItemStack[] armor, int level) {
        Enchantment protection = enchantment("protection");
        Enchantment unbreaking = enchantment("unbreaking");
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType() == Material.AIR) continue;
            if (protection != null) piece.addUnsafeEnchantment(protection, level);
            if (unbreaking != null) piece.addUnsafeEnchantment(unbreaking, Math.min(3, level));
        }
    }

    private void enchantWeapon(Player player, int level) {
        Enchantment sharpness = enchantment("sharpness");
        Enchantment power = enchantment("power");
        Enchantment unbreaking = enchantment("unbreaking");
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType().name().endsWith("_SWORD") && sharpness != null) item.addUnsafeEnchantment(sharpness, level);
            if (item.getType() == Material.BOW && power != null) item.addUnsafeEnchantment(power, level);
            if ((item.getType().name().endsWith("_SWORD") || item.getType() == Material.BOW) && unbreaking != null) item.addUnsafeEnchantment(unbreaking, Math.min(3, level));
        }
    }

    private Enchantment enchantment(String key) {
        try {
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
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
    private record Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, Location location, boolean glowing) { static Snapshot capture(Player p){ return new Snapshot(p.getInventory().getContents(), p.getInventory().getArmorContents(), p.getInventory().getItemInOffHand(), p.getLocation(), p.isGlowing()); } void restoreInventory(Player p){ p.getInventory().setContents(contents); p.getInventory().setArmorContents(armor); p.getInventory().setItemInOffHand(offhand); p.setGlowing(glowing); } }
}


