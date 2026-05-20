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
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "entry"), 54, service.menuTitle("entry", "3SMP Sapphire Vault"));
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        for (int slot : VAULT_OPEN_BUTTONS) inv.setItem(slot, invisibleButton());
        inv.setItem(4, button(Material.AMETHYST_CLUSTER, "<gradient:#f4cd2a:#eda323:#d28d0d>Sapphire Vault</gradient>", List.of(
                "<gray>Balance:</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + service.balance(player.getUniqueId()) + " Sapphires</gradient>",
                "<dark_gray>Press the vault core to open.</dark_gray>"
        )));
        return inv;
    }

    public Inventory buildVault(Player player, int requestedPage) {
        int page = service.normalizeSapphirePage(requestedPage);
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "vault:" + page), 54, service.menuTitle("vault", "3SMP Sapphire Vault"));
        fill(inv, Material.AIR);
        inv.setItem(service.sapphireBalanceSlot(), button(service.sapphireIconItem(), "<gradient:#f4cd2a:#eda323:#d28d0d>Sapphire Balance</gradient>", List.of(
                "<white>" + service.balance(player.getUniqueId()) + "</white> <#D6E8F7>Sapphires</#D6E8F7>",
                "<dark_gray>Page " + (page + 1) + " of " + service.sapphirePageCount() + "</dark_gray>"
        )));
        inv.setItem(service.sapphireStoreSlot(), button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Store Link</gradient>", List.of(
                "<gray>Configured website:</gray>",
                "<yellow>Click to open.</yellow>",
                "<white>" + service.shopUrl() + "</white>"
        )));
        List<String> ids = service.shopItemIds();
        List<Integer> slots = service.sapphireItemSlots();
        if (service.usesConfiguredSapphirePages()) {
            for (String id : ids) {
                int slot = service.shopItemSlot(id);
                if (service.shopItemPage(id) == page + 1 && slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, shopButton(id));
                }
            }
        } else {
            int start = page * slots.size();
            for (int i = 0; i < slots.size() && start + i < ids.size(); i++) {
                inv.setItem(slots.get(i), shopButton(ids.get(start + i)));
            }
        }
        if (page > 0) inv.setItem(service.sapphirePreviousSlot(), button(Material.ARROW, "<gradient:#D6E8F7:#ffffff>Previous Page</gradient>", List.of("<gray>Page " + page + " of " + service.sapphirePageCount() + "</gray>")));
        if (page + 1 < service.sapphirePageCount()) inv.setItem(service.sapphireNextSlot(), button(Material.ARROW, "<gradient:#D6E8F7:#ffffff>Next Page</gradient>", List.of("<gray>Page " + (page + 2) + " of " + service.sapphirePageCount() + "</gray>")));
        return inv;
    }

    public Inventory buildSummary(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.SAPPHIRES_MAIN, "summary"), 27, service.menuTitle("summary", "3SMP Sapphire Summary"));
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(11, button(service.sapphireIconItem(), "<gradient:#f4cd2a:#eda323:#d28d0d>Balance</gradient>", List.of("<gray>Current balance:</gray> <white>" + service.balance(player.getUniqueId()) + "</white>")));
        inv.setItem(13, button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Shop Link</gradient>", List.of("<gray>Configured website:</gray>", "<white>" + service.shopUrl() + "</white>")));
        inv.setItem(15, button(Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>Commands</gradient>", List.of(
                "<white>/sapphire bal</white>",
                "<white>/sapphire shop</white>"
        )));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to sapphire menu.</gray>")));
        return inv;
    }

    private void fill(Inventory inv, Material material) {
        if (material == Material.AIR) return;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(material, " ", List.of()));
    }

    private ItemStack invisibleButton() {
        ItemStack item = service.guiClickzoneItem();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<reset>"));
        meta.lore(List.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        return button(item, name, lore);
    }

    private ItemStack button(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack shopButton(String id) {
        long price = service.shopItemPrice(id);
        return button(service.shopItemIcon(id),
                "<gradient:#f4cd2a:#eda323:#d28d0d>" + service.shopItemDisplayName(id) + "</gradient>",
                price <= 0
                        ? List.of("<gray>Price:</gray> <white>TBD</white>", "<dark_gray>Coming soon.</dark_gray>")
                        : opensUnlockPicker(id)
                        ? List.of(
                                "<gray>Price per unlock:</gray> <white>" + String.format(java.util.Locale.US, "%,d", price) + "</white> <#D6E8F7>" + currencyName(service.shopItemCurrency(id)) + "</#D6E8F7>",
                                "<yellow>Click to choose.</yellow>"
                        )
                        : List.of(
                                "<gray>Price:</gray> <white>" + String.format(java.util.Locale.US, "%,d", price) + "</white> <#D6E8F7>" + currencyName(service.shopItemCurrency(id)) + "</#D6E8F7>",
                                "<yellow>Click to purchase.</yellow>"
                        ));
    }

    private String currencyName(String currency) {
        return currency == null || currency.equalsIgnoreCase("sapphires") ? "Sapphires" : currency;
    }

    private boolean opensUnlockPicker(String id) {
        return id.equalsIgnoreCase("cosmetics")
            || id.equalsIgnoreCase("kill_effect")
            || id.equalsIgnoreCase("badge")
            || id.equalsIgnoreCase("join_quit_message")
            || id.equalsIgnoreCase("cosmetic")
            || id.equalsIgnoreCase("weapon_cosmetic")
            || id.equalsIgnoreCase("tag")
            || id.equalsIgnoreCase("particle")
            || id.equalsIgnoreCase("name_color")
            || id.equalsIgnoreCase("name_gradient");
    }
}
