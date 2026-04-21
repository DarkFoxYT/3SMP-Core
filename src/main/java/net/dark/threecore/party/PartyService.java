package net.dark.threecore.party;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.party.gui.PartyMenu;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PartyService implements Listener {
    private static final String ITEM_ID_KEY = "3smpcore_party_item";
    private static final String HUB_ITEM_ID = "party_lectern";

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MenuService menuService;
    private final Map<UUID, PartyData> partiesByLeader = new HashMap<>();
    private final Map<UUID, UUID> leaderByMember = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();

    public PartyService(JavaPlugin plugin, ConfigFiles configs, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.menuService = menuService;
    }

    public void reload() { }

    public void handle(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        String sub = context.arg(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "", "menu" -> openMenu(player);
            case "create" -> create(player);
            case "invite" -> invite(player, context.arg(1));
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "leave" -> leave(player);
            case "kick" -> kick(player, context.arg(1));
            case "transfer" -> transfer(player, context.arg(1));
            case "disband" -> disband(player);
            default -> Text.send(player, "<red>Unknown party subcommand.</red>");
        }
    }

    public List<String> complete(CommandContext context) {
        if (context.args().length <= 1) return List.of("create", "invite", "accept", "deny", "leave", "kick", "transfer", "disband", "menu");
        return List.of();
    }

    public void openMenu(Player player) { menuService.open(player, new PartyMenu(this).build(player)); }
    public void openSummary(Player player) { menuService.open(player, new PartyMenu(this).buildSummary(player)); }

    public void handleMenuClick(Player player, int slot) {
        UUID leader = partyLeader(player.getUniqueId());
        switch (slot) {
            case 7 -> openSummary(player);
            case 9 -> openMenu(player);
            case 11 -> { if (isInParty(player.getUniqueId())) leave(player); else create(player); }
            case 13 -> showInviteHelp(player);
            case 15 -> { if (isInParty(player.getUniqueId())) queueParty(player); else accept(player); }
            case 17 -> { if (leader != null && leader.equals(player.getUniqueId())) disband(player); else deny(player); }
            case 19 -> showMembers(player);
            case 21 -> showStatus(player);
            case 23 -> showQueueInfo(player);
            case 25 -> showManagementHelp(player);
            default -> { }
        }
    }

    public void create(Player player) {
        if (isInParty(player.getUniqueId())) { Text.send(player, "<yellow>You are already in a party.</yellow>"); return; }
        partiesByLeader.put(player.getUniqueId(), new PartyData(player.getUniqueId()));
        leaderByMember.put(player.getUniqueId(), player.getUniqueId());
        givePartyItems(player);
        Text.send(player, "<green>Party created.</green>");
    }

    public void invite(Player player, String targetName) {
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null) { Text.send(player, "<red>Create a party first.</red>"); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { Text.send(player, "<red>Player not found.</red>"); return; }
        invites.put(target.getUniqueId(), leader);
        Text.send(player, "<green>Invite sent.</green>");
        Text.send(target, "<yellow>You were invited to a party by " + player.getName() + "</yellow>");
    }

    public void accept(Player player) {
        UUID leader = invites.remove(player.getUniqueId());
        if (leader == null) { Text.send(player, "<red>No pending invite.</red>"); return; }
        PartyData party = partiesByLeader.computeIfAbsent(leader, PartyData::new);
        if (party.members().size() >= maxSize()) { Text.send(player, "<red>That party is full.</red>"); return; }
        party.members().add(player.getUniqueId());
        leaderByMember.put(player.getUniqueId(), leader);
        givePartyItems(player);
        Text.send(player, "<green>Invite accepted.</green>");
    }

    public void deny(Player player) { invites.remove(player.getUniqueId()); Text.send(player, "<gray>Invite denied.</gray>"); }

    public void leave(Player player) {
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null) { Text.send(player, "<gray>You are not in a party.</gray>"); return; }
        PartyData party = partiesByLeader.get(leader);
        if (party == null) return;
        if (leader.equals(player.getUniqueId())) { disband(player); return; }
        party.members().remove(player.getUniqueId());
        leaderByMember.remove(player.getUniqueId());
        removePartyItems(player);
        Text.send(player, "<gray>You left the party.</gray>");
    }

    public void kick(Player player, String targetName) {
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null || !leader.equals(player.getUniqueId())) { Text.send(player, "<red>Only the leader can kick.</red>"); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { Text.send(player, "<red>Player not found.</red>"); return; }
        PartyData party = partiesByLeader.get(leader);
        if (party != null && party.members().remove(target.getUniqueId())) {
            leaderByMember.remove(target.getUniqueId());
            removePartyItems(target);
            Text.send(target, "<red>You were kicked from the party.</red>");
            Text.send(player, "<green>Kicked " + target.getName() + ".</green>");
        }
    }

    public void transfer(Player player, String targetName) {
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null || !leader.equals(player.getUniqueId())) { Text.send(player, "<red>Only the leader can transfer.</red>"); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { Text.send(player, "<red>Player not found.</red>"); return; }
        PartyData party = partiesByLeader.get(leader);
        if (party == null || !party.members().contains(target.getUniqueId())) { Text.send(player, "<red>Target is not in your party.</red>"); return; }
        partiesByLeader.remove(leader);
        party.withLeader(target.getUniqueId());
        leaderByMember.replaceAll((member, currentLeader) -> currentLeader.equals(leader) ? target.getUniqueId() : currentLeader);
        partiesByLeader.put(target.getUniqueId(), party);
        Text.send(player, "<green>Party ownership transferred.</green>");
    }

    public void disband(Player player) {
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null || !leader.equals(player.getUniqueId())) { Text.send(player, "<red>Only the leader can disband.</red>"); return; }
        PartyData party = partiesByLeader.remove(leader);
        if (party != null) {
            for (UUID member : party.members()) {
                leaderByMember.remove(member);
                Player target = Bukkit.getPlayer(member);
                if (target != null) removePartyItems(target);
            }
        }
        Text.send(player, "<red>Party disbanded.</red>");
    }

    public boolean isInParty(UUID uuid) { return partyLeader(uuid) != null; }
    public UUID partyLeader(UUID member) { return leaderByMember.get(member); }
    public Set<UUID> partyMembers(UUID member) {
        UUID leader = partyLeader(member);
        if (leader == null) return Set.of();
        PartyData party = partiesByLeader.get(leader);
        return party == null ? Set.of() : Set.copyOf(party.members());
    }
    public int partySize(UUID member) { return partyMembers(member).size(); }
    public int maxSize() { return configs.get("party.yml").getInt("party.max-size", 2); }
    public boolean canQueue2v2(UUID leader) { return partySize(leader) == 2; }
    public boolean hasInvite(UUID uuid) { return invites.containsKey(uuid); }

    public void handleSummaryClick(Player player, int slot) {
        if (slot == 22) openMenu(player);
        else if (slot == 11) showStatus(player);
        else if (slot == 13) showQueueInfo(player);
        else if (slot == 15) showMembers(player);
    }
    public boolean isLeader(UUID uuid) { return partyLeader(uuid) != null && partyLeader(uuid).equals(uuid); }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = itemId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        if (HUB_ITEM_ID.equals(id)) {
            openMenu(event.getPlayer());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        String id = itemId(event.getCurrentItem());
        if (HUB_ITEM_ID.equals(id)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player clicker && event.getClickedInventory() == clicker.getInventory()) openMenu(clicker);
        }
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof net.dark.threecore.gui.menu.CoreMenuHolder holder && holder.type().name().equals("PARTY_MAIN")) {
            if (event.getSlot() == 8 || event.getSlot() == 9) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof net.dark.threecore.gui.menu.CoreMenuHolder holder && holder.type().name().equals("PARTY_MAIN")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        String id = itemId(event.getItemDrop().getItemStack());
        if (HUB_ITEM_ID.equals(id)) event.setCancelled(true);
    }

    public void givePartyItem(Player player) { givePartyItems(player); }

    public void givePartyItems(Player player) {
        if (!configs.get("party.yml").getBoolean("party.item.enabled", true)) return;
        int hubSlot = Math.max(0, Math.min(8, configs.get("party.yml").getInt("party.item.slot", 8)));
        player.getInventory().setItem(hubSlot, createTagged(Material.LECTERN, configs.get("party.yml").getString("party.item.name", "<gradient:#34d399:#22c55e>Party Manager</gradient>"), HUB_ITEM_ID));
        clearTaggedItem(player, "party_barrier");
    }

    private void clearTaggedItem(Player player, String id) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            String existing = itemId(player.getInventory().getItem(i));
            if (id.equals(existing)) player.getInventory().setItem(i, null);
        }
    }

    private void removePartyItems(Player player) {
        clearTaggedItem(player, HUB_ITEM_ID);
    }

    private void queueParty(Player player) {
        if (!canQueue2v2(player.getUniqueId())) { Text.send(player, "<red>Your party must have exactly 2 players to queue 2v2.</red>"); return; }
        player.performCommand("/duel queue party");
    }

    private void showSummary(Player player) { Text.send(player, "<gradient:#1A2A4A:#D6E8F7>3SMP Party Summary</gradient>"); Text.send(player, "<gray>Status:</gray> <white>" + (isInParty(player.getUniqueId()) ? "In party" : "Solo") + "</white>"); Text.send(player, "<gray>Party size:</gray> <white>" + partySize(player.getUniqueId()) + "</white> <gray>| Max:</gray> <white>" + maxSize() + "</white>"); Text.send(player, "<gray>Ready for 2v2:</gray> <white>" + canQueue2v2(player.getUniqueId()) + "</white>"); }
    private void showInviteHelp(Player player) { Text.send(player, "<gray>Use <white>/party invite <player></white>, then they can accept or deny.</gray>"); }
    private void showMembers(Player player) {
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null) { Text.send(player, "<gray>You are not in a party.</gray>"); return; }
        PartyData party = partiesByLeader.get(leader);
        if (party == null) { Text.send(player, "<gray>Party data missing.</gray>"); return; }
        StringBuilder sb = new StringBuilder();
        for (UUID member : party.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) sb.append(online.getName()).append(", ");
        }
        Text.send(player, "<gray>Members: <white>" + (sb.isEmpty() ? "none" : sb.substring(0, sb.length() - 2)) + "</white></gray>");
    }
    private void showStatus(Player player) { Text.send(player, isInParty(player.getUniqueId()) ? "<green>You are in a party.</green>" : "<gray>You are solo.</gray>"); }
    private void showQueueInfo(Player player) { Text.send(player, "<gray>2v2 queues require a party of exactly 2 players.</gray>"); }
    private void showManagementHelp(Player player) { Text.send(player, "<gray>Leader actions: create, invite, kick, transfer, disband. Member actions: accept, deny, leave.</gray>"); }

    private ItemStack createTagged(Material material, String name, String id) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(List.of(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Right click to use.</gray>")));
        meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    private String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
    }

    private NamespacedKey key() { return new NamespacedKey(plugin, ITEM_ID_KEY); }

    private record PartyData(UUID leader, Set<UUID> members) {
        private PartyData(UUID leader) { this(leader, new LinkedHashSet<>(Set.of(leader))); }
        private PartyData withLeader(UUID newLeader) { return new PartyData(newLeader, new LinkedHashSet<>(members)); }
    }
}

