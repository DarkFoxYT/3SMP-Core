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
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "main"), 54, "3SMP Sapphire Vault");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d>Sapphire Vault</gradient>", List.of(
                "<gray>Premium currency, crate keys, gem tools, and cosmetics.</gray>",
                "<gray>Balance:</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + service.balance(player.getUniqueId()) + " Sapphires</gradient>"
        )));
        inv.setItem(10, button(Material.EMERALD, "<gradient:#f4cd2a:#eda323:#d28d0d>Balance</gradient>", List.of(
                "<gray>Your current sapphire balance.</gray>",
                "<white>" + service.balance(player.getUniqueId()) + "</white> <#D6E8F7>Sapphires</#D6E8F7>"
        )));
        inv.setItem(12, button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Store Link</gradient>", List.of(
                "<gray>Open the configured shop website.</gray>",
                "<white>" + service.shopUrl() + "</white>"
        )));
        inv.setItem(14, button(Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>Commands</gradient>", List.of(
                "<gray>Available player commands:</gray>",
                "<white>/sapphire bal</white>",
                "<white>/sapphire ballance</white>",
                "<white>/sapphire shop</white>"
        )));
        inv.setItem(16, button(Material.AMETHYST_CLUSTER, "<gradient:#f4cd2a:#eda323:#d28d0d>What Are Sapphires?</gradient>", List.of(
                "<gray>Earn or buy Sapphires and spend them here.</gray>",
                "<gray>Purchases run instantly from this menu.</gray>"
        )));
        inv.setItem(28, button(Material.TRIPWIRE_HOOK, "<gradient:#f4cd2a:#eda323:#d28d0d>Crate Keys</gradient>", List.of("<gray>Buy crate keys with Sapphires.</gray>", "<yellow>Click to purchase.</yellow>")));
        inv.setItem(30, button(Material.PRISMARINE_SHARD, "<gradient:#f4cd2a:#eda323:#d28d0d>Gem Extractor</gradient>", List.of("<gray>Remove gems from socketed gear.</gray>", "<yellow>Click to purchase.</yellow>")));
        inv.setItem(32, button(Material.ENDER_CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Gem Capsules</gradient>", List.of("<gray>Buy random gem capsules.</gray>", "<red>Prismatic gems are excluded.</red>", "<yellow>Click to purchase.</yellow>")));
        inv.setItem(34, button(Material.ENDER_EYE, "<gradient:#f4cd2a:#eda323:#d28d0d>Cosmetics</gradient>", List.of("<gray>Unlock cosmetic content.</gray>", "<yellow>Click to purchase.</yellow>")));
        inv.setItem(40, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d>Donor Ranks</gradient>", List.of("<gray>Spend Sapphires toward donor perks.</gray>", "<yellow>Click to purchase.</yellow>")));
        return inv;
    }

    public Inventory buildSummary(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "summary"), 27, "3SMP Sapphire Summary");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(11, button(Material.EMERALD, "<gradient:#f4cd2a:#eda323:#d28d0d>Balance</gradient>", List.of("<gray>Current balance:</gray> <white>" + service.balance(player.getUniqueId()) + "</white>")));
        inv.setItem(13, button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Shop Link</gradient>", List.of("<gray>Configured website:</gray>", "<white>" + service.shopUrl() + "</white>")));
        inv.setItem(15, button(Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>Commands</gradient>", List.of(
                "<white>/sapphire bal</white>",
                "<white>/sapphire ballance</white>",
                "<white>/sapphire shop</white>"
        )));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to sapphire menu.</gray>")));
        return inv;
    }

    public void handleClick(Player player, int slot) {
        switch (slot) {
            case 10 -> service.sendBalance(player);
            case 12 -> service.openShopLink(player);
            case 14 -> service.sendCommandHelp(player);
            case 28 -> service.purchase(player, "crate_keys");
            case 30 -> service.purchase(player, "gem_extractor");
            case 32 -> service.purchase(player, "gem_capsule");
            case 34 -> service.purchase(player, "cosmetics");
            case 40 -> service.purchase(player, "donor_rank");
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
