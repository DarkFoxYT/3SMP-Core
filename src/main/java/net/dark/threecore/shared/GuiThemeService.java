package net.dark.threecore.shared;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class GuiThemeService {
    private final ConfigFiles configs;

    public GuiThemeService(ConfigFiles configs) {
        this.configs = configs;
    }

    public void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Text.mm(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    public ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        item.setItemMeta(meta);
        return item;
    }

    public String title(String path, String fallback) {
        return configs.get(path).getString("menu.title", fallback);
    }
}
