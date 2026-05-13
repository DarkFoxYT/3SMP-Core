package net.dark.threecore.dungeons.runtime;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class DungeonDoorManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final List<DungeonDoor> doors = new ArrayList<>();

    public DungeonDoorManager(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public DungeonDoor create(Location location, int width, int height) {
        YamlConfiguration yaml = configs.get("dungeons/dungeons.yml");
        Material material = Material.matchMaterial(yaml.getString("dungeon-door.material", "POLISHED_BLACKSTONE"));
        DungeonDoor door = new BlockDungeonDoor(
            plugin,
            location,
            material == null ? Material.POLISHED_BLACKSTONE : material,
            width,
            height,
            yaml.getInt("dungeon-door.slide-distance-blocks", 4),
            yaml.getInt("dungeon-door.slide-duration-ticks", 40),
            yaml.getBoolean("dungeon-door.particles", true)
        );
        doors.add(door);
        return door;
    }

    public void openAll() {
        doors.forEach(DungeonDoor::open);
    }

    public void closeAll() {
        doors.forEach(DungeonDoor::close);
    }

    public void cleanup() {
        doors.forEach(DungeonDoor::close);
        doors.clear();
    }

    public List<DungeonDoor> doors() {
        return List.copyOf(doors);
    }
}
