package net.dark.threecore.rtp;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RtpManager(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void reload() {
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only.</red>");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            Text.send(sender, "<green>RTP reloaded.</green>");
            return;
        }
        teleport(player);
    }

    public boolean teleport(Player player) {
        String worldName = resolveTargetWorld(player);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Text.send(player, "<red>No RTP world configured or loaded:</red> <white>" + worldName + "</white>");
            return false;
        }

        String key = "worlds." + world.getName().toLowerCase(Locale.ROOT);
        long cooldown = configs.get("world/rtp.yml").getLong(key + ".cooldown-seconds", configs.get("world/rtp.yml").getLong("defaults.cooldown-seconds", 300L));
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remaining = cooldown * 1000L - (now - last);
        if (remaining > 0) {
            Text.send(player, "<red>RTP cooldown: " + (remaining / 1000L) + "s.</red>");
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);

        int maxAttempts = configs.get("world/rtp.yml").getInt(key + ".max-attempts", configs.get("world/rtp.yml").getInt("defaults.max-attempts", 20));
        int minRadius = configs.get("world/rtp.yml").getInt(key + ".min-radius", configs.get("world/rtp.yml").getInt("defaults.min-radius", 1000));
        int maxRadius = configs.get("world/rtp.yml").getInt(key + ".max-radius", configs.get("world/rtp.yml").getInt("defaults.max-radius", 5000));

        Text.send(player, "<gray>Searching for a safe survival RTP location...</gray>");
        CompletableFuture
                .supplyAsync(() -> candidates(minRadius, maxRadius, maxAttempts))
                .thenAccept(candidates -> Bukkit.getScheduler().runTask(plugin, () -> finishTeleport(player, world, candidates)));
        return true;
    }

    private String resolveTargetWorld(Player player) {
        String current = player.getWorld().getName().toLowerCase(Locale.ROOT);
        String explicit = configs.get("world/rtp.yml").getString("worlds." + current + ".world", "");
        if (explicit != null && !explicit.isBlank()) return explicit;
        String survivalWorld = configs.get("world/survival.yml").getString("world", "");
        if (survivalWorld != null && !survivalWorld.isBlank()) return survivalWorld;
        String configuredDefault = configs.get("world/rtp.yml").getString("default-world", "");
        if (configuredDefault != null && !configuredDefault.isBlank()) return configuredDefault;
        return player.getWorld().getName();
    }

    private List<int[]> candidates(int minRadius, int maxRadius, int maxAttempts) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int min = Math.max(0, Math.min(minRadius, maxRadius));
        int max = Math.max(min + 1, Math.max(minRadius, maxRadius));
        List<int[]> out = new ArrayList<>(Math.max(1, maxAttempts));
        for (int i = 0; i < maxAttempts; i++) {
            int x = random.nextInt(min, max) * (random.nextBoolean() ? 1 : -1);
            int z = random.nextInt(min, max) * (random.nextBoolean() ? 1 : -1);
            out.add(new int[]{x, z});
        }
        return out;
    }

    private void finishTeleport(Player player, World world, List<int[]> candidates) {
        if (!player.isOnline()) return;
        Location location = findSafe(world, candidates);
        if (location == null) {
            Text.send(player, "<red>Could not find a safe location.</red>");
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(location);
        Text.send(player, "<green>Teleported randomly in survival.</green>");
    }

    private Location findSafe(World world, List<int[]> candidates) {
        for (int[] pair : candidates) {
            int x = pair[0];
            int z = pair[1];
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()) return loc;
        }
        return null;
    }
}



