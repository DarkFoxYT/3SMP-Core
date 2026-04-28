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
        inv.setItem(11, button(Material.DIAMOND_SWORD, "<gradient:#60a5fa:#c084fc>1v1 Duels</gradient>", List.of(
                "<gray>Pick a kit and queue solo.</gray>",
                "<gray>Queued players:</gray> <white>" + service.soloQueueCount() + "</white>",
                "<gray>Queued mode:</gray> <white>" + service.queueModeName(player.getUniqueId()) + "</white>",
                "<gray>Queued kit:</gray> <white>" + service.queueKitName(player.getUniqueId()) + "</white>"
        )));
        inv.setItem(15, button(Material.SHIELD, "<gradient:#34d399:#22c55e>2v2 Duels</gradient>", List.of(
                "<gray>Queue with a party or as a solo fill.</gray>",
                "<gray>Queued units:</gray> <white>" + service.partyQueueCount() + "</white>",
                "<gray>Queued mode:</gray> <white>" + service.queueModeName(player.getUniqueId()) + "</white>",
                "<gray>Queued kit:</gray> <white>" + service.queueKitName(player.getUniqueId()) + "</white>"
        )));
        return inv;
    }

    public Inventory buildKitMenu(Player player) {
        return buildKitMenu(player, "3SMP Duel Kits");
    }

    public Inventory buildKitMenu(Player player, String title) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_KITS, "kits"), 54, title);
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        int[] slots = {10,12,14,16,28,30,32,34};
        int pos = 0;
        for (DuelKit kit : service.kits()) {
            if (!kit.enabled()) continue;
            boolean queued = service.isQueuedForKit(player.getUniqueId(), kit.id());
            List<String> lore = new java.util.ArrayList<>(kit.lore());
            lore.add("<gray>Queued players:</gray> <white>" + service.queuedPlayersForKit(kit.id()) + "</white>");
            lore.add(queued ? "<green>Click to leave queue.</green>" : "<aqua>Click to select this kit.</aqua>");
            if (pos >= slots.length) break;
            int slot = slots[pos++];
            inv.setItem(slot, button(queued ? Material.LIME_DYE : kit.icon(), queued ? "<green>Queued: " + kit.displayName() + "</green>" : kit.displayName(), lore));
        }
        inv.setItem(45, backButton("duel menu"));
        
        return inv;
    }

    public Inventory buildDev(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "dev"), 54, "3SMP Dev Panel");
        fill(inv, Material.BLUE_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.NETHER_STAR, "<gradient:#1A2A4A:#f59e0b>3SMP Developer Panel</gradient>", List.of(
                "<gray>Clean admin tools for testing and editing.</gray>",
                "<gray>Duel enabled:</gray> <white>" + service.isEnabled() + "</white>",
                "<gray>Selected map:</gray> <white>" + service.selectedEditorMap(player.getUniqueId()) + "</white>",
                "<gray>Editor opens per selected arena and closes on save.</gray>"
        )));
        inv.setItem(12, button(Material.MAP, "<gradient:#34d399:#22c55e>Select Arena To Edit</gradient>", List.of("<gray>Pick an arena, teleport to its editor world,</gray>", "<gray>and receive marker tools automatically.</gray>")));
        inv.setItem(14, button(Material.ARMOR_STAND, "<gradient:#60a5fa:#c084fc>Current Arena Tools</gradient>", List.of("<gray>Open the current arena marker panel.</gray>", "<gray>Save the arena to exit editor mode.</gray>")));
        inv.setItem(16, button(Material.OAK_SIGN, "<gradient:#f59e0b:#f97316>Leaderboards</gradient>", List.of("<gray>Inspect duel ratings and streaks.</gray>")));
        inv.setItem(18, button(Material.SMITHING_TABLE, "<gradient:#60a5fa:#f59e0b>Kit Editor</gradient>", List.of("<gray>Edit kit inventory, armor, and offhand in-game.</gray>")));
        inv.setItem(20, button(Material.STRUCTURE_VOID, "<gradient:#4c1d95:#a78bfa>Dungeon Editor</gradient>", List.of("<gray>Teleport into the dungeon editor world.</gray>", "<gray>Get the dungeon room tools and markers.</gray>")));
        inv.setItem(30, button(service.isEnabled() ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK, service.isEnabled() ? "<red>Disable Duels</red>" : "<green>Enable Duels</green>", List.of("<gray>Toggle matchmaking and challenges.</gray>")));
        inv.setItem(32, button(Material.SLIME_BLOCK, "<gradient:#f59e0b:#f97316>Launchpads</gradient>", List.of("<gray>Open the launchpad editor.</gray>")));
        inv.setItem(34, button(Material.STRUCTURE_BLOCK, "<gradient:#22d3ee:#8b5cf6>Save Current Arena</gradient>", List.of("<gray>Save markers, restore your inventory,</gray>", "<gray>and return to spawn.</gray>")));
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
        inv.setItem(22, backButton("duel menu"));
        return inv;
    }

    private ItemStack backButton(String destination) {
        return button(Material.ARROW, "<gradient:#D6E8F7:#FFFFFF>Back</gradient>", List.of("<gray>Return to " + destination + ".</gray>"));
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



