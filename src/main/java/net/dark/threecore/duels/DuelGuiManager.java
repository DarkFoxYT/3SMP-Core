package net.dark.threecore.duels;

import net.dark.threecore.duels.model.DuelKit;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DuelGuiManager {
    private static final int[] SOLO_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] PARTY_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34};
    private final DuelService service;

    public DuelGuiManager(DuelService service) {
        this.service = service;
    }

    public Inventory buildMain(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_MAIN, "main"), 54, title("menus.duel", "3SMP Duels"));
        fill(inv, Material.AIR);
        ItemStack solo = button("menus.duel.items.solo", Material.DIAMOND_SWORD, "<gradient:#60a5fa:#c084fc>1v1</gradient>", List.of(
                "<gray>Solo duel queue.</gray>",
                "<gray>Current mode:</gray> <white>" + service.queueModeName(player.getUniqueId()) + "</white>",
                "<gray>Current kit:</gray> <white>" + service.queueKitName(player.getUniqueId()) + "</white>",
                "<gray>Click to choose a kit and queue.</gray>"
        ));
        ItemStack party = button("menus.duel.items.party", Material.PLAYER_HEAD, "<gradient:#f4cd2a:#eda323:#d28d0d>Party Duels</gradient>", List.of(
                "<gray>Queue your party or configure party FFA.</gray>",
                "<gray>Current mode:</gray> <white>" + service.queueModeName(player.getUniqueId()) + "</white>",
                "<gray>Current kit:</gray> <white>" + service.queueKitName(player.getUniqueId()) + "</white>",
                "<gray>Click to choose a kit and queue.</gray>"
        ));
        setArea(inv, SOLO_SLOTS, solo);
        setArea(inv, PARTY_SLOTS, party);
        return inv;
    }

    private void setArea(Inventory inv, int[] slots, ItemStack item) {
        for (int slot : slots) inv.setItem(slot, item.clone());
    }

    public Inventory buildKitSelector(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_KITS, "kits"), 54, title("menus.kits", "Select Duel Kit"));
        fill(inv, Material.AIR);
        int[] slots = {10,12,14,16,28,30,32,34};
        int index = 0;
        for (DuelKit kit : service.kits()) {
            if (!kit.enabled()) continue;
            int slot = kit.slot();
            if (slot < 0 || slot >= inv.getSize() || inv.getItem(slot) != null) {
                if (index >= slots.length) break;
                slot = slots[index++];
            }
            inv.setItem(slot, button("menus.duel.items.kit", kit.icon(), kit.displayName(), kitSelectorLore(kit)));
        }
        return inv;
    }

    public Inventory buildSummary(Player player) {
        return buildEndSummary(player,
                "Summary",
                "00:00",
                0,
                0,
                0,
                0,
                0);
    }

    public Inventory buildEndSummary(Player player, String winner, String duration, int kills, int deaths, int wins, int losses, int streak) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_MAIN, "summary"), 27, title("menus.summary", "Duel Summary"));
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(11, button("menus.duel.items.summary-winner", Material.GOLD_BLOCK, "<gradient:#60a5fa:#c084fc>" + winner + "</gradient>", List.of(
                "<gray>Duration:</gray> <white>" + duration + "</white>",
                "<gray>Kills:</gray> <white>" + kills + "</white>",
                "<gray>Deaths:</gray> <white>" + deaths + "</white>"
        )));
        inv.setItem(13, button("menus.duel.items.summary-stats", Material.PAPER, "<gradient:#34d399:#22c55e>Stats</gradient>", List.of(
                "<gray>Wins:</gray> <white>" + wins + "</white>",
                "<gray>Losses:</gray> <white>" + losses + "</white>",
                "<gray>Streak:</gray> <white>" + streak + "</white>"
        )));
        inv.setItem(15, button("menus.duel.items.back", Material.ARROW, "<gray>Back to Duels</gray>", List.of("<gray>Open duel menu.</gray>")));
        return inv;
    }

    private void fill(Inventory inv, Material material) {
        if (material == Material.AIR) return;
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(material, " ", List.of()));
    }

    private List<String> kitSelectorLore(DuelKit kit) {
        List<String> lore = new ArrayList<>();
        List<String> description = new ArrayList<>();
        for (String line : kit.lore()) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(net.dark.threecore.text.Text.mm(line)).toLowerCase();
            if (plain.contains("round") || plain.contains("auto splash") || plain.contains("auto-splash")) continue;
            description.add(line);
        }
        if (!description.isEmpty()) {
            lore.add("<gray>Description:</gray>");
            lore.addAll(description);
        }
        lore.add("<gray>Queued:</gray> <white>" + service.queuedPlayersForKit(kit.id()) + "</white>");
        lore.add("<gray>Click to select this kit.</gray>");
        return lore;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        return decorate(new ItemStack(material), name, lore);
    }

    private ItemStack button(String path, Material fallback, String name, List<String> lore) {
        return decorate(service.guiIcon(path, fallback), name, lore);
    }

    private ItemStack decorate(ItemStack item, String name, List<String> lore) {
        if (item == null || item.getType() == Material.AIR) return item == null ? new ItemStack(Material.AIR) : item;
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(new ArrayList<>(lore).stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private net.kyori.adventure.text.Component title(String path, String fallback) {
        String title = service.guiText(path + ".title", fallback);
        String background = service.guiText(path + ".background", "");
        StringBuilder builder = new StringBuilder(background == null ? "" : background);
        for (String layer : service.guiTextList(path + ".layers")) builder.append(layer);
        builder.append(title == null ? "" : title);
        return Text.mm(builder.toString());
    }
}
