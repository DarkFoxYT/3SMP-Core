package net.dark.threecore.dungeons;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class Dunguons3SMP extends JavaPlugin {
    private DungeonManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new DungeonManager(this);
        manager.loadAll();
        DungeonCommand command = new DungeonCommand(manager);
        getServer().getPluginManager().registerEvents(new DungeonListener(manager), this);
        if (getCommand("dungeon") != null) {
            getCommand("dungeon").setExecutor(command);
            getCommand("dungeon").setTabCompleter(command);
        }
        getServer().getScheduler().runTaskTimer(this, manager::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(this, value);
    }
}