package net.dark.threecore.rtp;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    public RtpManager(JavaPlugin plugin, ConfigFiles configs) { this.plugin = plugin; this.configs = configs; }
    public void reload() { }
    public void handle(CommandSender sender, String[] args) { if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; } if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { Text.send(sender, "<green>RTP reloaded.</green>"); return; } teleport(player); }
    public boolean teleport(Player player) { String worldName = configs.get("rtp.yml").getString("worlds." + player.getWorld().getName().toLowerCase(Locale.ROOT) + ".world", player.getWorld().getName()); World world = Bukkit.getWorld(worldName); if (world == null) { Text.send(player, "<red>No RTP world configured.</red>"); return false; } long cooldown = configs.get("rtp.yml").getLong("worlds." + world.getName().toLowerCase(Locale.ROOT) + ".cooldown-seconds", 300L); long now = System.currentTimeMillis(); long last = cooldowns.getOrDefault(player.getUniqueId(), 0L); long remaining = cooldown * 1000L - (now - last); if (remaining > 0) { Text.send(player, "<red>RTP cooldown: " + (remaining / 1000L) + "s.</red>"); return false; } cooldowns.put(player.getUniqueId(), now); int maxAttempts = configs.get("rtp.yml").getInt("worlds." + world.getName().toLowerCase(Locale.ROOT) + ".max-attempts", 20); int minRadius = configs.get("rtp.yml").getInt("worlds." + world.getName().toLowerCase(Locale.ROOT) + ".min-radius", 1000); int maxRadius = configs.get("rtp.yml").getInt("worlds." + world.getName().toLowerCase(Locale.ROOT) + ".max-radius", 5000); CompletableFuture.supplyAsync(() -> findSafe(world, minRadius, maxRadius, maxAttempts)).thenAccept(loc -> Bukkit.getScheduler().runTask(plugin, () -> { if (loc == null) Text.send(player, "<red>Could not find a safe location.</red>"); else { player.teleport(loc); Text.send(player, "<green>Teleported randomly.</green>"); } })); return true; }
    private Location findSafe(World world, int minRadius, int maxRadius, int maxAttempts) { ThreadLocalRandom random = ThreadLocalRandom.current(); for (int i = 0; i < maxAttempts; i++) { int x = random.nextInt(minRadius, maxRadius) * (random.nextBoolean() ? 1 : -1); int z = random.nextInt(minRadius, maxRadius) * (random.nextBoolean() ? 1 : -1); int y = world.getHighestBlockYAt(x, z) + 1; Location loc = new Location(world, x + 0.5, y, z + 0.5); if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()) return loc; } return null; }
}
