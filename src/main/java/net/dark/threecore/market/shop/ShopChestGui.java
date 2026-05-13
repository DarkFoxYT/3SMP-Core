package net.dark.threecore.market.shop;

import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ShopChestGui {
    public Inventory buildSettings(ShopChestData data) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SHOP_CHEST, "settings:" + key(data)), 27, "Shop Settings");
        fill(inv);
        inv.setItem(11, button(Material.ITEM_FRAME, "<gradient:#f4cd2a:#eda323:#d28d0d>Set Item</gradient>", List.of("<gray>Use your held item.</gray>", "<gray>Current:</gray> <white>" + data.itemName() + "</white>")));
        inv.setItem(13, button(Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>Set Price</gradient>", List.of("<gray>Current:</gray> <white>" + data.price() + "</white>")));
        inv.setItem(15, button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Set Quantity</gradient>", List.of("<gray>Current:</gray> <white>" + data.quantity() + "</white>")));
        inv.setItem(22, button(data.enabled() ? Material.LIME_WOOL : Material.RED_WOOL, data.enabled() ? "<green>Enabled</green>" : "<red>Disabled</red>", List.of("<gray>Toggle shop state.</gray>")));
        return inv;
    }

    public Inventory buildBuy(ShopChestData data, int stock) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SHOP_CHEST, "buy:" + key(data)), 27, "Buy from Shop");
        fill(inv);
        inv.setItem(13, button(Material.valueOf(data.itemType()), "<gradient:#f4cd2a:#eda323:#d28d0d>" + data.itemName() + "</gradient>", List.of("<gray>Price per unit:</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + data.price() + "</gradient>", "<gray>Stock:</gray> <white>" + stock + "</white>", "<gray>Owner:</gray> <white>" + (data.owner() == null ? "None" : data.owner().toString()) + "</white>")));
        inv.setItem(22, button(Material.EMERALD, "<green>Purchase</green>", List.of("<gray>Buy one stack according to quantity.</gray>")));
        return inv;
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Text.mm(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String key(ShopChestData data) {
        return data.world() + ":" + data.x() + ":" + data.y() + ":" + data.z();
    }
}
