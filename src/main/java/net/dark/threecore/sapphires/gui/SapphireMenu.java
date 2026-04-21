package net.dark.threecore.sapphires.gui;

import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.sapphires.SapphireService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class SapphireMenu {
    private final SapphireService service;

    public SapphireMenu(SapphireService service) {
        this.service = service;
    }

    public Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "main"), 27, "3SMP Sapphire Shop");
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(7, button(Material.BOOK, "<gradient:#1A2A4A:#D6E8F7>Summary</gradient>", List.of("<gray>View your balance and configured shop link.</gray>")));
        inv.setItem(11, button(Material.EMERALD, "<gradient:#38bdf8:#8b5cf6>Balance</gradient>", List.of(
                "<gray>View your current sapphire balance.</gray>",
                "<white>Balance:</white> <gradient:#22d3ee:#a78bfa>" + service.balance(player.getUniqueId()) + "</gradient>"
        )));
        inv.setItem(13, button(Material.CHEST, "<gradient:#34d399:#22c55e>Shop</gradient>", List.of(
                "<gray>Click to get the configured sapphire shop link.</gray>",
                "<gray>This redirects to the server shop website.</gray>"
        )));
        inv.setItem(15, button(Material.PAPER, "<gradient:#f59e0b:#f97316>Commands</gradient>", List.of(
                "<gray>Available player commands:</gray>",
                "<white>/sapphire bal</white>",
                "<white>/sapphire ballance</white>",
                "<white>/sapphire shop</white>"
        )));
        return inv;
    }

    public Inventory buildSummary(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "summary"), 27, "3SMP Sapphire Summary");
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(11, button(Material.EMERALD, "<gradient:#38bdf8:#8b5cf6>Balance</gradient>", List.of("<gray>Current balance:</gray> <white>" + service.balance(player.getUniqueId()) + "</white>")));
        inv.setItem(13, button(Material.CHEST, "<gradient:#34d399:#22c55e>Shop Link</gradient>", List.of("<gray>Configured website:</gray>", "<white>" + service.shopUrl() + "</white>")));
        inv.setItem(15, button(Material.PAPER, "<gradient:#f59e0b:#f97316>Commands</gradient>", List.of(
                "<white>/sapphire bal</white>",
                "<white>/sapphire ballance</white>",
                "<white>/sapphire shop</white>"
        )));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to sapphire menu.</gray>")));
        return inv;
    }

    public void handleClick(Player player, int slot) {
        switch (slot) {
            case 7 -> service.openSummary(player);
            case 11 -> service.sendBalance(player);
            case 13 -> service.openShopLink(player);
            case 15 -> service.sendCommandHelp(player);
            case 22 -> service.openShop(player);
            default -> { }
        }
    }

    public void handleSummaryClick(Player player, int slot) {
        switch (slot) {
            case 11 -> service.sendBalance(player);
            case 13 -> service.openShopLink(player);
            case 15 -> service.sendCommandHelp(player);
            case 22 -> service.openShop(player);
            default -> { }
        }
    }

    private void fill(Inventory inv, Material material) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(material, " ", List.of()));
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(lore.stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
