package net.dark.threecore.sapphires.gui;

import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.sapphires.SapphireService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

public final class SapphireMenu {
    private static final Set<Integer> VAULT_OPEN_BUTTONS = Set.of(13, 21, 22, 23, 29, 30, 31, 32, 33, 39, 40, 41);
    private final SapphireService service;

    public SapphireMenu(SapphireService service) {
        this.service = service;
    }

    public static boolean isVaultOpenButton(int slot) {
        return VAULT_OPEN_BUTTONS.contains(slot);
    }

    public Inventory buildEntry(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "entry"), 54, "3SMP Sapphire Vault");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : VAULT_OPEN_BUTTONS) inv.setItem(slot, invisibleButton());
        inv.setItem(4, button(Material.AMETHYST_CLUSTER, "<gradient:#f4cd2a:#eda323:#d28d0d>Sapphire Vault</gradient>", List.of(
                "<gray>Balance:</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + service.balance(player.getUniqueId()) + " Sapphires</gradient>",
                "<dark_gray>Press the vault core to open.</dark_gray>"
        )));
        return inv;
    }

    public Inventory buildVault(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "vault"), 54, "3SMP Sapphire Vault");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d>Vault Open</gradient>", List.of(
                "<gray>Spend Sapphires on crate keys, gems, cosmetics, and perks.</gray>"
        )));
        inv.setItem(13, button(Material.EMERALD, "<gradient:#f4cd2a:#eda323:#d28d0d>Sapphire Balance</gradient>", List.of(
                "<white>" + service.balance(player.getUniqueId()) + "</white> <#D6E8F7>Sapphires</#D6E8F7>"
        )));
        inv.setItem(49, button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Store Link</gradient>", List.of(
                "<gray>Configured website:</gray>",
                "<white>" + service.shopUrl() + "</white>"
        )));
        for (String id : service.shopItemIds()) {
            inv.setItem(service.shopItemSlot(id), button(service.shopItemMaterial(id),
                    "<gradient:#f4cd2a:#eda323:#d28d0d>" + service.shopItemDisplayName(id) + "</gradient>",
                    List.of(
                            "<gray>Price:</gray> <white>" + service.shopItemPrice(id) + "</white> <#D6E8F7>" + service.shopItemCurrency(id) + "</#D6E8F7>",
                            "<yellow>Click to purchase.</yellow>"
                    )));
        }
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

    private void fill(Inventory inv, Material material) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(material, " ", List.of()));
    }

    private ItemStack invisibleButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<reset>"));
        meta.lore(List.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setCustomModelData(1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
