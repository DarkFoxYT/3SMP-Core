package net.dark.threecore.market;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public final class MarketWorldManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public MarketWorldManager(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public World ensureWorld() {
        String name = worldName();
        World world = Bukkit.getWorld(name);
        if (world != null) return world;
        WorldCreator creator = new WorldCreator(name);
        String generator = configs.get("world/market.yml").getString("world.generator", "VoidGen");
        if (generator != null && !generator.isBlank()) creator.generator(generator);
        creator.generateStructures(false);
        world = Bukkit.createWorld(creator);
        if (world != null) {
            world.setAutoSave(false);
            world.setPVP(false);
            setGameRule(world, "doMobSpawning", false);
            setGameRule(world, "doWeatherCycle", false);
            world.setSpawnLocation(0, configs.get("world/market.yml").getInt("world.spawn.y", 80), 0);
            register(world);
        }
        return world;
    }

    public String worldName() {
        return configs.get("world/market.yml").getString("world.name", "market");
    }

    private void register(World world) {
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) return;
        if (!configs.get("world/market.yml").getBoolean("world.multiverse.register", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + world.getName() + " normal");
                if (configs.get("world/market.yml").getBoolean("world.multiverse.hide", true)) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set hidden true");
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set pvp false");
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to register market world: " + ex.getMessage());
            }
        });
    }

    private <T> void setGameRule(World world, String ruleName, T value) {
        @SuppressWarnings("unchecked")
        GameRule<T> rule = (GameRule<T>) org.bukkit.Registry.GAME_RULE.get(org.bukkit.NamespacedKey.minecraft(toSnakeCase(ruleName)));
        if (rule != null) world.setGameRule(rule, value);
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }
}
