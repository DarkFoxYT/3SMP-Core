package net.dark.threecore.party.gui;

import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.party.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class PartyMenu {
    private final PartyService service;

    public PartyMenu(PartyService service) {
        this.service = service;
    }

    public Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PARTY_MAIN, "party"), 27, "3SMP Party");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(Material.GREEN_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(7, button(Material.BOOK, "<gradient:#1A2A4A:#D6E8F7>Summary</gradient>", List.of("<gray>View party status, size, and queue readiness.</gray>")));
        inv.setItem(9, button(Material.LECTERN, "<gradient:#34d399:#22c55e>Party Manager</gradient>", List.of("<gray>Open party controls and invites.</gray>")));
        inv.setItem(11, button(Material.LIME_BANNER, "<green>Create / Leave</green>", List.of("<gray>Create a party or leave your current one.</gray>")));
        inv.setItem(13, button(Material.NAME_TAG, "<gradient:#60a5fa:#c084fc>Invite Help</gradient>", List.of("<gray>Invite players with /party invite <player>.</gray>")));
        inv.setItem(15, button(Material.ENDER_PEARL, "<gradient:#f59e0b:#f97316>Queue 2v2</gradient>", List.of("<gray>Queue your party for duels.</gray>")));
        inv.setItem(17, button(Material.BARRIER, "<red>Disband / Deny</red>", List.of("<gray>Leaders disband here.</gray>", "<gray>Others deny invites here.</gray>")));
        inv.setItem(19, button(Material.PLAYER_HEAD, "<gradient:#a78bfa:#f472b6>Members</gradient>", List.of("<gray>Show party members.</gray>")));
        inv.setItem(21, button(Material.BOOK, "<gradient:#34d399:#22c55e>Status</gradient>", List.of("<gray>Show party status.</gray>")));
        inv.setItem(23, button(Material.CHEST, "<gradient:#60a5fa:#c084fc>Queue Info</gradient>", List.of("<gray>Show queue instructions.</gray>")));
        inv.setItem(25, button(Material.ARROW, "<gray>Help</gray>", List.of("<gray>Show management shortcuts.</gray>")));
        return inv;
    }

    public Inventory buildSummary(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PARTY_MAIN, "summary"), 27, "3SMP Party Summary");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(11, button(Material.PLAYER_HEAD, "<gradient:#34d399:#22c55e>Party Status</gradient>", List.of(
                "<gray>Status:</gray> <white>" + (service.isInParty(player.getUniqueId()) ? "In party" : "Solo") + "</white>",
                "<gray>Size:</gray> <white>" + service.partySize(player.getUniqueId()) + "</white> <gray>| Max:</gray> <white>" + service.maxSize() + "</white>"
        )));
        inv.setItem(13, button(Material.ENDER_PEARL, "<gradient:#f59e0b:#f97316>Queue Ready</gradient>", List.of(
                "<gray>Ready for 2v2:</gray> <white>" + service.canQueue2v2(player.getUniqueId()) + "</white>",
                "<gray>Invite pending:</gray> <white>" + service.hasInvite(player.getUniqueId()) + "</white>"
        )));
        inv.setItem(15, button(Material.BOOK, "<gradient:#60a5fa:#c084fc>Members</gradient>", List.of(
                "<gray>Open the party member list.</gray>"
        )));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to party menu.</gray>")));
        return inv;
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
