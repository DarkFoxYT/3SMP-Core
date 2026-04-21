package net.dark.threecore.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuService {
    private final JavaPlugin plugin;

    public MenuService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Inventory inventory) {
        player.openInventory(inventory);
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
