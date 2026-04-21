package net.dark.threecore.zonepvp;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

public final class ZonePvpService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public ZonePvpService(JavaPlugin plugin, ConfigFiles configs) { this.plugin = plugin; this.configs = configs; }
    public void reload() { }
    public boolean isActive(Player player) { return player != null && snapshots.containsKey(player.getUniqueId()); }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (!player.hasPermission("3smpcore.zonepvp.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        if (args.length == 0) { Text.send(player, "<gray>/zonepvp pos1|pos2|respawn|toggle|reload</gray>"); return; }
        var yaml = configs.get("zonepvp.yml");
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos1", "pos2" -> { writeLocation(yaml, "zone." + args[0].toLowerCase(Locale.ROOT), player.getLocation()); save(yaml); Text.send(player, "<green>ZonePvP " + args[0] + " set.</green>"); }
            case "respawn" -> { writeLocation(yaml, "respawn", player.getLocation()); save(yaml); Text.send(player, "<green>ZonePvP respawn set.</green>"); }
            case "toggle" -> { yaml.set("enabled", !yaml.getBoolean("enabled", true)); save(yaml); Text.send(player, yaml.getBoolean("enabled") ? "<green>ZonePvP enabled.</green>" : "<red>ZonePvP disabled.</red>"); }
            case "reload" -> { configs.reload(); Text.send(player, "<green>ZonePvP reloaded.</green>"); }
            default -> Text.send(player, "<gray>/zonepvp pos1|pos2|respawn|toggle|reload</gray>");
        }
    }
    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("pos1", "pos2", "respawn", "toggle", "reload") : List.of(); }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        boolean was = snapshots.containsKey(event.getPlayer().getUniqueId());
        boolean now = inZone(event.getTo());
        if (!was && now) enter(event.getPlayer());
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
            Text.send(killer, "<gradient:#f59e0b:#f97316>Zone streak:</gradient> <white>" + streak + "</white>");
        }
    }

    private void enter(Player player) {
        snapshots.put(player.getUniqueId(), Snapshot.capture(player));
        clearSpawnBuffs(player);
        streaks.put(player.getUniqueId(), 0);
        applyKit(player, 0);
        Text.send(player, "<green>Entered ZonePvP.</green>");
    }
    private void exit(Player player, boolean restore) {
        Snapshot snap = snapshots.remove(player.getUniqueId());
        streaks.remove(player.getUniqueId());
        if (restore && snap != null) snap.restore(player); else player.getInventory().clear();
        clearSpawnBuffs(player);
        Text.send(player, "<gray>Left ZonePvP.</gray>");
    }
    private void applyKit(Player player, int streak) {
        player.getInventory().clear();
        String tier = streak >= configs.get("zonepvp.yml").getInt("upgrade.final-streak", 25) ? "diamond" : "iron";
        List<String> items = configs.get("zonepvp.yml").getStringList("kits." + tier + ".items");
        for (String item : items) add(player, item);
        List<String> armor = configs.get("zonepvp.yml").getStringList("kits." + tier + ".armor");
        ItemStack[] contents = new ItemStack[4];
        for (int i=0;i<armor.size() && i<4;i++) contents[i] = stack(armor.get(i));
        player.getInventory().setArmorContents(contents);
        player.setHealth(Math.min(20.0, player.getMaxHealth())); player.setFoodLevel(20); player.setSaturation(8);
    }
    private void clearSpawnBuffs(Player player) { player.removePotionEffect(PotionEffectType.SPEED); player.removePotionEffect(PotionEffectType.SATURATION); }
    private void add(Player p, String mat) { ItemStack s = stack(mat); if (s != null) p.getInventory().addItem(s); }
    private ItemStack stack(String mat) { try { return new ItemStack(Material.valueOf(mat.toUpperCase(Locale.ROOT))); } catch(Exception e){ return null; } }

    public boolean inZone(Location loc) {
        if (!configs.get("zonepvp.yml").getBoolean("enabled", true) || loc.getWorld() == null) return false;
        Location a = readLocation("zone.pos1"), b = readLocation("zone.pos2");
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(loc.getWorld())) return false;
        return loc.getX() >= Math.min(a.getX(), b.getX()) && loc.getX() <= Math.max(a.getX(), b.getX()) && loc.getY() >= Math.min(a.getY(), b.getY()) && loc.getY() <= Math.max(a.getY(), b.getY()) && loc.getZ() >= Math.min(a.getZ(), b.getZ()) && loc.getZ() <= Math.max(a.getZ(), b.getZ());
    }
    private Location readLocation(String path) { var s = configs.get("zonepvp.yml").getConfigurationSection(path); if (s == null) return null; World w = Bukkit.getWorld(s.getString("world", "spawn")); return w == null ? null : new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")); }
    private void writeLocation(org.bukkit.configuration.file.YamlConfiguration y, String p, Location l) { y.set(p+".world", l.getWorld().getName()); y.set(p+".x", l.getX()); y.set(p+".y", l.getY()); y.set(p+".z", l.getZ()); y.set(p+".yaw", l.getYaw()); y.set(p+".pitch", l.getPitch()); }
    private void save(org.bukkit.configuration.file.YamlConfiguration y) { try { y.save(new File(plugin.getDataFolder(), "zonepvp.yml")); } catch(Exception ignored){} }
    private record Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, Location location) { static Snapshot capture(Player p){ return new Snapshot(p.getInventory().getContents(), p.getInventory().getArmorContents(), p.getInventory().getItemInOffHand(), p.getLocation()); } void restore(Player p){ p.getInventory().setContents(contents); p.getInventory().setArmorContents(armor); p.getInventory().setItemInOffHand(offhand); p.teleport(location); } }
}
