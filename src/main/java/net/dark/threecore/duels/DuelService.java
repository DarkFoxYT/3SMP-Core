package net.dark.threecore.duels;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.gui.DuelMenu;
import net.dark.threecore.duels.DuelLeaderboardService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.duels.model.DuelKit;
import net.dark.threecore.duels.model.DuelMap;
import net.dark.threecore.duels.model.DuelMatch;
import net.dark.threecore.duels.model.DuelMode;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.launchpads.LaunchpadService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.title.Title;
import java.time.Duration;

import java.util.*;

public final class DuelService implements Listener {
    private static final String ITEM_ID_KEY = "3smpcore_duel_item";
    private static final String QUEUE_ITEM_ID = "queue_sword";
    private static final String PARTY_ITEM_ID = "party_horn";

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MenuService menuService;
    private final PartyService partyService;
    private final DuelLeaderboardService leaderboardService;
    private final LaunchpadService launchpadService;
    private final Map<String, DuelKit> kits = new LinkedHashMap<>();
    private final Map<Integer, String> kitSlots = new HashMap<>();
    private final Map<String, DuelMap> maps = new LinkedHashMap<>();
    private final Map<UUID, QueueUnit> queueByPlayer = new HashMap<>();
    private final Map<UUID, QueueUnit> queueUnits = new HashMap<>();
    private final Map<String, Deque<UUID>> soloQueues = new HashMap<>();
    private final Map<String, Deque<UUID>> partyQueues = new HashMap<>();
    private final Map<UUID, DuelMatch> matchesByPlayer = new HashMap<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Set<UUID> devMode = new HashSet<>();
    private final Map<UUID, DuelChallenge> challengesByTarget = new HashMap<>();
    private final Map<UUID, DuelChallenge> challengesByChallenger = new HashMap<>();
    private BukkitTask hudTask;
    private boolean enabled = true;

