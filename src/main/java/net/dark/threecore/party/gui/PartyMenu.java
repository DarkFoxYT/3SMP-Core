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
        inv.setItem(11, button(Material.NAME_TAG, "<gradient:#60a5fa:#c084fc>Invite Player</gradient>", List.of("<gray>Open a player picker to invite online players.</gray>")));
        inv.setItem(13, button(Material.PLAYER_HEAD, "<gradient:#a78bfa:#f472b6>Members</gradient>", List.of("<gray>View everyone currently in your party.</gray>")));
        inv.setItem(15, button(Material.ARROW, "<gradient:#D6E8F7:#FFFFFF>Leave Party</gradient>", List.of("<gray>Leave your current party.</gray>", "<gray>Leaders disband from the hotbar barrier.</gray>")));
        inv.setItem(22, button(Material.DIAMOND_SWORD, "<gradient:#60a5fa:#c084fc>Party Duel</gradient>", List.of("<gray>Set red/blue teams, kit, mode, and rounds.</gray>")));
        inv.setItem(24, button(Material.COMPASS, "<gradient:#4c1d95:#a78bfa>Team Dungeon</gradient>", List.of("<gray>Open dungeons in party mode.</gray>")));
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


