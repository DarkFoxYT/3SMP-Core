package net.dark.threecore.dungeons;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class DungeonItems {
    private final Dunguons3SMP plugin;
    public static final String MENU_NAME = ChatColor.GOLD + "Dungeon Selector";

    public DungeonItems(Dunguons3SMP plugin) {
        this.plugin = plugin;
    }

    public ItemStack menuItem() {
        ItemStack stack = new ItemStack(Material.COMPASS);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Dungeon Menu");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click to open."));
        meta.getPersistentDataContainer().set(plugin.key("menu_item"), PersistentDataType.BYTE, (byte) 1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isMenuItem(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(plugin.key("menu_item"), PersistentDataType.BYTE);
    }

    public void giveMenuItem(Player player) {
        var inv = player.getInventory();
        int slot = inv.firstEmpty();
        if (slot < 0) slot = 0;
        inv.setItem(slot, menuItem());
    }
}