    public DuelService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService, PartyService partyService, DuelLeaderboardService leaderboardService, LaunchpadService launchpadService) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
        this.partyService = partyService;
        this.leaderboardService = leaderboardService;
        this.launchpadService = launchpadService;
        reload();
        startHudTask();
    }

    public void reload() {
        kits.clear();
        kitSlots.clear();
        maps.clear();
        loadKits();
        loadMaps();
        enabled = configs.get("duels.yml").getBoolean("duels.enabled", true);
    }

    public void handle(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        String sub = context.arg(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "", "menu" -> openMainMenu(player);
            case "accept" -> acceptChallenge(player);
            case "deny", "decline" -> denyChallenge(player);
            case "leaderboard", "top" -> openLeaderboard(player);
            case "challenge" -> challenge(player, context.arg(1), context.arg(2), context.arg(3));
            case "queue" -> {
                if (context.args().length > 1 && context.arg(1).equalsIgnoreCase("party")) queueParty(player);
                else openKitMenu(player);
            }
            case "leave" -> leaveQueue(player, false);
            case "kiteditor", "devpanel", "mapeditor" -> openDevMenu(player);
            case "test" -> runTestDuel(player);
            case "admin" -> handleAdmin(player, context);
            case "map", "arena" -> handleMap(player, context);
            default -> {
                Player target = Bukkit.getPlayerExact(context.arg(0));
                if (target != null) challenge(player, target.getName(), context.arg(1), context.arg(2));
                else Text.send(player, "<yellow>Use /duel <player>, accept, deny, menu, queue, leaderboard, devpanel or map.</yellow>");
            }
        }
    }

    public List<String> complete(CommandContext context) {
        if (context.args().length <= 1) return List.of("menu", "challenge", "accept", "deny", "queue", "leave", "leaderboard", "test", "kiteditor", "devpanel", "mapeditor", "map", "arena", "admin");
        if (context.arg(0).equalsIgnoreCase("queue") && context.args().length <= 2) return List.of("party");
        if (context.arg(0).equalsIgnoreCase("map") && context.args().length <= 2) return List.of("create", "setlobby", "setspawna", "setspawnb", "setspec", "save", "list", "enable", "disable");
        if (context.arg(0).equalsIgnoreCase("test") && context.args().length <= 2) return List.of("arena");
        if (context.arg(0).equalsIgnoreCase("admin") && context.args().length <= 2) return List.of("reload", "toggle");
        return List.of();
    }

    public Collection<DuelKit> kits() { return kits.values(); }
    public Collection<DuelMap> maps() { return maps.values(); }
    public boolean isQueuedForKit(UUID uuid, String kitId) { QueueUnit unit = queueByPlayer.get(uuid); return unit != null && unit.kitId().equalsIgnoreCase(kitId); }
    public boolean isPlayerInDuel(UUID uuid) { return matchesByPlayer.containsKey(uuid); }

    public void openMainMenu(Player player) { menuService.open(player, new DuelMenu(this).buildMain(player)); }
    public void openSummary(Player player) { menuService.open(player, new DuelMenu(this).buildSummary(player)); }

    public void openKitMenu(Player player) { menuService.open(player, new DuelMenu(this).buildKitMenu(player)); }
    public void openDevMenu(Player player) { menuService.open(player, new DuelMenu(this).buildDev(player)); }
    public void openLeaderboard(Player player) { leaderboardService.open(player); }

    public void toggleDev(Player player) {
        if (!devMode.add(player.getUniqueId())) {
            devMode.remove(player.getUniqueId());
            Text.send(player, "<yellow>Duel dev mode disabled.</yellow>");
        } else {
            Text.send(player, "<green>Duel dev mode enabled.</green>");
        }
    }

    public void handleMainMenuClick(Player player, int slot) {
        if (slot == 7) openSummary(player);
        else if (slot == 10) openKitMenu(player);
        else if (slot == 16) queueParty(player);
    }

    public void handleSummaryClick(Player player, int slot) {
        if (slot == 22) openMainMenu(player);
        else if (slot == 11) openKitMenu(player);
    }

    public void handleKitMenuClick(Player player, int slot) {
        if (slot == 45) { openMainMenu(player); return; }
        String kitId = kitSlots.get(slot);
        if (kitId == null) return;
        if (isQueuedForKit(player.getUniqueId(), kitId)) leaveQueue(player, false);
        else queueSolo(player, kitId);
    }

    public void handleMenuClick(Player player, int slot) { handleMainMenuClick(player, slot); }
    public int soloQueueCount() { return soloQueues.values().stream().mapToInt(Deque::size).sum(); }
    public int partyQueueCount() { return partyQueues.values().stream().mapToInt(Deque::size).sum(); }
    public int kitCount() { return kits.size(); }
    public int mapCount() { return maps.size(); }
    public boolean isEnabled() { return enabled; }
    public boolean isDevEnabled(UUID uuid) { return devMode.contains(uuid); }
    public void handleDevMenuClick(Player player, int slot) {
        if (slot == 11) runTestDuel(player);
        else if (slot == 13) openMapEditor(player);
        else if (slot == 15) openLeaderboard(player);
        else if (slot == 17 && launchpadService != null) launchpadService.openMenu(player);
    }

    public void queueSolo(Player player, String kitId) {
        if (!enabled) { Text.send(player, "<red>Duels are currently disabled.</red>"); return; }
        if (matchesByPlayer.containsKey(player.getUniqueId())) { Text.send(player, "<red>You are already in a duel.</red>"); return; }
        if (queueByPlayer.containsKey(player.getUniqueId())) leaveQueue(player, true);
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null || !kit.enabled()) { Text.send(player, "<red>That kit is unavailable.</red>"); return; }
        QueueUnit unit = new QueueUnit(UUID.randomUUID(), DuelMode.SOLO, kitId, Set.of(player.getUniqueId()), System.currentTimeMillis());
        queueUnits.put(unit.unitId(), unit);
        queueByPlayer.put(player.getUniqueId(), unit);
        soloQueues.computeIfAbsent(kitId, ignored -> new ArrayDeque<>()).add(unit.unitId());
        giveQueueItem(player);
        Text.actionBar(player, "<gradient:#60a5fa:#c084fc>Queued for 1v1</gradient> <gray>" + kit.displayName() + "</gray>");
        Text.send(player, "<green>Queued for 1v1 using " + kit.displayName() + "</green>");
        tryMatchAll();
    }

    public void queueParty(Player player) {
        if (!enabled) { Text.send(player, "<red>Duels are currently disabled.</red>"); return; }
        if (matchesByPlayer.containsKey(player.getUniqueId())) { Text.send(player, "<red>You are already in a duel.</red>"); return; }
        UUID leader = partyService.partyLeader(player.getUniqueId());
        if (leader == null) { Text.send(player, "<red>You need a party to queue party duels.</red>"); return; }
        if (!leader.equals(player.getUniqueId())) { Text.send(player, "<red>Only the party leader can queue party duels.</red>"); return; }
        Set<UUID> members = partyService.partyMembers(player.getUniqueId());
        if (members.size() < 2 || members.size() > 3) { Text.send(player, "<red>Your party must have 2 or 3 members for party duels.</red>"); return; }
        if (members.stream().anyMatch(queueByPlayer::containsKey)) { Text.send(player, "<yellow>Your party is already queued.</yellow>"); return; }
        String kitId = selectedKitForParty();
        if (kitId == null) { Text.send(player, "<red>No enabled kits are configured.</red>"); return; }
        QueueUnit unit = new QueueUnit(UUID.randomUUID(), DuelMode.PARTY, kitId, new LinkedHashSet<>(members), System.currentTimeMillis());
        queueUnits.put(unit.unitId(), unit);
        for (UUID member : members) queueByPlayer.put(member, unit);
        partyQueues.computeIfAbsent(kitId, ignored -> new ArrayDeque<>()).add(unit.unitId());
        notifyMembers(members, "<gradient:#34d399:#22c55e>Queued for " + members.size() + "v" + members.size() + "</gradient> <gray>" + kits.get(kitId).displayName() + "</gray>");
        tryMatchAll();
    }

    public void leaveQueue(Player player, boolean silent) {
        QueueUnit unit = queueByPlayer.get(player.getUniqueId());
        if (unit == null) {
            if (!silent) Text.send(player, "<gray>You are not queued.</gray>");
            return;
        }
        removeQueueUnit(unit, true);
        if (!silent) Text.send(player, "<gray>Left the duel queue.</gray>");
    }

    public void challenge(Player challenger, String targetName, String kitIdInput, String mapIdInput) {
        if (!enabled) { Text.send(challenger, "<red>Duels are currently disabled.</red>"); return; }
        if (targetName == null || targetName.isBlank()) { Text.send(challenger, "<red>Usage: /duel <player> [kit] [arena]</red>"); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || target.equals(challenger)) { Text.send(challenger, "<red>Player not found.</red>"); return; }
        if (matchesByPlayer.containsKey(challenger.getUniqueId()) || matchesByPlayer.containsKey(target.getUniqueId())) { Text.send(challenger, "<red>One of you is already in a duel.</red>"); return; }
        String kitId = resolveKit(kitIdInput);
        String mapId = resolveMap(mapIdInput);
        if (kitId == null || mapId == null) { Text.send(challenger, "<red>No valid kit or arena configured.</red>"); return; }
        Set<UUID> challengers = challengeTeam(challenger);
        Set<UUID> targets = challengeTeam(target);
        if (challengers.size() != targets.size()) { Text.send(challenger, "<red>Both sides need the same party size.</red>"); return; }
        DuelMode mode = challengers.size() == 1 ? DuelMode.SOLO : DuelMode.PARTY;
        DuelChallenge challenge = new DuelChallenge(challenger.getUniqueId(), target.getUniqueId(), challengers, targets, kitId, mapId, mode, System.currentTimeMillis());
        challengesByTarget.put(target.getUniqueId(), challenge);
        challengesByChallenger.put(challenger.getUniqueId(), challenge);
        Text.send(challenger, "<green>Challenge sent to " + target.getName() + ".</green>");
        Text.send(target, "<gradient:#60a5fa:#c084fc>" + challenger.getName() + " challenged you</gradient> <gray>Kit: " + kits.get(kitId).displayName() + " | Arena: " + maps.get(mapId).displayName() + "</gray>");
        Text.send(target, "<gray>Use <white>/duel accept</white> or <white>/duel deny</white>.</gray>");
    }

    public void acceptChallenge(Player target) {
        DuelChallenge challenge = challengesByTarget.remove(target.getUniqueId());
        if (challenge == null) { Text.send(target, "<gray>You have no pending duel challenge.</gray>"); return; }
        challengesByChallenger.remove(challenge.challenger());
        long timeout = configs.get("duels.yml").getLong("duels.challenges.timeout-seconds", 60L) * 1000L;
        if (System.currentTimeMillis() - challenge.createdAt() > timeout) { Text.send(target, "<red>That challenge expired.</red>"); return; }
        startMatch(new QueueUnit(UUID.randomUUID(), challenge.mode(), challenge.kitId(), challenge.challengers(), System.currentTimeMillis()), new QueueUnit(UUID.randomUUID(), challenge.mode(), challenge.kitId(), challenge.targets(), System.currentTimeMillis()), maps.get(challenge.mapId()));
    }

    public void denyChallenge(Player target) {
        DuelChallenge challenge = challengesByTarget.remove(target.getUniqueId());
        if (challenge == null) { Text.send(target, "<gray>You have no pending duel challenge.</gray>"); return; }
        challengesByChallenger.remove(challenge.challenger());
        Text.send(target, "<gray>Challenge denied.</gray>");
        Player challenger = Bukkit.getPlayer(challenge.challenger());
        if (challenger != null) Text.send(challenger, "<red>Challenge denied.</red>");
    }

    private Set<UUID> challengeTeam(Player player) {
        UUID leader = partyService.partyLeader(player.getUniqueId());
        if (leader == null || !leader.equals(player.getUniqueId())) return Set.of(player.getUniqueId());
        Set<UUID> members = partyService.partyMembers(player.getUniqueId());
        return members.size() >= 2 && members.size() <= 3 ? new LinkedHashSet<>(members) : Set.of(player.getUniqueId());
    }

    public void runTestDuel(Player player) {
        if (!enabled) {
            Text.send(player, "<red>Duels are currently disabled.</red>");
            return;
        }
        Text.send(player, "<green>Starting duel test sequence...</green>");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openKitMenu(player);
            Text.send(player, "<gray>Pick a kit to simulate the real duel flow.</gray>");
        }, 20L);
    }
    public void handleMapEditorClick(Player player, int slot) {
        switch (slot) {
            case 10 -> Text.send(player, "<gray>Stand where you want the lobby and run /duel map setlobby <mapId></gray>");
            case 11 -> Text.send(player, "<gray>Stand where you want spawn A and run /duel map setspawna <mapId></gray>");
            case 12 -> Text.send(player, "<gray>Stand where you want spawn B and run /duel map setspawnb <mapId></gray>");
            case 13 -> Text.send(player, "<gray>Stand where you want spectator and run /duel map setspec <mapId></gray>");
            case 15 -> Text.send(player, "<gray>Use /duel map save <mapId> to persist changes.</gray>");
            case 26 -> openDevMenu(player);
        }
    }
    public void openMapEditor(Player player) {
        menuService.open(player, createMapEditor(player));
    }

    private org.bukkit.inventory.Inventory createMapEditor(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "map-editor"), 27, "3SMP Map Editor");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createTagged(Material.GRAY_STAINED_GLASS_PANE, " ", "map_editor_filler"));
        inv.setItem(10, createTagged(Material.LECTERN, "<gradient:#60a5fa:#c084fc>Set Lobby</gradient>", "map_set_lobby"));
        inv.setItem(11, createTagged(Material.DIAMOND_SWORD, "<gradient:#34d399:#22c55e>Set Spawn A</gradient>", "map_set_spawn_a"));
        inv.setItem(12, createTagged(Material.SHIELD, "<gradient:#f59e0b:#f97316>Set Spawn B</gradient>", "map_set_spawn_b"));
        inv.setItem(13, createTagged(Material.COMPASS, "<gradient:#a78bfa:#f472b6>Set Spectator</gradient>", "map_set_spectator"));
        inv.setItem(15, createTagged(Material.SUNFLOWER, "<gradient:#22d3ee:#8b5cf6>Save Map</gradient>", "map_save"));
        inv.setItem(26, createTagged(Material.BARRIER, "<red>Close</red>", "map_close"));
        return inv;
    }
    public void giveQueueItem(Player player) {
        if (!configs.get("duels.yml").getBoolean("duels.queue-item.enabled", true)) return;
        int slot = configs.get("duels.yml").getInt("duels.queue-item.slot", 0);
        player.getInventory().setItem(slot, createTagged(Material.DIAMOND_SWORD, configs.get("duels.yml").getString("duels.queue-item.name", "<gradient:#60a5fa:#c084fc>Duel Queue</gradient>"), QUEUE_ITEM_ID));
    }

    public void givePartyItem(Player player) {
        if (!configs.get("party.yml").getBoolean("party.item.enabled", true)) return;
        int slot = Math.max(0, Math.min(8, configs.get("party.yml").getInt("party.item.slot", 8)));
        player.getInventory().setItem(slot, createTagged(Material.LECTERN, configs.get("party.yml").getString("party.item.name", "<gradient:#34d399:#22c55e>Party Manager</gradient>"), PARTY_ITEM_ID));
    }

    public void reloadItems(Player player) {
        giveQueueItem(player);
        if (player.hasPermission("3smpcore.party.use")) givePartyItem(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = itemId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        if (QUEUE_ITEM_ID.equals(id)) openMainMenu(event.getPlayer());
        else if (PARTY_ITEM_ID.equals(id)) event.getPlayer().performCommand("/party");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { reloadItems(event.getPlayer()); }
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, () -> reloadItems(event.getPlayer())); }
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) { reloadItems(event.getPlayer()); }
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        String id = itemId(event.getItemDrop().getItemStack());
        if (QUEUE_ITEM_ID.equals(id) || PARTY_ITEM_ID.equals(id)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String id = itemId(event.getCurrentItem());
        if (id == null) return;
        if (QUEUE_ITEM_ID.equals(id) || PARTY_ITEM_ID.equals(id)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == player.getInventory()) {
                if (QUEUE_ITEM_ID.equals(id)) openMainMenu(player);
                else player.performCommand("party");
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        DuelMatch match = matchesByPlayer.get(event.getEntity().getUniqueId());
        if (match == null) return;
        Set<UUID> winners = match.teamOne().contains(event.getEntity().getUniqueId()) ? match.teamTwo() : match.teamOne();
        endMatch(match, winners, "death");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        QueueUnit unit = queueByPlayer.get(event.getPlayer().getUniqueId());
        if (unit != null) removeQueueUnit(unit, false);
        DuelMatch match = matchesByPlayer.get(event.getPlayer().getUniqueId());
        if (match != null) {
            Set<UUID> winners = match.teamOne().contains(event.getPlayer().getUniqueId()) ? match.teamTwo() : match.teamOne();
            endMatch(match, winners, "quit");
        }
    }

    private void handleMap(Player player, CommandContext context) {
        if (!player.hasPermission("3smpcore.duel.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        String sub = context.arg(1).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> Text.send(player, "<gray>Maps: " + String.join(", ", maps.keySet()) + "</gray>");
            case "create" -> {
                String id = context.arg(2).toLowerCase(Locale.ROOT);
                if (id.isBlank()) { Text.send(player, "<red>Usage: /duel map create <id></red>"); return; }
                maps.put(id, new DuelMap(id, id, true, player.getWorld().getName(), player.getLocation(), player.getLocation(), player.getLocation(), player.getLocation()));
                saveMaps();
                Text.send(player, "<green>Map created: " + id + "</green>");
            }
            case "setlobby", "setspawna", "setspawnb", "setspec" -> {
                String id = context.arg(2).toLowerCase(Locale.ROOT);
                DuelMap map = maps.get(id);
                if (map == null) { Text.send(player, "<red>Map not found.</red>"); return; }
                DuelMap updated = switch (sub) {
                    case "setlobby" -> map.withLobby(player.getLocation());
                    case "setspawna" -> map.withSpawnA(player.getLocation());
                    case "setspawnb" -> map.withSpawnB(player.getLocation());
                    default -> map.withSpectator(player.getLocation());
                };
                maps.put(id, updated);
                Text.send(player, "<green>Updated " + sub + " for " + id + "</green>");
            }
            case "save" -> { saveMaps(); Text.send(player, "<green>Maps saved.</green>"); }
            case "enable", "disable" -> {
                String id = context.arg(2).toLowerCase(Locale.ROOT);
                DuelMap map = maps.get(id);
                if (map == null) { Text.send(player, "<red>Map not found.</red>"); return; }
                maps.put(id, map.withEnabled(sub.equals("enable")));
                saveMaps();
                Text.send(player, "<green>Map " + id + " updated.</green>");
            }
            default -> Text.send(player, "<gray>Use: create, setlobby, setspawna, setspawnb, setspec, save, list, enable, disable</gray>");
        }
    }
    private void handleAdmin(Player player, CommandContext context) {
        if (!player.hasPermission("3smpcore.duel.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        switch (context.arg(1).toLowerCase(Locale.ROOT)) {
            case "reload" -> { reload(); Text.send(player, "<green>Duel module reloaded.</green>"); }
            case "toggle" -> { enabled = !enabled; Text.send(player, enabled ? "<green>Duels enabled.</green>" : "<red>Duels disabled.</red>"); }
            default -> Text.send(player, "<gray>Admin subcommands: reload, toggle</gray>");
        }
    }

    private void saveMaps() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (var entry : maps.entrySet()) {
            DuelMap map = entry.getValue();
            String path = "maps." + entry.getKey();
            yaml.set(path + ".display-name", map.displayName());
            yaml.set(path + ".enabled", map.enabled());
            yaml.set(path + ".world", map.worldName());
            writeLocation(yaml, path + ".lobby", map.lobby());
            writeLocation(yaml, path + ".spawn-a", map.spawnA());
            writeLocation(yaml, path + ".spawn-b", map.spawnB());
            writeLocation(yaml, path + ".spectator", map.spectator());
        }
        try { yaml.save(new java.io.File(plugin.getDataFolder(), "duel-maps.yml")); } catch (Exception e) { throw new IllegalStateException("Failed to save duel maps", e); }
    }

    private void writeLocation(org.bukkit.configuration.file.YamlConfiguration yaml, String path, Location location) {
        if (location == null) return;
        yaml.set(path + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }
    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof CoreMenuHolder holder)) continue;
            if (holder.type() == CoreMenuType.DUEL_KITS) {
                menuService.open(player, new DuelMenu(this).buildKitMenu(player));
            } else if (holder.type() == CoreMenuType.DUEL_MAIN) {
                menuService.open(player, new DuelMenu(this).buildMain(player));
            } else if (holder.type() == CoreMenuType.DUEL_DEV) {
                menuService.open(player, new DuelMenu(this).buildDev(player));
            }
        }
    }
    private void startHudTask() {
        if (hudTask != null) hudTask.cancel();
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queueUnits.isEmpty()) return;
            tryMatchAll();
            refreshOpenMenus();
            long now = System.currentTimeMillis();
            for (QueueUnit unit : new HashSet<>(queueUnits.values())) {
                String elapsed = formatDuration(now - unit.joinedAt());
                String kitName = kits.containsKey(unit.kitId().toLowerCase(Locale.ROOT)) ? kits.get(unit.kitId().toLowerCase(Locale.ROOT)).displayName() : unit.kitId();
                String modeLabel = unit.mode() == DuelMode.SOLO ? "1v1" : "2v2";
                String msg = "<gradient:#60a5fa:#c084fc>Queued " + modeLabel + "</gradient> <gray>" + kitName + "</gray> <dark_gray>|</dark_gray> <white>" + elapsed + "</white>";
                notifyMembers(unit.members(), msg);
            }
        }, 20L, 20L);
    }

    private void tryMatchAll() {
        matchQueues(DuelMode.SOLO);
        matchQueues(DuelMode.PARTY);
    }

    private void matchQueues(DuelMode mode) {
        Map<String, List<QueueUnit>> grouped = new HashMap<>();
        for (QueueUnit unit : queueUnits.values()) {
            if (unit.mode() == mode) grouped.computeIfAbsent(unit.kitId().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(unit);
        }
        for (Map.Entry<String, List<QueueUnit>> entry : grouped.entrySet()) {
            List<QueueUnit> list = entry.getValue();
            while (list.size() >= 2) {
                QueueUnit pairA = selectBestUnit(list);
                if (pairA == null) break;
                list.remove(pairA);
                QueueUnit pairB = selectMatchFor(list, pairA);
                if (pairB == null) break;
                list.remove(pairB);
                startMatch(pairA, pairB);
            }
        }
    }

    private QueueUnit selectBestUnit(List<QueueUnit> units) {
        return units.stream().min(Comparator.comparingLong(QueueUnit::joinedAt)).orElse(null);
    }

    private QueueUnit selectMatchFor(List<QueueUnit> units, QueueUnit anchor) {
        if (units.isEmpty()) return null;
        long waitedMs = System.currentTimeMillis() - anchor.joinedAt();
        if (waitedMs >= 60_000L) return units.get(0);
        int anchorRating = unitRating(anchor);
        QueueUnit best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (QueueUnit candidate : units) {
            int diff = Math.abs(anchorRating - unitRating(candidate));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = candidate;
            }
        }
        return best;
    }

    private int unitRating(QueueUnit unit) {
        if (unit.mode() == DuelMode.SOLO) return ratingOf(unit.members().iterator().next());
        int total = 0;
        for (UUID member : unit.members()) total += ratingOf(member);
        return unit.members().isEmpty() ? 1000 : total / unit.members().size();
    }

    private int ratingOf(UUID uuid) { return repository.load(uuid).duelRating(); }

    private void startMatch(QueueUnit first, QueueUnit second) {
        DuelMap map = pickMap();
        startMatch(first, second, map);
    }

    private void startMatch(QueueUnit first, QueueUnit second, DuelMap map) {
        if (map == null || map.spawnA() == null || map.spawnB() == null) {
            notifyMembers(first.members(), "<red>No valid duel map configured.</red>");
            notifyMembers(second.members(), "<red>No valid duel map configured.</red>");
            removeQueueUnit(first, false);
            removeQueueUnit(second, false);
            return;
        }
        DuelMatch match = new DuelMatch(UUID.randomUUID(), first.mode(), first.kitId(), map.id(), first.members(), second.members(), System.currentTimeMillis());
        first.members().forEach(member -> matchesByPlayer.put(member, match));
        second.members().forEach(member -> matchesByPlayer.put(member, match));
        removeQueueUnit(first, false);
        removeQueueUnit(second, false);
        notifyMembers(match.teamOne(), "<green>Match found. Starting on " + map.displayName() + "</green>");
        notifyMembers(match.teamTwo(), "<green>Match found. Starting on " + map.displayName() + "</green>");
        int seconds = Math.max(1, configs.get("duels.yml").getInt("duels.countdown.seconds", 5));
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;
            @Override public void run() {
                if (!matchesByPlayer.containsKey(match.teamOne().iterator().next()) || remaining < 0) return;
                if (remaining == 0) {
                    beginFight(match, map);
                    return;
                }
                notifyMembers(match.teamOne(), "<gradient:#60a5fa:#c084fc>Duel starts in " + remaining + "</gradient>");
                notifyMembers(match.teamTwo(), "<gradient:#60a5fa:#c084fc>Duel starts in " + remaining + "</gradient>");
                titleMembers(match.teamOne(), "<gradient:#60a5fa:#c084fc>" + remaining + "</gradient>", "<gray>Get ready</gray>");
                titleMembers(match.teamTwo(), "<gradient:#60a5fa:#c084fc>" + remaining + "</gradient>", "<gray>Get ready</gray>");
                remaining--;
            }
        }, 0L, 20L);
    }

    private void beginFight(DuelMatch match, DuelMap map) {
        applyTeam(match.teamOne(), map.spawnA(), match.kitId());
        applyTeam(match.teamTwo(), map.spawnB(), match.kitId());
        notifyMembers(match.teamOne(), "<green>Fight!</green>");
        notifyMembers(match.teamTwo(), "<green>Fight!</green>");
        titleMembers(match.teamOne(), "<gradient:#34d399:#22c55e>FIGHT!</gradient>", "<gray>Good luck.</gray>");
        titleMembers(match.teamTwo(), "<gradient:#34d399:#22c55e>FIGHT!</gradient>", "<gray>Good luck.</gray>");
    }

    private void titleMembers(Set<UUID> members, String title, String subtitle) {
        Title.Times times = Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(900), Duration.ofMillis(250));
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.showTitle(Title.title(Text.mm(title), Text.mm(subtitle), times));
        }
    }

    private void applyTeam(Set<UUID> members, Location spawn, String kitId) {
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            snapshots.put(uuid, Snapshot.capture(player));
            if (kit != null) applyKit(player, kit);
            player.teleport(spawn);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setFireTicks(0);
        }
    }

    private void applyKit(Player player, DuelKit kit) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        for (String material : kit.contents()) addItem(player, material);
        for (int i = 0; i < kit.armor().size() && i < 4; i++) setArmor(player, i, kit.armor().get(i));
        if (!kit.offhand().isEmpty()) setOffhand(player, kit.offhand().get(0));
    }

    private void addItem(Player player, String materialName) {
        Material material = parseMaterial(materialName);
        if (material != null) player.getInventory().addItem(new ItemStack(material));
    }

    private void setArmor(Player player, int index, String materialName) {
        Material material = parseMaterial(materialName);
        if (material == null) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null || armor.length < 4) armor = new ItemStack[4];
        armor[index] = new ItemStack(material);
        player.getInventory().setArmorContents(armor);
    }

    private void setOffhand(Player player, String materialName) {
        Material material = parseMaterial(materialName);
        if (material != null) player.getInventory().setItemInOffHand(new ItemStack(material));
    }

    private Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return null; }
    }

    private void endMatch(DuelMatch match, Set<UUID> winners, String reason) {
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(match.teamOne());
        all.addAll(match.teamTwo());
        for (UUID uuid : all) {
            matchesByPlayer.remove(uuid);
            Snapshot snapshot = snapshots.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && snapshot != null) snapshot.restore(player);
            if (player != null) {
                boolean win = winners.contains(uuid);
                Text.send(player, win ? "<green>You won the duel.</green>" : "<gray>Duel ended: " + reason + "</gray>");
                updateDuelStats(uuid, win);
            }
        }
        balanceRatings(match.teamOne(), match.teamTwo(), winners);
    }

    private void balanceRatings(Set<UUID> teamOne, Set<UUID> teamTwo, Set<UUID> winners) {
        boolean teamOneWon = !teamOne.isEmpty() && winners.contains(teamOne.iterator().next());
        for (UUID uuid : teamOne) adjustRating(uuid, teamOneWon);
        for (UUID uuid : teamTwo) adjustRating(uuid, !teamOneWon);
    }

    private void adjustRating(UUID uuid, boolean win) {
        var data = repository.load(uuid);
        data.recordDuel(win);
        int delta = win ? 15 : -15;
        data.duelRating(data.duelRating() + delta);
        repository.save(data);
    }

    private void updateDuelStats(UUID uuid, boolean win) {
        var data = repository.load(uuid);
        data.recordDuel(win);
        repository.save(data);
    }

    private void removeQueueUnit(QueueUnit unit, boolean notify) {
        Deque<UUID> queue = unit.mode() == DuelMode.SOLO ? soloQueues.get(unit.kitId()) : partyQueues.get(unit.kitId());
        if (queue != null) queue.remove(unit.unitId());
        queueUnits.remove(unit.unitId());
        for (UUID member : unit.members()) queueByPlayer.remove(member);
        if (notify) unit.members().forEach(this::removeQueueItemByUuid);
    }

    private void notifyMembers(Collection<UUID> members, String message) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) Text.send(player, message);
        }
    }

    private void removeQueueItemByUuid(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        // Keep the queue item in the hotbar; only prevent dropping it.
    }

    private String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
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

    private NamespacedKey key() { return new NamespacedKey(plugin, ITEM_ID_KEY); }

    private String resolveKit(String input) {
        if (input != null && !input.isBlank()) {
            DuelKit kit = kits.get(input.toLowerCase(Locale.ROOT));
            if (kit != null && kit.enabled()) return kit.id();
        }
        return kits.values().stream().filter(DuelKit::enabled).map(DuelKit::id).findFirst().orElse(null);
    }

    private String resolveMap(String input) {
        if (input != null && !input.isBlank()) {
            DuelMap map = maps.get(input.toLowerCase(Locale.ROOT));
            if (map != null && map.enabled()) return map.id();
        }
        return maps.values().stream().filter(DuelMap::enabled).map(DuelMap::id).findFirst().orElse(null);
    }

    private DuelMap pickMap() { return maps.values().stream().filter(DuelMap::enabled).findFirst().orElse(null); }

    private String selectedKitForParty() { return kits.values().stream().filter(DuelKit::enabled).findFirst().map(DuelKit::id).orElse(null); }

    private void loadKits() {
        var section = configs.get("duel-kits.yml").getConfigurationSection("kits");
        if (section == null) return;
        int fallbackSlot = 10;
        for (String id : section.getKeys(false)) {
            var kit = section.getConfigurationSection(id);
            if (kit == null) continue;
            int slot = kit.getInt("slot", fallbackSlot++);
            DuelKit definition = new DuelKit(id.toLowerCase(Locale.ROOT), kit.getString("display-name", id), parseMaterialOrDefault(kit.getString("icon", "IRON_SWORD"), Material.IRON_SWORD), kit.getString("permission", ""), slot, kit.getBoolean("enabled", true), kit.getStringList("lore"), kit.getStringList("contents"), kit.getStringList("armor"), kit.getStringList("offhand"));
            kits.put(definition.id(), definition);
            kitSlots.put(slot, definition.id());
        }
    }

    private void loadMaps() {
        var section = configs.get("duel-maps.yml").getConfigurationSection("maps");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var map = section.getConfigurationSection(id);
            if (map == null) continue;
            String worldName = map.getString("world", "world");
            maps.put(id.toLowerCase(Locale.ROOT), new DuelMap(id.toLowerCase(Locale.ROOT), map.getString("display-name", id), map.getBoolean("enabled", true), worldName, loc(map, "lobby", worldName), loc(map, "spawn-a", worldName), loc(map, "spawn-b", worldName), loc(map, "spectator", worldName)));
        }
    }

    private Location loc(org.bukkit.configuration.ConfigurationSection map, String key, String worldName) {
        org.bukkit.configuration.ConfigurationSection section = map.getConfigurationSection(key);
        if (section != null) {
            var world = Bukkit.getWorld(section.getString("world", worldName));
            if (world == null) return null;
            return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw", 0.0), (float) section.getDouble("pitch", 0.0));
        }
        return loc(map.getStringList(key), worldName);
    }

    private Location loc(List<String> values, String worldName) {
        if (values.size() < 3) return null;
        var world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = parseDouble(values, 0);
        double y = parseDouble(values, 1);
        double z = parseDouble(values, 2);
        float yaw = values.size() > 3 ? (float) parseDouble(values, 3) : 0f;
        float pitch = values.size() > 4 ? (float) parseDouble(values, 4) : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    private double parseDouble(List<String> values, int index) {
        try { return Double.parseDouble(values.get(index)); } catch (Exception ex) { return 0d; }
    }

    private Material parseMaterialOrDefault(String name, Material fallback) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return fallback; }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remaining = seconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, remaining);
    }

    private record QueueUnit(UUID unitId, DuelMode mode, String kitId, Set<UUID> members, long joinedAt) {}
    private record DuelChallenge(UUID challenger, UUID target, Set<UUID> challengers, Set<UUID> targets, String kitId, String mapId, DuelMode mode, long createdAt) {}

    private static final class Snapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final Location location;
        private final double health;
        private final int food;

        private Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, Location location, double health, int food) {
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
            this.location = location;
            this.health = health;
            this.food = food;
        }

        static Snapshot capture(Player player) {
            return new Snapshot(player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand(), player.getLocation(), player.getHealth(), player.getFoodLevel());
        }

        void restore(Player player) {
            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offhand);
            player.teleport(location);
            if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                player.setHealth(Math.max(1.0, Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue())));
            }
            player.setFoodLevel(food);
        }
    }
}













