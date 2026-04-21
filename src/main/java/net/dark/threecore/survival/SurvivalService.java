package net.dark.threecore.survival;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.rtp.RtpManager;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SurvivalService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final RtpManager rtpManager;

    public SurvivalService(JavaPlugin plugin, ConfigFiles configs, RtpManager rtpManager) {
        this.plugin = plugin;
        this.configs = configs;
        this.rtpManager = rtpManager;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (args.length > 0 && args[0].equalsIgnoreCase("rtp")) { rtpManager.teleport(player); return; }
        teleport(player);
    }

    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("rtp") : List.of(); }

    public void teleport(Player player) {
        var yaml = configs.get("survival.yml");
        String worldName = yaml.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) { Text.send(player, "<red>Survival world is not loaded: " + worldName + ".</red>"); return; }
        Location loc;
        if (yaml.getBoolean("spawn.use-world-spawn", true)) loc = world.getSpawnLocation().add(0.5, 0.0, 0.5);
        else loc = new Location(world, yaml.getDouble("spawn.x"), yaml.getDouble("spawn.y"), yaml.getDouble("spawn.z"), (float) yaml.getDouble("spawn.yaw"), (float) yaml.getDouble("spawn.pitch"));
        player.teleport(loc);
        Text.send(player, yaml.getString("messages.teleported", "<green>Sent to survival.</green>"));
    }
}
