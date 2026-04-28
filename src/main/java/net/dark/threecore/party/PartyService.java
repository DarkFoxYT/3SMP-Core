package net.dark.threecore.party;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.social.FriendService;
import net.dark.threecore.duels.model.DuelKit;
import net.dark.threecore.party.gui.PartyMenu;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PartyService implements Listener {
    private static final String ITEM_ID_KEY = "3smpcore_party_item";
    private static final String HUB_ITEM_ID = "party_lectern";
    private static final String CREATE_ITEM_ID = "party_goat_horn";
    private static final String DISBAND_ITEM_ID = "party_disband";

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MenuService menuService;
    private final Map<UUID, PartyData> partiesByLeader = new HashMap<>();
    private final Map<UUID, UUID> leaderByMember = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    private final Map<UUID, PartyDuelSetup> duelSetups = new HashMap<>();
    private final Map<UUID, PendingRoundsEdit> pendingRoundsEdits = new HashMap<>();
    private DuelService duelService;
    private DungeonService dungeonService;
    private FriendService friendService;
    private net.dark.threecore.social.SocialTabService socialTabService;

    public PartyService(JavaPlugin plugin, ConfigFiles configs, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.menuService = menuService;
    }

    public void reload() { }

    public void setDuelService(DuelService duelService) { this.duelService = duelService; }
    public void setDungeonService(DungeonService dungeonService) { this.dungeonService = dungeonService; }
    public void setFriendService(FriendService friendService) { this.friendService = friendService; }
    public void setSocialTabService(net.dark.threecore.social.SocialTabService socialTabService) { this.socialTabService = socialTabService; }

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
            case "duel" -> openPartyDuelMenu(player);
            case "dungeon" -> openPartyDungeon(player);
            default -> Text.send(player, "<red>Unknown party subcommand.</red>");
        }
    }

    public List<String> complete(CommandContext context) {
        if (context.args().length <= 1) return List.of("create", "invite", "accept", "deny", "leave", "kick", "transfer", "disband", "duel", "dungeon", "menu");
        return List.of();
    }

    public void openMenu(Player player) { menuService.open(player, new PartyMenu(this).build(player)); }
    public void openSummary(Player player) { menuService.open(player, new PartyMenu(this).buildSummary(player)); }

    public void handleMenuClick(Player player, int slot) {
        UUID leader = partyLeader(player.getUniqueId());
        switch (slot) {
            case 11 -> openInvitePicker(player, 0);
            case 13 -> openMembersMenu(player);
            case 15 -> leave(player);
            case 22 -> openPartyDuelMenu(player);
            case 24 -> openPartyDungeon(player);
            default -> { }
        }
    }

    public void openPartyDungeon(Player player) {
        if (!isInParty(player.getUniqueId())) { Text.send(player, "<red>Create a party first.</red>"); return; }
        if (!isLeader(player.getUniqueId())) { Text.send(player, "<yellow>Only the party leader can start a party dungeon.</yellow>"); return; }
        if (dungeonService == null) { Text.send(player, "<red>Dungeon service is not ready.</red>"); return; }
        dungeonService.enablePartyMode(player);
        dungeonService.openMenu(player);
    }
    public void create(Player player) {
        if (isInParty(player.getUniqueId())) { Text.send(player, "<yellow>You are already in a party.</yellow>"); return; }
        partiesByLeader.put(player.getUniqueId(), new PartyData(player.getUniqueId()));
        leaderByMember.put(player.getUniqueId(), player.getUniqueId());
        givePartyItems(player);
        if (socialTabService != null) socialTabService.refreshAll();
        Text.send(player, "<green>Party created.</green>");
    }

    public void invite(Player player, String targetName) {
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null) { Text.send(player, "<red>Create a party first.</red>"); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { Text.send(player, "<red>Player not found.</red>"); return; }
        invites.put(target.getUniqueId(), leader);
        Text.send(player, "<green>Invite sent.</green>");
        sendInviteMessage(target, player.getName());
    }

    public void accept(Player player) {
        UUID leader = invites.remove(player.getUniqueId());
        if (leader == null) { Text.send(player, "<red>No pending invite.</red>"); return; }
        PartyData party = partiesByLeader.computeIfAbsent(leader, PartyData::new);
        if (party.members().size() >= maxSize()) { Text.send(player, "<red>That party is full.</red>"); return; }
        party.members().add(player.getUniqueId());
        leaderByMember.put(player.getUniqueId(), leader);
        givePartyItems(player);
        if (socialTabService != null) socialTabService.refreshAll();
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
        if (socialTabService != null) socialTabService.refreshAll();
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
            if (socialTabService != null) socialTabService.refreshAll();
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
        if (socialTabService != null) socialTabService.refreshAll();
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
                if (target != null) givePartyItems(target);
            }
        }
        givePartyItems(player);
        if (socialTabService != null) socialTabService.refreshAll();
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
    public int maxSize() { return configs.get("social/party.yml").getInt("party.max-size", 2); }
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
        if (HUB_ITEM_ID.equals(id)) openMenu(event.getPlayer());
        else if (CREATE_ITEM_ID.equals(id)) create(event.getPlayer());
        else if (DISBAND_ITEM_ID.equals(id)) disband(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        String id = itemId(event.getCurrentItem());
        if (HUB_ITEM_ID.equals(id) || CREATE_ITEM_ID.equals(id) || DISBAND_ITEM_ID.equals(id)) {
            event.setCancelled(true);
            event.setCurrentItem(event.getCurrentItem());
            if (event.getWhoClicked() instanceof Player clicker && event.getClickedInventory() == clicker.getInventory()) {
                event.setCursor(null);
                Bukkit.getScheduler().runTask(plugin, clicker::updateInventory);
                if (HUB_ITEM_ID.equals(id)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (clicker.isOnline()) {
                            clicker.setItemOnCursor(null);
                            clicker.updateInventory();
                            openMenu(clicker);
                        }
                    }, 20L);
                }
                else if (CREATE_ITEM_ID.equals(id)) create(clicker);
                else if (DISBAND_ITEM_ID.equals(id)) disband(clicker);
            }
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
        if (HUB_ITEM_ID.equals(id) || CREATE_ITEM_ID.equals(id) || DISBAND_ITEM_ID.equals(id)) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID leader = partyLeader(event.getPlayer().getUniqueId());
        if (leader == null) return;
        Player onlineLeader = Bukkit.getPlayer(leader);
        if (onlineLeader != null && onlineLeader.isOnline()) disband(onlineLeader);
        else forceDisband(leader);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { givePartyItems(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, () -> givePartyItems(event.getPlayer())); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { givePartyItems(event.getPlayer()); }

    public void givePartyItem(Player player) { givePartyItems(player); }

    public void givePartyItems(Player player) {
        if (!isSpawnWorld(player) || net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || net.dark.threecore.duels.DuelService.isDuelPlayer(player) || (dungeonService != null && dungeonService.isDungeonWorld(player.getWorld()))) { removePartyItems(player); return; }
        if (!configs.get("social/party.yml").getBoolean("party.item.enabled", true)) return;
        int hubSlot = Math.max(0, Math.min(8, configs.get("social/party.yml").getInt("party.item.slot", 8)));
        clearTaggedItem(player, HUB_ITEM_ID);
        clearTaggedItem(player, CREATE_ITEM_ID);
        clearTaggedItem(player, DISBAND_ITEM_ID);
        if (isInParty(player.getUniqueId())) {
            player.getInventory().setItem(hubSlot, createTagged(Material.LECTERN, configs.get("social/party.yml").getString("party.item.name", "<gradient:#34d399:#22c55e>Party Manager</gradient>"), HUB_ITEM_ID));
            if (isLeader(player.getUniqueId()) && hubSlot > 0) player.getInventory().setItem(hubSlot - 1, createTagged(Material.BARRIER, "<red>Disband Party</red>", DISBAND_ITEM_ID));
        } else {
            player.getInventory().setItem(hubSlot, createTagged(Material.GOAT_HORN, "<gradient:#34d399:#22c55e>Create Party</gradient>", CREATE_ITEM_ID));
        }
    }

    private void clearTaggedItem(Player player, String id) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            String existing = itemId(player.getInventory().getItem(i));
            if (id.equals(existing)) player.getInventory().setItem(i, null);
        }
    }

    private void removePartyItems(Player player) {
        clearTaggedItem(player, HUB_ITEM_ID);
        clearTaggedItem(player, CREATE_ITEM_ID);
        clearTaggedItem(player, DISBAND_ITEM_ID);
    }

    private boolean isSpawnWorld(Player player) {
        String configured = configs.get("core/config.yml").getString("spawn.world", "spawn");
        return player.getWorld() != null && (player.getWorld().getName().equalsIgnoreCase(configured) || player.getWorld().getName().equalsIgnoreCase("spawn"));
    }

    public void openInvitePicker(Player player, int page) {
        if (!isInParty(player.getUniqueId())) { Text.send(player, "<red>Create a party first using the hotbar goat horn.</red>"); return; }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.PARTY_MAIN, "invite:" + page), 54, "Invite Player");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createPlain(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " "));
        java.util.List<Player> candidates = inviteCandidates(player);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int start = page * slots.length;
        for (int i = 0; i < slots.length && start + i < candidates.size(); i++) inv.setItem(slots[i], head(candidates.get(start + i), "<green>Invite " + candidates.get(start + i).getName() + "</green>"));
        if (page > 0) inv.setItem(45, createPlain(Material.ARROW, "<gray>Previous Page</gray>"));
        if (start + slots.length < candidates.size()) inv.setItem(53, createPlain(Material.ARROW, "<gray>Next Page</gray>"));
        inv.setItem(49, createPlain(Material.BARRIER, "<red>Back</red>"));
        player.openInventory(inv);
    }

    public void handleInvitePickerClick(Player player, String context, int slot) {
        int page = 0;
        try { page = Integer.parseInt(context.substring("invite:".length())); } catch (Exception ignored) {}
        if (slot == 49) { openMenu(player); return; }
        if (slot == 45 && page > 0) { openInvitePicker(player, page - 1); return; }
        if (slot == 53) { openInvitePicker(player, page + 1); return; }
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int index = -1; for (int i = 0; i < slots.length; i++) if (slots[i] == slot) index = i;
        if (index < 0) return;
        java.util.List<Player> candidates = inviteCandidates(player);
        int targetIndex = page * slots.length + index;
        if (targetIndex >= candidates.size()) return;
        invite(player, candidates.get(targetIndex).getName());
        openInvitePicker(player, page);
    }

    private java.util.List<Player> inviteCandidates(Player player) {
        java.util.List<Player> base = new java.util.ArrayList<>(Bukkit.getOnlinePlayers()).stream().map(entry -> (Player) entry)
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> !partyMembers(player.getUniqueId()).contains(p.getUniqueId()))
                .sorted(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return friendService == null ? base : friendService.sortFriendsFirst(player, base);
    }

    public void openMembersMenu(Player player) {
        if (!isInParty(player.getUniqueId())) { Text.send(player, "<gray>You are not in a party.</gray>"); return; }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.PARTY_MAIN, "members"), 27, "Party Members");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createPlain(Material.GRAY_STAINED_GLASS_PANE, " "));
        int slot = 10;
        for (UUID uuid : partyMembers(player.getUniqueId())) {
            Player member = Bukkit.getPlayer(uuid);
            inv.setItem(slot++, member == null ? createPlain(Material.PLAYER_HEAD, "<gray>Offline member</gray>") : memberHead(member, (isLeader(uuid) ? "<gold>Leader: " : "<white>Member: ") + member.getName() + "</white>"));
            if (slot == 17) slot = 19;
            if (slot > 25) break;
        }
        inv.setItem(22, createPlain(Material.ARROW, "<gray>Back</gray>"));
        player.openInventory(inv);
    }

    public void handleMembersClick(Player player, int slot) { if (slot == 22) openMenu(player); }

    private ItemStack head(Player target, String name) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(List.of(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Click to select.</gray>")));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack memberHead(Player target, String name) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(List.of(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Party member.</gray>")));
        stack.setItemMeta(meta);
        return stack;
    }

    public void openPartyDuelMenu(Player player) {
        if (!isInParty(player.getUniqueId())) { Text.send(player, "<red>Create a party first.</red>"); return; }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.PARTY_MAIN, "party-duel"), 45, "Party Duel Setup");
        for (int i=0;i<inv.getSize();i++) inv.setItem(i, createPlain(Material.BLUE_STAINED_GLASS_PANE, " "));
        PartyDuelSetup setup = setup(player);
        inv.setItem(10, createPlain(Material.RED_WOOL, "<red>Red Team</red><gray>: </gray><white>" + teamNames(setup.red()) + "</white>"));
        inv.setItem(16, createPlain(Material.BLUE_WOOL, "<blue>Blue Team</blue><gray>: </gray><white>" + teamNames(setup.blue()) + "</white>"));
        inv.setItem(20, createPlain(selectedKitMaterial(setup), "<gradient:#60a5fa:#c084fc>Kit</gradient><gray>: </gray><white>" + selectedKitName(setup) + "</white>", List.of(
                "<gray>Click to change the duel kit.</gray>",
                "<gray>Selected kit:</gray> <white>" + selectedKitName(setup) + "</white>"
        )));
        inv.setItem(22, createPlain(Material.CLOCK, "<gradient:#f59e0b:#f97316>Rounds</gradient><gray>: " + setup.rounds()[0] + "</gray>", List.of(
                "<gray>Click to edit rounds via sign input.</gray>",
                "<gray>Selected rounds:</gray> <white>" + setup.rounds()[0] + "</white>"
        )));
        inv.setItem(24, createPlain(Material.MAP, "<gradient:#34d399:#22c55e>Map</gradient><gray>: </gray><white>" + selectedMapName(setup) + "</white>", List.of(
                "<gray>Click to choose a specific arena.</gray>",
                "<gray>Selected arena:</gray> <white>" + selectedMapName(setup) + "</white>"
        )));
        inv.setItem(40, createPlain(Material.LIME_DYE, "<green>Start Party Duel</green>"));
        inv.setItem(44, createPlain(Material.ARROW, "<gray>Back</gray>"));
        player.openInventory(inv);
    }

    public void handlePartyDuelClick(Player player, int slot) {
        if (!isInParty(player.getUniqueId())) {
            Text.send(player, "<red>Create a party first.</red>");
            openMenu(player);
            return;
        }
        switch (slot) {
            case 10 -> openPartyDuelMemberPicker(player, "red");
            case 16 -> openPartyDuelMemberPicker(player, "blue");
            case 20 -> openPartyDuelKitPicker(player);
            case 22 -> openRoundsSign(player);
            case 24 -> openPartyDuelMapPicker(player);
            case 40 -> startPartyDuel(player);
            case 44 -> openMenu(player);
            default -> { }
        }
    }


    public void openPartyDuelKitPicker(Player player) {
        if (duelService == null) { Text.send(player, "<red>Duel service is not ready.</red>"); return; }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.PARTY_MAIN, "party-duel-kits"), 54, "Party Duel Kit");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createPlain(Material.BLUE_STAINED_GLASS_PANE, " "));
        int[] slots = {10,12,14,16,20,22,24,28,30,32,34};
        int index = 0;
        PartyDuelSetup setup = setup(player);
        for (DuelKit kit : duelService.kitsView()) {
            if (!kit.enabled() || index >= slots.length) continue;
            boolean selected = kit.id().equalsIgnoreCase(setup.kitId()[0]);
            inv.setItem(slots[index++], createPlain(selected ? Material.LIME_DYE : kit.icon(), (selected ? "<green>Selected: </green>" : "") + kit.displayName(), List.of(
                    "<gray>Click to set this kit for the party duel.</gray>",
                    "<gray>Current selection:</gray> <white>" + selectedKitName(setup) + "</white>"
            )));
        }
        inv.setItem(49, createPlain(Material.ARROW, "<gray>Back</gray>"));
        player.openInventory(inv);
    }

    public void openPartyDuelMapPicker(Player player) {
        if (duelService == null) { Text.send(player, "<red>Duel service is not ready.</red>"); return; }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.PARTY_MAIN, "party-duel-maps"), 54, "Party Duel Map");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createPlain(Material.BLUE_STAINED_GLASS_PANE, " "));
        int[] slots = {10,12,14,16,20,22,24,28,30,32,34};
        int index = 0;
        PartyDuelSetup setup = setup(player);
        for (var map : duelService.enabledMapsView()) {
            if (index >= slots.length) break;
            boolean selected = map.id().equalsIgnoreCase(setup.mapId()[0]);
            inv.setItem(slots[index++], createPlain(selected ? Material.LIME_DYE : Material.MAP, (selected ? "<green>Selected: </green>" : "") + map.displayName(), List.of(
                    "<gray>Click to set this arena for the party duel.</gray>",
                    "<gray>Current selection:</gray> <white>" + selectedMapName(setup) + "</white>"
            )));
        }
        inv.setItem(49, createPlain(Material.ARROW, "<gray>Back</gray>"));
        player.openInventory(inv);
    }

    public void handlePartyDuelMapPickerClick(Player player, int slot) {
        if (slot == 49) { openPartyDuelMenu(player); return; }
        if (duelService == null) return;
        int[] slots = {10,12,14,16,20,22,24,28,30,32,34};
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) index = i;
        if (index < 0) return;
        List<net.dark.threecore.duels.model.DuelMap> enabled = duelService.enabledMapsView().stream().toList();
        if (index >= enabled.size()) return;
        PartyDuelSetup setup = setup(player);
        setup.mapId()[0] = enabled.get(index).id();
        Text.actionBar(player, "<gradient:#34d399:#22c55e>Party duel map:</gradient> <white>" + enabled.get(index).displayName() + "</white>");
        openPartyDuelMenu(player);
    }

    public void handlePartyDuelKitPickerClick(Player player, int slot) {
        if (slot == 49) { openPartyDuelMenu(player); return; }
        if (duelService == null) return;
        int[] slots = {10,12,14,16,20,22,24,28,30,32,34};
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) index = i;
        if (index < 0) return;
        List<DuelKit> enabled = duelService.kitsView().stream().filter(DuelKit::enabled).toList();
        if (index >= enabled.size()) return;
        PartyDuelSetup setup = setup(player);
        setup.kitId()[0] = enabled.get(index).id();
        Text.actionBar(player, "<gradient:#60a5fa:#c084fc>Party duel kit:</gradient> <white>" + enabled.get(index).displayName() + "</white>");
        openPartyDuelMenu(player);
    }

    private void startPartyDuel(Player player) {
        PartyDuelSetup setup = setup(player);
        if (duelService == null) { Text.send(player, "<red>Duel service is not ready.</red>"); return; }
        if (duelService.startConfiguredPartyDuel(player, setup.red(), setup.blue(), setup.kitId()[0], setup.rounds()[0], setup.mapId()[0])) {
            Text.send(player, "<green>Party duel starting.</green>");
            player.closeInventory();
        }
    }

    private void openRoundsSign(Player player) {
        PartyDuelSetup setup = setup(player);
        UUID leader = partyLeader(player.getUniqueId());
        if (leader == null) leader = player.getUniqueId();
        org.bukkit.Location signLocation = player.getLocation().getBlock().getLocation().add(0, 1, 0);
        var block = signLocation.getBlock();
        var previous = block.getBlockData().clone();
        block.setType(Material.OAK_SIGN);
        pendingRoundsEdits.put(player.getUniqueId(), new PendingRoundsEdit(leader, signLocation, previous));
        try {
            if (block.getState() instanceof Sign sign) {
                sign.getSide(Side.FRONT).setLine(0, "------");
                sign.getSide(Side.FRONT).setLine(1, String.valueOf(setup.rounds()[0]));
                sign.getSide(Side.FRONT).setLine(2, "rounds");
                sign.getSide(Side.FRONT).setLine(3, "------");
                sign.update(true, false);
                player.openSign(sign, Side.FRONT);
                Text.send(player, "<gray>Type the round amount on the sign line under the top dashes.</gray>");
            }
        } catch (Throwable ex) {
            pendingRoundsEdits.remove(player.getUniqueId());
            block.setBlockData(previous);
            Text.send(player, "<red>Could not open sign editor.</red>");
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        PendingRoundsEdit edit = pendingRoundsEdits.remove(event.getPlayer().getUniqueId());
        if (edit == null) return;
        if (!edit.location().equals(event.getBlock().getLocation())) return;
        try {
            int rounds = parseSignInteger(event, 15);
            PartyDuelSetup setup = duelSetups.computeIfAbsent(edit.leader(), ignored -> new PartyDuelSetup(new LinkedHashSet<>(), new LinkedHashSet<>(), new int[]{rounds}, new String[]{defaultKitId()}, new String[]{defaultMapId()}));
            setup.rounds()[0] = rounds;
            event.getBlock().setBlockData(edit.previous());
            Text.send(event.getPlayer(), "<green>Party duel rounds set:</green> <white>" + rounds + "</white>");
            Bukkit.getScheduler().runTask(plugin, () -> openPartyDuelMenu(event.getPlayer()));
        } catch (Exception ex) {
            event.getBlock().setBlockData(edit.previous());
            Text.send(event.getPlayer(), "<red>Invalid round amount.</red>");
        }
    }
    public void openPartyDuelMemberPicker(Player player, String team) {
        if (!isInParty(player.getUniqueId())) { Text.send(player, "<red>Create a party first.</red>"); return; }
        String normalized = team.equalsIgnoreCase("blue") ? "blue" : "red";
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.PARTY_MAIN, "party-duel-members:" + normalized), 27, normalized.equals("red") ? "Select Red Team" : "Select Blue Team");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createPlain(normalized.equals("red") ? Material.RED_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE, " "));
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int index = 0;
        PartyDuelSetup setup = setup(player);
        for (UUID uuid : partyMembers(player.getUniqueId())) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null || index >= slots.length) continue;
            boolean selected = normalized.equals("red") ? setup.red().contains(uuid) : setup.blue().contains(uuid);
            boolean other = normalized.equals("red") ? setup.blue().contains(uuid) : setup.red().contains(uuid);
            String name = (selected ? "<green>Selected: " : other ? "<yellow>Other Team: " : "<white>") + member.getName() + "</white>";
            inv.setItem(slots[index++], memberHead(member, name));
        }
        inv.setItem(22, createPlain(Material.ARROW, "<gray>Back</gray>"));
        player.openInventory(inv);
    }

    public void handlePartyDuelMemberPickerClick(Player player, String context, int slot) {
        String team = context.substring("party-duel-members:".length()).equalsIgnoreCase("blue") ? "blue" : "red";
        if (slot == 22) { openPartyDuelMenu(player); return; }
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) index = i;
        if (index < 0) return;
        List<UUID> members = new ArrayList<>(partyMembers(player.getUniqueId()));
        if (index >= members.size()) return;
        UUID target = members.get(index);
        PartyDuelSetup setup = setup(player);
        Set<UUID> selected = team.equals("red") ? setup.red() : setup.blue();
        Set<UUID> other = team.equals("red") ? setup.blue() : setup.red();
        if (selected.remove(target)) {
            Text.send(player, "<gray>Removed " + playerName(target) + " from " + team + " team.</gray>");
        } else {
            other.remove(target);
            selected.add(target);
            Text.send(player, (team.equals("red") ? "<red>" : "<blue>") + "Added " + playerName(target) + " to " + team + " team.</" + team + ">");
        }
        openPartyDuelMemberPicker(player, team);
    }

    private PartyDuelSetup setup(Player player) {
        UUID leader = partyLeader(player.getUniqueId());
        UUID key = leader == null ? player.getUniqueId() : leader;
        PartyDuelSetup setup = duelSetups.computeIfAbsent(key, ignored -> new PartyDuelSetup(new LinkedHashSet<>(), new LinkedHashSet<>(), new int[]{3}, new String[]{defaultKitId()}, new String[]{defaultMapId()}));
        autoAssignTwoMemberParty(player, setup);
        return setup;
    }

    private int parseSignInteger(SignChangeEvent event, int max) {
        for (int i = 0; i < 4; i++) {
            String line = event.getLine(i);
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.equals("------") || trimmed.equalsIgnoreCase("rounds")) continue;
            try {
                return Math.max(1, Math.min(max, Integer.parseInt(trimmed)));
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("No valid integer on sign");
    }
    private void autoAssignTwoMemberParty(Player player, PartyDuelSetup setup) {
        if (!setup.red().isEmpty() || !setup.blue().isEmpty()) return;
        List<UUID> members = new ArrayList<>(partyMembers(player.getUniqueId()));
        if (members.size() != 2) return;
        setup.red().add(members.get(0));
        setup.blue().add(members.get(1));
    }

    private String defaultKitId() {
        if (duelService == null) return "sword";
        return duelService.kitsView().stream().filter(DuelKit::enabled).map(DuelKit::id).findFirst().orElse("sword");
    }

    private org.bukkit.Material selectedKitMaterial(PartyDuelSetup setup) {
        if (duelService == null) return Material.DIAMOND_SWORD;
        DuelKit kit = duelService.kit(setup.kitId()[0]);
        return kit == null ? Material.DIAMOND_SWORD : kit.icon();
    }

    private String defaultMapId() {
        if (duelService == null) return "";
        return duelService.enabledMapsView().stream().findFirst().map(net.dark.threecore.duels.model.DuelMap::id).orElse("");
    }

    private String selectedMapName(PartyDuelSetup setup) {
        if (duelService == null || setup.mapId()[0].isBlank()) return "random";
        var map = duelService.map(setup.mapId()[0]);
        return map == null ? "random" : map.displayName();
    }

    private String selectedKitName(PartyDuelSetup setup) {
        if (duelService == null) return setup.kitId()[0];
        DuelKit kit = duelService.kit(setup.kitId()[0]);
        return kit == null ? setup.kitId()[0] : kit.displayName();
    }
    private String teamNames(Set<UUID> uuids) {
        if (uuids.isEmpty()) return "none";
        List<String> names = new ArrayList<>();
        for (UUID uuid : uuids) names.add(playerName(uuid));
        return String.join(", ", names);
    }

    private String playerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    private ItemStack createPlain(Material material, String name) {
        return createPlain(material, name, List.of());
    }

    private ItemStack createPlain(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(lore.stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        stack.setItemMeta(meta);
        return stack;
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

    private void sendInviteMessage(Player target, String inviterName) {
        Component line = Text.mm("<gradient:#34d399:#22c55e>Party Invite</gradient> <gray>from</gray> <white>" + inviterName + "</white> ");
        Component accept = Text.mm("<green>[ACCEPT]</green>")
                .clickEvent(ClickEvent.runCommand("/party accept"))
                .hoverEvent(HoverEvent.showText(Text.mm("<gray>Click to join the party.</gray>")));
        Component deny = Text.mm("<red>[DENY]</red>")
                .clickEvent(ClickEvent.runCommand("/party deny"))
                .hoverEvent(HoverEvent.showText(Text.mm("<gray>Click to deny the invite.</gray>")));
        target.sendMessage(line.append(accept).append(Component.space()).append(deny));
    }

    private void forceDisband(UUID leader) {
        PartyData party = partiesByLeader.remove(leader);
        if (party == null) return;
        for (UUID member : party.members()) {
            leaderByMember.remove(member);
            Player target = Bukkit.getPlayer(member);
            if (target != null && target.isOnline()) {
                givePartyItems(target);
                Text.send(target, "<red>Party disbanded because a member left the server.</red>");
            }
        }
    }
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

    private record PartyDuelSetup(Set<UUID> red, Set<UUID> blue, int[] rounds, String[] kitId, String[] mapId) {}
    private record PendingRoundsEdit(UUID leader, org.bukkit.Location location, org.bukkit.block.data.BlockData previous) {}
}
















