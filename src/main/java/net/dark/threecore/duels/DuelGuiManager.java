package net.dark.threecore.duels;

import net.dark.threecore.duels.model.DuelKit;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DuelGuiManager {
    private final DuelService service;

    public DuelGuiManager(DuelService service) {
        this.service = service;
    }

    public Inventory buildMain(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_MAIN, "main"), 27, "3SMP Duels");
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(10, button(Material.DIAMOND_SWORD, "<gradient:#60a5fa:#c084fc>1v1</gradient>", List.of(
                "<gray>Solo duel queue.</gray>",
                "<gray>Current mode:</gray> <white>" + service.queueModeName(player.getUniqueId()) + "</white>",
                "<gray>Current kit:</gray> <white>" + service.queueKitName(player.getUniqueId()) + "</white>",
                "<gray>Click to choose a kit and queue.</gray>"
        )));
        inv.setItem(16, button(Material.PLAYER_HEAD, "<gradient:#f59e0b:#f97316>Party Duels</gradient>", List.of(
                "<gray>Queue your party for 2v2.</gray>",
                "<gray>Current mode:</gray> <white>" + service.queueModeName(player.getUniqueId()) + "</white>",
                "<gray>Current kit:</gray> <white>" + service.queueKitName(player.getUniqueId()) + "</white>"
        )));
        return inv;
    }

    public Inventory buildKitSelector(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_KITS, "kits"), 54, "Select Duel Kit");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        int[] slots = {10,12,14,16,28,30,32,34};
        int index = 0;
        for (DuelKit kit : service.kits()) {
            if (!kit.enabled()) continue;
            if (index >= slots.length) break;
            inv.setItem(slots[index++], button(kit.icon(), kit.displayName(), kitSelectorLore(kit)));
        }
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to duel menu.</gray>")));
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
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_MAIN, "summary"), 27, "Duel Summary");
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(11, button(Material.GOLD_BLOCK, "<gradient:#60a5fa:#c084fc>" + winner + "</gradient>", List.of(
                "<gray>Duration:</gray> <white>" + duration + "</white>",
                "<gray>Kills:</gray> <white>" + kills + "</white>",
                "<gray>Deaths:</gray> <white>" + deaths + "</white>"
        )));
        inv.setItem(13, button(Material.PAPER, "<gradient:#34d399:#22c55e>Stats</gradient>", List.of(
                "<gray>Wins:</gray> <white>" + wins + "</white>",
                "<gray>Losses:</gray> <white>" + losses + "</white>",
                "<gray>Streak:</gray> <white>" + streak + "</white>"
        )));
        inv.setItem(15, button(Material.ARROW, "<gray>Back to Duels</gray>", List.of("<gray>Open duel menu.</gray>")));
        return inv;
    }

    private void fill(Inventory inv, Material material) {
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
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(new ArrayList<>(lore).stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
