package net.dark.threecore.market;

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

public final class MarketGui {
    private final MarketPlotManager plotManager;

    public MarketGui(MarketPlotManager plotManager) {
        this.plotManager = plotManager;
    }

    public Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.MARKET_MAIN, "main"), 27, "3SMP Market");
        fill(inv);
        inv.setItem(11, button(Material.COMPASS, "<gradient:#1A2A4A:#f59e0b>Teleport to Plot</gradient>", List.of("<gray>Go to your owned plot.</gray>")));
        inv.setItem(13, button(Material.CHEST, "<gradient:#1A2A4A:#f59e0b>Manage Plot</gradient>", List.of("<gray>View plot info.</gray>")));
        inv.setItem(15, button(Material.EMERALD, "<gradient:#1A2A4A:#f59e0b>Pay Rent</gradient>", List.of("<gray>Pay rent for your plot.</gray>")));
        inv.setItem(22, button(Material.NAME_TAG, "<gradient:#1A2A4A:#f59e0b>Trust Players</gradient>", List.of("<gray>Trust editing comes from commands for now.</gray>")));
        return inv;
    }

    public void handle(Player player, int slot) {
        plotManager.handle(player, slot);
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Text.mm(" "));
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
