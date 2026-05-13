package net.dark.threecore.dungeons.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemsAdderHook {
    private final JavaPlugin plugin;

    public ItemsAdderHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public ItemStack item(String id, Material fallback) {
        if (available() && id != null && !id.isBlank()) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = customStack.getMethod("getInstance", String.class).invoke(null, id);
                if (stack != null) {
                    Object item = stack.getClass().getMethod("getItemStack").invoke(stack);
                    if (item instanceof ItemStack itemStack) return itemStack;
                }
            } catch (Throwable ex) {
                plugin.getLogger().fine("ItemsAdder item lookup failed for " + id + ": " + ex.getMessage());
            }
        }
        return new ItemStack(fallback == null ? Material.STONE : fallback);
    }
}
