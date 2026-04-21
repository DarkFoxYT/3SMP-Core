package net.dark.threecore.duels.gui;

import net.dark.threecore.duels.DuelService;
import net.dark.threecore.duels.model.DuelKit;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class DuelMenu {
    private final DuelService service;

    public DuelMenu(DuelService service) {
        this.service = service;
    }

    public Inventory buildMain(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_MAIN, "main"), 27, "3SMP Duels");
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(7, button(Material.BOOK, "<gradient:#1A2A4A:#D6E8F7>Summary</gradient>", List.of("<gray>Open duel status summary.</gray>")));
        inv.setItem(10, button(Material.DIAMOND_SWORD, "<gradient:#60a5fa:#c084fc>1v1 Duels</gradient>", List.of("<gray>Pick a kit and queue solo.</gray>")));
        inv.setItem(16, button(Material.PLAYER_HEAD, "<gradient:#34d399:#22c55e>2v2 Party Duels</gradient>", List.of("<gray>Queue your party as one team.</gray>")));
        inv.setItem(13, button(Material.NETHER_STAR, "<gradient:#38bdf8:#8b5cf6>Queue Info</gradient>", List.of("<gray>Click the mode or kit buttons to queue.</gray>", "<gray>Queue HUD shows above the hotbar.</gray>")));
        return inv;
    }

    public Inventory buildKitMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_KITS, "kits"), 54, "3SMP Duel Kits");
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        int slot = 10;
        for (DuelKit kit : service.kits()) {
            if (!kit.enabled()) continue;
            boolean queued = service.isQueuedForKit(player.getUniqueId(), kit.id());
            List<String> lore = new java.util.ArrayList<>(kit.lore());
            lore.add(queued ? "<green>Click to leave queue.</green>" : "<aqua>Click to queue this kit.</aqua>");
            inv.setItem(slot++, button(queued ? Material.LIME_DYE : kit.icon(), queued ? "<green>Queued: " + kit.displayName() + "</green>" : kit.displayName(), lore));
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
        }
        inv.setItem(45, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to duel menu.</gray>")));
        inv.setItem(49, button(Material.PAPER, "<gradient:#60a5fa:#c084fc>Queue Status</gradient>", List.of("<gray>Queued kits toggle when clicked.</gray>", "<gray>This menu updates live.</gray>")));
        return inv;
    }

    public Inventory buildDev(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "dev"), 27, "3SMP Duel Dev");
        fill(inv, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(7, button(Material.BOOK, "<gradient:#1A2A4A:#D6E8F7>Summary</gradient>", List.of("<gray>Inspect queue, maps, and testing tools.</gray>")));
        inv.setItem(11, button(Material.DIAMOND_SWORD, "<gradient:#60a5fa:#c084fc>Test Duel</gradient>", List.of("<gray>Simulate the duel flow.</gray>")));
        inv.setItem(13, button(Material.MAP, "<gradient:#34d399:#22c55e>Map Editor</gradient>", List.of("<gray>Manage spawn points and arena data.</gray>")));
        inv.setItem(15, button(Material.OAK_SIGN, "<gradient:#f59e0b:#f97316>Leaderboard</gradient>", List.of("<gray>View duel ratings and stats.</gray>")));
        inv.setItem(17, button(Material.GOLD_BLOCK, "<gradient:#f59e0b:#f97316>Launchpads</gradient>", List.of("<gray>Open the launchpad editor.</gray>")));
        return inv;
    }

    public Inventory buildSummary(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_MAIN, "summary"), 27, "3SMP Duel Summary");
        fill(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        inv.setItem(11, button(Material.DIAMOND_SWORD, "<gradient:#60a5fa:#c084fc>Solo Queues</gradient>", List.of(
                "<gray>Queued players:</gray> <white>" + service.soloQueueCount() + "</white>",
                "<gray>Available kits:</gray> <white>" + service.kitCount() + "</white>"
        )));
        inv.setItem(13, button(Material.PLAYER_HEAD, "<gradient:#34d399:#22c55e>Party Queues</gradient>", List.of(
                "<gray>Queued parties:</gray> <white>" + service.partyQueueCount() + "</white>",
                "<gray>Enabled:</gray> <white>" + service.isEnabled() + "</white>"
        )));
        inv.setItem(15, button(Material.MAP, "<gradient:#f59e0b:#f97316>Maps</gradient>", List.of(
                "<gray>Loaded maps:</gray> <white>" + service.mapCount() + "</white>",
                "<gray>Dev mode:</gray> <white>" + service.isDevEnabled(player.getUniqueId()) + "</white>"
        )));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to duel menu.</gray>")));
        return inv;
    }

    private void fill(Inventory inv, Material material) { for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(material, " ", List.of())); }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(lore.stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
