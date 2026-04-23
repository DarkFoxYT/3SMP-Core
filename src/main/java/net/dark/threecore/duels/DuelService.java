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
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import net.kyori.adventure.title.Title;
import java.time.Duration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;

public final class DuelService implements Listener {
    private static final String ITEM_ID_KEY = "3smpcore_duel_item";
    private static final String QUEUE_ITEM_ID = "queue_sword";
    private static final String EDITOR_TOOL_KEY = "3smpcore_duel_editor_tool";
    private static final Set<UUID> ACTIVE_DUEL_PLAYERS = new HashSet<>();
    
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MenuService menuService;
    private final PartyService partyService;
    private final DuelLeaderboardService leaderboardService;
    private final LaunchpadService launchpadService;
    private final DuelWorldService worldService;
    private final Map<String, DuelKit> kits = new LinkedHashMap<>();
    private final Map<Integer, String> kitSlots = new HashMap<>();
    private final Map<String, DuelMap> maps = new LinkedHashMap<>();
    private final Map<UUID, QueueUnit> queueByPlayer = new HashMap<>();
    private final Map<UUID, QueueUnit> queueUnits = new HashMap<>();
    private final Map<String, Deque<UUID>> soloQueues = new HashMap<>();
    private final Map<String, Deque<UUID>> partyQueues = new HashMap<>();
    private final Map<UUID, DuelMatch> matchesByPlayer = new HashMap<>();
    private final Map<UUID, String> pendingChallengeTargets = new HashMap<>();
    private final Set<UUID> frozenDuelPlayers = new HashSet<>();
    private final Map<UUID, SpectatorSnapshot> spectatorSnapshots = new HashMap<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, org.bukkit.World> instanceWorldsByMatch = new HashMap<>();
    private final Map<UUID, String> selectedEditorMap = new HashMap<>();
    private final Set<UUID> devMode = new HashSet<>();
    private final Set<UUID> mapEditorMode = new HashSet<>();
    private final Set<UUID> endingMatches = new HashSet<>();
    private final Map<UUID, ItemStack[]> editorInventorySnapshots = new HashMap<>();
    private final Map<UUID, PendingKitRoundEdit> pendingKitRoundEdits = new HashMap<>();
    private final Map<UUID, DuelChallenge> challengesByTarget = new HashMap<>();
    private final Map<UUID, DuelChallenge> challengesByChallenger = new HashMap<>();
    private final List<Consumer<Player>> postMatchItemRefreshers = new ArrayList<>();
    private BukkitTask hudTask;
    private boolean enabled = true;
    private String lastPickedMapId = "";

    public DuelService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService, PartyService partyService, DuelLeaderboardService leaderboardService, LaunchpadService launchpadService) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
        this.partyService = partyService;
        this.leaderboardService = leaderboardService;
        this.launchpadService = launchpadService;
        this.worldService = new DuelWorldService(plugin, configs);
        reload();
        startHudTask();
    }

    public void reload() {
        kits.clear();
        kitSlots.clear();
        maps.clear();
        loadKits();
        loadMaps();
        enabled = configs.get("duels/duels.yml").getBoolean("duels.enabled", true);
    }

    public void handle(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        if (!canUseInWorld(player)) { Text.send(player, "<red>Duels are not available in this world.</red>"); return; }
        String sub = context.arg(0).toLowerCase(Locale.ROOT);
        boolean adminAccess = player.isOp() || player.hasPermission("3smpcore.duel.admin");
        Player directTarget = sub.isBlank() ? null : Bukkit.getPlayerExact(context.arg(0));
        if (!adminAccess && directTarget == null && !sub.equals("accept") && !sub.equals("deny") && !sub.equals("decline") && !sub.equals("leave")) {
            Text.send(player, "<yellow>Use <white>/duel <player></white> to challenge someone.</yellow>");
            return;
        }
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
            case "leave" -> leaveDuelOrQueue(player);
            case "spec", "spectate" -> spectate(player, context.arg(1));
            case "kiteditor" -> { if (context.args().length >= 2) openKitEditor(player, context.arg(1)); else openKitEditorSelector(player); }
            case "devpanel", "mapeditor" -> openDevMenu(player);
            case "test" -> runTestDuel(player);
            case "admin" -> handleAdmin(player, context);
            case "map", "arena" -> handleMap(player, context);
            case "savearena" -> saveArenaCommand(player);
            default -> {
                Player target = Bukkit.getPlayerExact(context.arg(0));
                if (target != null) openChallengeKitMenu(player, target);
                else Text.send(player, "<yellow>Use /duel <player>, accept, deny, menu, queue, leaderboard, devpanel or map.</yellow>");
            }
        }
    }

    public List<String> complete(CommandContext context) {
        if (context.args().length <= 1) { java.util.List<String> out = new java.util.ArrayList<>(); if (context.sender() instanceof Player p && (p.isOp() || p.hasPermission("3smpcore.duel.admin"))) out.addAll(List.of("menu", "challenge", "accept", "deny", "queue", "leave", "spec", "spectate", "leaderboard", "test", "kiteditor", "devpanel", "mapeditor", "map", "arena", "admin")); else out.addAll(List.of("accept", "deny", "leave")); for (Player online : Bukkit.getOnlinePlayers()) out.add(online.getName()); return out; }
        if ((context.arg(0).equalsIgnoreCase("spec") || context.arg(0).equalsIgnoreCase("spectate")) && context.args().length <= 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (context.arg(0).equalsIgnoreCase("queue") && context.args().length <= 2) return List.of("party");
        if (context.arg(0).equalsIgnoreCase("map") && context.args().length <= 2) return List.of("create", "select", "delete", "editor", "marker", "savemarkers", "setlobby", "setspawna", "setspawnb", "setspec", "save", "list", "enable", "disable");
        if (context.arg(0).equalsIgnoreCase("test") && context.args().length <= 2) return List.of("arena");
        if (context.arg(0).equalsIgnoreCase("admin") && context.args().length <= 2) return List.of("reload", "toggle");
        return List.of();
    }

    public Collection<DuelKit> kits() { return kits.values(); }
    public Collection<DuelMap> maps() { return maps.values(); }
    public boolean isQueuedForKit(UUID uuid, String kitId) { QueueUnit unit = queueByPlayer.get(uuid); return unit != null && unit.kitId().equalsIgnoreCase(kitId); }
    public boolean isPlayerInDuel(UUID uuid) { return matchesByPlayer.containsKey(uuid); }
    public static boolean isDuelPlayer(Player player) { return player != null && ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId()); }
    public void addPostMatchItemRefresher(Consumer<Player> refresher) { postMatchItemRefreshers.add(refresher); }

    public void openMainMenu(Player player) { menuService.open(player, new DuelMenu(this).buildMain(player)); }
    public void openSummary(Player player) { menuService.open(player, new DuelMenu(this).buildSummary(player)); }

    public void openKitMenu(Player player) { menuService.open(player, new DuelMenu(this).buildKitMenu(player)); }
    private void openChallengeKitMenu(Player player, Player target) {
        pendingChallengeTargets.put(player.getUniqueId(), target.getName());
        menuService.open(player, new DuelMenu(this).buildKitMenu(player, "<gradient:#60a5fa:#c084fc>Choose Kit vs " + target.getName() + "</gradient>"));
        Text.actionBar(player, "<gray>Pick a kit to challenge</gray> <white>" + target.getName() + "</white>");
    }
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
        else if (slot == 11) openKitMenu(player);
        else if (slot == 15) queueParty(player);
    }

    public void handleSummaryClick(Player player, int slot) {
        if (slot == 22) openMainMenu(player);
        else if (slot == 11) openKitMenu(player);
    }

    public void handleKitMenuClick(Player player, int slot) {
        if (slot == 45) { openMainMenu(player); return; }
        String kitId = kitSlots.get(slot);
        if (kitId == null) return;
        String target = pendingChallengeTargets.remove(player.getUniqueId());
        if (target != null) { challenge(player, target, kitId, ""); return; }
        if (isQueuedForKit(player.getUniqueId(), kitId)) leaveQueue(player, false);
        else queueSolo(player, kitId);
    }

    public void handleMenuClick(Player player, int slot) { handleMainMenuClick(player, slot); }
    public int soloQueueCount() { return soloQueues.values().stream().mapToInt(Deque::size).sum(); }
    public int partyQueueCount() { return partyQueues.values().stream().mapToInt(Deque::size).sum(); }
    public Collection<DuelKit> kitsView() { return Collections.unmodifiableCollection(kits.values()); }
    public Collection<DuelMap> enabledMapsView() { return maps.values().stream().filter(DuelMap::enabled).toList(); }
    public DuelKit kit(String id) { return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT)); }
    public DuelMap map(String id) { return id == null ? null : maps.get(id.toLowerCase(Locale.ROOT)); }

    public boolean startConfiguredPartyDuel(Player requester, Set<UUID> red, Set<UUID> blue, String kitId, int rounds, String mapId) {
        if (red == null || blue == null || red.isEmpty() || blue.isEmpty()) {
            Text.send(requester, "<red>Select at least one player for each team.</red>");
            return false;
        }
        DuelKit kit = kit(kitId);
        if (kit == null || !kit.enabled()) {
            Text.send(requester, "<red>Select a valid kit first.</red>");
            return false;
        }
        Set<UUID> overlap = new HashSet<>(red);
        overlap.retainAll(blue);
        if (!overlap.isEmpty()) {
            Text.send(requester, "<red>A player cannot be on both teams.</red>");
            return false;
        }
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(red);
        all.addAll(blue);
        for (UUID uuid : all) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                Text.send(requester, "<red>All selected party members must be online.</red>");
                return false;
            }
            if (matchesByPlayer.containsKey(uuid) || queueByPlayer.containsKey(uuid)) {
                Text.send(requester, "<red>One selected player is already queued or dueling.</red>");
                return false;
            }
        }
        QueueUnit first = new QueueUnit(UUID.randomUUID(), DuelMode.PARTY, kit.id(), new LinkedHashSet<>(red), System.currentTimeMillis());
        QueueUnit second = new QueueUnit(UUID.randomUUID(), DuelMode.PARTY, kit.id(), new LinkedHashSet<>(blue), System.currentTimeMillis());
        startMatch(first, second);
        return true;
    }
    public int kitCount() { return kits.size(); }
    public int mapCount() { return maps.size(); }
    public boolean isEnabled() { return enabled; }
    public boolean isDevEnabled(UUID uuid) { return devMode.contains(uuid); }
    public boolean isMapEditorEnabled(UUID uuid) { return mapEditorMode.contains(uuid); }
    public String selectedEditorMap(UUID uuid) { return selectedEditorMap.getOrDefault(uuid, "none"); }
    public void handleDevMenuClick(Player player, int slot) {
        switch (slot) {
            case 10 -> runTestDuel(player);
            case 12 -> openArenaSelector(player);
            case 14 -> openMapEditor(player);
            case 16 -> openLeaderboard(player);
            case 18 -> openKitEditorSelector(player);
            case 28 -> { reload(); Text.send(player, "<green>Duel configs reloaded.</green>"); openDevMenu(player); }
            case 30 -> { enabled = !enabled; Text.send(player, enabled ? "<green>Duel module enabled.</green>" : "<red>Duel module disabled.</red>"); openDevMenu(player); }
            case 32 -> { if (launchpadService != null) launchpadService.openMenu(player); }
            case 34 -> saveArenaCommand(player);
            default -> { }
        }
    }

    public void openKitEditorSelector(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "kit-selector"), 54, "Select Kit To Edit");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createTagged(Material.GRAY_STAINED_GLASS_PANE, " ", "kit_editor_filler"));
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int index = 0;
        for (DuelKit kit : kits.values()) {
            if (index >= slots.length) break;
            inv.setItem(slots[index++], createTagged(kit.icon(), kit.displayName(), "kit_edit_" + kit.id()));
        }
        inv.setItem(49, createTagged(Material.ARROW, "<gray>Back</gray>", "kit_editor_back"));
        player.openInventory(inv);
    }

    public void handleKitEditorSelectorClick(Player player, int slot) {
        if (slot == 49) { openDevMenu(player); return; }
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) index = i;
        if (index < 0 || index >= kits.size()) return;
        openKitEditor(player, new ArrayList<>(kits.keySet()).get(index));
    }

    public void openKitEditor(Player player, String kitId) {
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) { Text.send(player, "<red>Unknown kit.</red>"); return; }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "kit-editor:" + kit.id()), 54, "Kit Editor: " + kit.id());
        for (int i = 36; i < inv.getSize(); i++) inv.setItem(i, createTagged(Material.GRAY_STAINED_GLASS_PANE, " ", "kit_editor_filler"));
        for (String item : kit.contents()) addEditorItem(inv, item);
        for (int i = 0; i < kit.armor().size() && i < 4; i++) inv.setItem(45 + i, materialItem(kit.armor().get(i)));
        if (!kit.offhand().isEmpty()) inv.setItem(50, materialItem(kit.offhand().get(0)));
        inv.setItem(36, createTagged(kit.autoApplyPotions() ? Material.SPLASH_POTION : Material.GLASS_BOTTLE, kit.autoApplyPotions() ? "<light_purple>Auto Potion Splash: ON</light_purple>" : "<gray>Auto Potion Splash: OFF</gray>", "kit_editor_auto_potions"));
        inv.setItem(37, createTagged(Material.CLOCK, "<gradient:#f59e0b:#fbbf24>Rounds: " + kit.rounds() + "</gradient>", "kit_editor_rounds"));
        for (int i = 0; i < 4; i++) {
            ItemStack potion = i < kit.autoPotions().size() ? materialItem(kit.autoPotions().get(i)) : null;
            inv.setItem(38 + i, potion == null ? null : potion);
        }
        inv.setItem(42, createTagged(kit.healthIndicator() ? Material.LIME_DYE : Material.GRAY_DYE, kit.healthIndicator() ? "<green>Health Indicator: ON</green>" : "<gray>Health Indicator: OFF</gray>", "kit_editor_health_indicator"));
        inv.setItem(43, new ItemStack(kit.icon()));
        inv.setItem(44, createTagged(Material.ITEM_FRAME, "<gradient:#60a5fa:#c084fc>Kit Icon Slot</gradient>", "kit_editor_icon_label"));
        inv.setItem(49, createTagged(Material.SHIELD, "<gradient:#60a5fa:#c084fc>Offhand Slot</gradient>", "kit_editor_label"));
        inv.setItem(52, createTagged(Material.LIME_DYE, "<green>Save Kit</green>", "kit_editor_save"));
        inv.setItem(53, createTagged(Material.BARRIER, "<red>Cancel</red>", "kit_editor_cancel"));
        player.openInventory(inv);
    }

    public void handleKitEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String context = ((CoreMenuHolder) event.getView().getTopInventory().getHolder()).context();
        String kitId = context.substring("kit-editor:".length());
        int raw = event.getRawSlot();
        if (raw == 36) { event.setCancelled(true); toggleKitAutoPotions(player, kitId); return; }
        if (raw == 37) { event.setCancelled(true); openKitRoundsSign(player, kitId); return; }
        if (raw == 42) { event.setCancelled(true); toggleKitHealthIndicator(player, kitId); return; }
        if (raw == 52) {
            event.setCancelled(true);
            saveKitFromEditor(player, kitId, event.getView().getTopInventory());
            return;
        }
        if (raw == 53) { event.setCancelled(true); openKitEditorSelector(player); return; }
        if (raw == 49 || raw == 36 || raw == 37 || raw == 42 || raw == 44 || raw == 49 || raw == 51) event.setCancelled(true);
    }

    private void toggleKitAutoPotions(Player player, String kitId) {
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) return;
        var yaml = configs.get("duels/kits.yml");
        yaml.set("kits." + kit.id() + ".auto-apply-potions", !kit.autoApplyPotions());
        saveKitYaml(player, yaml);
        reload();
        openKitEditor(player, kit.id());
    }

    private void adjustKitRounds(Player player, String kitId, int delta) {
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) return;
        int next = Math.max(1, Math.min(9, kit.rounds() + delta));
        var yaml = configs.get("duels/kits.yml");
        yaml.set("kits." + kit.id() + ".rounds", next);
        saveKitYaml(player, yaml);
        reload();
        openKitEditor(player, kit.id());
    }

    private void openKitRoundsSign(Player player, String kitId) {
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) return;
        Location signLocation = player.getLocation().getBlock().getLocation().add(0, 1, 0);
        var block = signLocation.getBlock();
        var previous = block.getBlockData().clone();
        block.setType(Material.OAK_SIGN);
        pendingKitRoundEdits.put(player.getUniqueId(), new PendingKitRoundEdit(kit.id(), signLocation, previous));
        try {
            if (block.getState() instanceof Sign sign) {
                sign.getSide(Side.FRONT).setLine(0, "------");
                sign.getSide(Side.FRONT).setLine(1, String.valueOf(kit.rounds()));
                sign.getSide(Side.FRONT).setLine(2, "rounds");
                sign.getSide(Side.FRONT).setLine(3, "------");
                sign.update(true, false);
                player.openSign(sign, Side.FRONT);
                Text.send(player, "<gray>Enter the round amount on the sign line under the top dashes.</gray>");
            } else {
                pendingKitRoundEdits.remove(player.getUniqueId());
                block.setBlockData(previous);
                Text.send(player, "<red>Could not open sign editor.</red>");
            }
        } catch (Throwable ex) {
            pendingKitRoundEdits.remove(player.getUniqueId());
            block.setBlockData(previous);
            Text.send(player, "<red>Could not open sign editor.</red>");
        }
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
    private void toggleKitHealthIndicator(Player player, String kitId) {
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null) return;
        var yaml = configs.get("duels/kits.yml");
        yaml.set("kits." + kit.id() + ".health-indicator", !kit.healthIndicator());
        saveKitYaml(player, yaml);
        reload();
        openKitEditor(player, kit.id());
    }
    private boolean saveKitYaml(Player player, org.bukkit.configuration.file.YamlConfiguration yaml) {
        try { yaml.save(new java.io.File(plugin.getDataFolder(), "duels/kits.yml")); return true; }
        catch (Exception ex) { Text.send(player, "<red>Failed to save kit.</red>"); return false; }
    }

    private void saveKitFromEditor(Player player, String kitId, org.bukkit.inventory.Inventory inv) {
        DuelKit existing = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (existing == null) return;
        List<String> contents = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (validKitItem(inv.getItem(i))) contents.add(i + "|" + encodeItem(inv.getItem(i)));
        List<String> armor = new ArrayList<>();
        for (int i = 45; i <= 48; i++) armor.add(validKitItem(inv.getItem(i)) ? encodeItem(inv.getItem(i)) : "");
        List<String> offhand = new ArrayList<>();
        if (validKitItem(inv.getItem(50))) offhand.add(encodeItem(inv.getItem(50)));
        var yaml = configs.get("duels/kits.yml");
        String path = "kits." + existing.id();
        yaml.set(path + ".contents", contents);
        yaml.set(path + ".armor", armor);
        yaml.set(path + ".offhand", offhand);
        yaml.set(path + ".auto-apply-potions", existing.autoApplyPotions());
        if (validKitItem(inv.getItem(43))) yaml.set(path + ".icon", inv.getItem(43).getType().name());
        else yaml.set(path + ".icon", existing.icon().name());
        List<String> autoPotions = new ArrayList<>();
        for (int i = 38; i <= 41; i++) autoPotions.add(validKitItem(inv.getItem(i)) ? encodeItem(inv.getItem(i)) : "");
        yaml.set(path + ".auto-potions", autoPotions);
        yaml.set(path + ".rounds", existing.rounds());
        yaml.set(path + ".health-indicator", existing.healthIndicator());
        if (!saveKitYaml(player, yaml)) return;
        reload();
        Text.send(player, "<green>Saved duel kit:</green> <white>" + existing.id() + "</white>");
        openKitEditorSelector(player);
    }

    private void addEditorItem(org.bukkit.inventory.Inventory inv, String encoded) {
        SlottedItem slotted = parseSlottedItem(encoded);
        if (slotted.item() == null) return;
        if (slotted.slot() >= 0 && slotted.slot() < 36) inv.setItem(slotted.slot(), slotted.item());
        else inv.addItem(slotted.item());
    }

    private ItemStack materialItem(String encoded) {
        return parseSlottedItem(encoded).item();
    }

    private SlottedItem parseSlottedItem(String encoded) {
        if (encoded == null || encoded.isBlank()) return new SlottedItem(-1, null);
        String value = encoded;
        int slot = -1;
        int divider = encoded.indexOf('|');
        if (divider > 0) {
            try { slot = Integer.parseInt(encoded.substring(0, divider)); } catch (NumberFormatException ignored) { slot = -1; }
            value = encoded.substring(divider + 1);
        }
        ItemStack decoded = decodeItem(value);
        if (decoded != null) return new SlottedItem(slot, decoded);
        Material material = parseMaterial(value.contains(":") ? value.substring(0, value.indexOf(':')) : value);
        if (material == null) return new SlottedItem(slot, null);
        int amount = 1;
        if (value.contains(":")) {
            try { amount = Math.max(1, Integer.parseInt(value.substring(value.indexOf(':') + 1))); } catch (NumberFormatException ignored) { amount = 1; }
        }
        return new SlottedItem(slot, new ItemStack(material, amount));
    }

    private String encodeItem(ItemStack item) {
        if (!validKitItem(item)) return "";
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
            return "item:" + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception ex) {
            return item.getType().name() + ":" + item.getAmount();
        }
    }

    private ItemStack decodeItem(String encoded) {
        if (encoded == null || !encoded.startsWith("item:")) return null;
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring("item:".length()))); BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            Object object = in.readObject();
            return object instanceof ItemStack stack ? stack : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean validKitItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && itemId(item) == null && editorToolId(item) == null;
    }

    private record SlottedItem(int slot, ItemStack item) {}
    public void queueSolo(Player player, String kitId) {
        if (!enabled) { Text.send(player, "<red>Duels are currently disabled.</red>"); return; }
        if (!canUseInWorld(player)) { Text.send(player, "<red>Duels are not available in this world.</red>"); return; }
        if (!canUseInWorld(player)) { Text.send(player, "<red>Duels are not available in this world.</red>"); return; }
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
        tryMatchAll();
    }

    public void queueParty(Player player) {
        if (!enabled) { Text.send(player, "<red>Duels are currently disabled.</red>"); return; }
        if (matchesByPlayer.containsKey(player.getUniqueId())) { Text.send(player, "<red>You are already in a duel.</red>"); return; }
        UUID leader = partyService.partyLeader(player.getUniqueId());
        if (leader == null) { Text.send(player, "<red>You need a party to queue party duels.</red>"); return; }
        if (!leader.equals(player.getUniqueId())) { Text.send(player, "<red>Only the party leader can queue party duels.</red>"); return; }
        Set<UUID> members = partyService.partyMembers(player.getUniqueId());
        if (members.isEmpty()) members = Set.of(player.getUniqueId());
        if (members.size() > 3) { Text.send(player, "<red>Your party can have at most 3 members for party duels.</red>"); return; }
        if (members.stream().anyMatch(queueByPlayer::containsKey)) { Text.send(player, "<yellow>Your party is already queued.</yellow>"); return; }
        String kitId = selectedKitForParty();
        if (kitId == null) { Text.send(player, "<red>No enabled kits are configured.</red>"); return; }
        QueueUnit unit = new QueueUnit(UUID.randomUUID(), DuelMode.PARTY, kitId, new LinkedHashSet<>(members), System.currentTimeMillis());
        queueUnits.put(unit.unitId(), unit);
        for (UUID member : members) queueByPlayer.put(member, unit);
        partyQueues.computeIfAbsent(kitId, ignored -> new ArrayDeque<>()).add(unit.unitId());
        actionBarMembers(members, "<gradient:#34d399:#22c55e>Queued for 2v2</gradient> <gray>" + kits.get(kitId).displayName() + "</gray>");
        tryMatchAll();
    }

    public void leaveQueue(Player player, boolean silent) {
        QueueUnit unit = queueByPlayer.get(player.getUniqueId());
        if (unit == null) {
            if (!silent) Text.send(player, "<gray>You are not queued.</gray>");
            return;
        }
        removeQueueUnit(unit, true);
        if (!silent) Text.actionBar(player, "<gray>Left the duel queue.</gray>");
        giveQueueItem(player);
        refreshOpenMenus();
    }

    public void leaveDuelOrQueue(Player player) {
        if (spectatorSnapshots.containsKey(player.getUniqueId())) { stopSpectating(player); return; }
        DuelMatch match = matchesByPlayer.get(player.getUniqueId());
        if (match != null) {
            Set<UUID> winners = match.teamOne().contains(player.getUniqueId()) ? match.teamTwo() : match.teamOne();
            endMatch(match, winners, "forfeit");
            return;
        }
        leaveQueue(player, false);
    }


    public void spectate(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) { Text.send(player, "<red>Usage: /spectate <player></red>"); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { Text.send(player, "<red>Player not found.</red>"); return; }
        DuelMatch match = matchesByPlayer.get(target.getUniqueId());
        if (match == null) { Text.send(player, "<red>That player is not in a duel.</red>"); return; }
        if (matchesByPlayer.containsKey(player.getUniqueId())) { Text.send(player, "<red>You cannot spectate while in a duel.</red>"); return; }
        spectatorSnapshots.putIfAbsent(player.getUniqueId(), SpectatorSnapshot.capture(player));
        Location spec = maps.containsKey(match.mapId()) ? maps.get(match.mapId()).spectator() : null;
        if (spec == null) spec = target.getLocation();
        player.teleport(spec);
        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(target);
        Text.send(player, "<gradient:#60a5fa:#c084fc>Spectating</gradient> <white>" + target.getName() + "</white><gray>. Use /leave to exit.</gray>");
    }

    private void stopSpectating(Player player) {
        SpectatorSnapshot snapshot = spectatorSnapshots.remove(player.getUniqueId());
        if (snapshot == null) return;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        player.setGameMode(snapshot.gameMode());
        player.teleport(snapshot.location());
        Text.send(player, "<gray>Stopped spectating.</gray>");
    }

    public void challenge(Player challenger, String targetName, String kitIdInput, String mapIdInput) {
        if (!enabled) { Text.send(challenger, "<red>Duels are currently disabled.</red>"); return; }
        if (!canUseInWorld(challenger)) { Text.send(challenger, "<red>Duels are not available in this world.</red>"); return; }
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
        long timeout = configs.get("duels/duels.yml").getLong("duels.challenges.timeout-seconds", 60L) * 1000L;
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
            case 10 -> spawnEditorMarker(player, "lobby");
            case 11 -> spawnEditorMarker(player, "spawn-a");
            case 12 -> spawnEditorMarker(player, "spawn-b");
            case 13 -> spawnEditorMarker(player, "spectator");
            case 14, 15 -> Text.send(player, "<gray>Bounds are no longer needed. Save lobby, spawn A, spawn B, and spectator markers.</gray>");
            case 16 -> saveArenaCommand(player);
            case 22 -> Text.send(player, "<gray>Editor tools are tied to the selected arena. Save the arena to exit editor mode.</gray>");
            case 24 -> { String id = selectedEditorMap.get(player.getUniqueId()); if (id == null) Text.send(player, "<gray>Select a map with /duel map select <id>.</gray>"); else saveMarkers(player, id); }
            case 26 -> openDevMenu(player);
            default -> { }
        }
    }

    public void openArenaSelector(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "arena-selector"), 54, "Select Arena To Edit");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createTagged(Material.GRAY_STAINED_GLASS_PANE, " ", "arena_filler"));
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        int index = 0;
        for (DuelMap map : maps.values()) {
            if (index >= slots.length) break;
            inv.setItem(slots[index++], createTagged(Material.MAP, "<gradient:#60a5fa:#c084fc>" + map.displayName() + "</gradient>", "arena_select_" + map.id()));
        }
        inv.setItem(49, createTagged(Material.ARROW, "<gray>Back</gray>", "arena_back"));
        player.openInventory(inv);
    }

    public void handleArenaSelectorClick(Player player, int slot) {
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
        if (slot == 49) { openDevMenu(player); return; }
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) index = i;
        if (index < 0 || index >= maps.size()) return;
        selectMapEditor(player, new ArrayList<>(maps.keySet()).get(index));
    }

    public void saveArenaCommand(Player player) {
        String id = selectedEditorMap.get(player.getUniqueId());
        if (id == null) { Text.send(player, "<red>Select an arena first.</red>"); return; }
        saveMarkers(player, id);
        Text.send(player, "<green>Arena saved:</green> <white>" + id + "</white> <gray>Bounds/spawns updated from editor markers.</gray>");
    }

    private void restoreEditorInventory(Player player) {
        removeEditorTools(player);
        ItemStack[] contents = editorInventorySnapshots.remove(player.getUniqueId());
        if (contents != null) player.getInventory().setContents(contents);
    }

    public void openMapEditor(Player player) {
        menuService.open(player, createMapEditor(player));
    }

    private org.bukkit.inventory.Inventory createMapEditor(Player player) {
        String selected = selectedEditorMap.getOrDefault(player.getUniqueId(), "none");
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "map-editor"), 27, "Map Editor: " + selected);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createTagged(Material.GRAY_STAINED_GLASS_PANE, " ", "map_editor_filler"));
        inv.setItem(10, createTagged(Material.LECTERN, "<gradient:#60a5fa:#c084fc>Set Lobby</gradient>", "map_set_lobby"));
        inv.setItem(11, createTagged(Material.DIAMOND_SWORD, "<gradient:#34d399:#22c55e>Set Spawn A</gradient>", "map_set_spawn_a"));
        inv.setItem(12, createTagged(Material.SHIELD, "<gradient:#f59e0b:#f97316>Set Spawn B</gradient>", "map_set_spawn_b"));
        inv.setItem(13, createTagged(Material.COMPASS, "<gradient:#a78bfa:#f472b6>Set Spectator</gradient>", "map_set_spectator"));
        inv.setItem(14, createTagged(Material.ARMOR_STAND, "<gradient:#D6E8F7:#FFFFFF>Min Bound</gradient>", "map_min_bound"));
        inv.setItem(15, createTagged(Material.ARMOR_STAND, "<gradient:#1A2A4A:#D6E8F7>Max Bound</gradient>", "map_max_bound"));
        inv.setItem(16, createTagged(Material.STRUCTURE_BLOCK, "<gradient:#22d3ee:#8b5cf6>Save Arena</gradient>", "map_save_item"));
        inv.setItem(22, createTagged(Material.BOOK, "<gradient:#D6E8F7:#FFFFFF>Editor Active</gradient>", "map_editor_info"));
        inv.setItem(24, createTagged(Material.SUNFLOWER, "<gradient:#22d3ee:#8b5cf6>Save Markers</gradient>", "map_save"));
        inv.setItem(26, createTagged(Material.BARRIER, "<red>Back</red>", "map_close"));
        return inv;
    }
    public void giveQueueItem(Player player) {
        if (net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId()) || matchesByPlayer.containsKey(player.getUniqueId())) { clearQueueItem(player); return; }
        if (!configs.get("duels/duels.yml").getBoolean("duels.queue-item.enabled", true)) return;
        int slot = configs.get("duels/duels.yml").getInt("duels.queue-item.slot", 0);
        ItemStack item = createTagged(Material.DIAMOND_SWORD, configs.get("duels/duels.yml").getString("duels.queue-item.name", "<gradient:#60a5fa:#c084fc>Duel Queue</gradient>"), QUEUE_ITEM_ID);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Text.mm("<gray>Click to open duel modes.</gray>"),
                Text.mm("<gray>1v1 queued:</gray> <white>" + soloQueueCount() + "</white>"),
                Text.mm("<gray>2v2 queued:</gray> <white>" + partyQueueCount() + "</white>")
        ));
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
    }

    private void clearQueueItem(Player player) { for (int i = 0; i < player.getInventory().getSize(); i++) if (QUEUE_ITEM_ID.equals(itemId(player.getInventory().getItem(i)))) player.getInventory().setItem(i, null); }

    public void reloadItems(Player player) {
        giveQueueItem(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String editorTool = editorToolId(event.getItem());
        if (editorTool != null) {
            event.setCancelled(true);
            placeEditorMarkerFromTool(event.getPlayer(), editorTool, event.getClickedBlock() == null ? null : event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5));
            return;
        }
        String id = itemId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        if (QUEUE_ITEM_ID.equals(id)) { if (canUseInWorld(event.getPlayer())) openMainMenu(event.getPlayer()); else Text.send(event.getPlayer(), "<red>Duels are not available in this world.</red>"); }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { reloadItems(event.getPlayer()); }
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, () -> reloadItems(event.getPlayer())); }
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (mapEditorMode.remove(player.getUniqueId())) {
            restoreEditorInventory(player);
            Text.send(player, "<yellow>Duel map editor mode disabled because you changed worlds.</yellow>");
        }
        reloadItems(player);
    }
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        String id = itemId(stack);
        if (QUEUE_ITEM_ID.equals(id) || editorToolId(stack) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String editorTool = editorToolId(event.getCurrentItem());
        if (editorTool != null) { event.setCancelled(true); event.setCursor(null); event.setCurrentItem(event.getCurrentItem()); if (event.getWhoClicked() instanceof Player p) Bukkit.getScheduler().runTask(plugin, p::updateInventory); return; }
        String id = itemId(event.getCurrentItem());
        if (id == null) return;
        if (QUEUE_ITEM_ID.equals(id)) {
            event.setCancelled(true);
            event.setCursor(null);
            event.setCurrentItem(event.getCurrentItem());
            if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == player.getInventory()) { Bukkit.getScheduler().runTask(plugin, player::updateInventory); Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!player.isOnline()) return; player.setItemOnCursor(null); player.updateInventory(); if (canUseInWorld(player)) openMainMenu(player); else Text.send(player, "<red>Duels are not available in this world.</red>"); }, 20L); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandDuringDuel(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        boolean active = ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId());
        boolean matched = matchesByPlayer.containsKey(player.getUniqueId());
        if (active && !matched) {
            ACTIVE_DUEL_PLAYERS.remove(player.getUniqueId());
            return;
        }
        if (!active && !matched) return;
        String command = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        if (command.equals("/leave")) return;
        event.setCancelled(true);
        Text.send(player, "<red>You cannot use commands during a duel.</red> <gray>Use <white>/leave</white> to forfeit.</gray>");
    }
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        PendingKitRoundEdit edit = pendingKitRoundEdits.remove(event.getPlayer().getUniqueId());
        if (edit == null) return;
        if (!edit.location().equals(event.getBlock().getLocation())) return;
        try {
            int rounds = parseSignInteger(event, 15);
            var yaml = configs.get("duels/kits.yml");
            yaml.set("kits." + edit.kitId() + ".rounds", rounds);
            saveKitYaml(event.getPlayer(), yaml);
            event.getBlock().setBlockData(edit.previous());
            reload();
            Text.send(event.getPlayer(), "<green>Kit rounds saved:</green> <white>" + rounds + "</white>");
            Bukkit.getScheduler().runTask(plugin, () -> openKitEditor(event.getPlayer(), edit.kitId()));
        } catch (Exception ex) {
            event.getBlock().setBlockData(edit.previous());
            Text.send(event.getPlayer(), "<red>Invalid round amount.</red>");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!frozenDuelPlayers.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        DuelMatch match = matchesByPlayer.get(event.getEntity().getUniqueId());
        if (match == null) return;
        event.getDrops().clear();
        if (event.getEntity().getKiller() != null) playKillEffect(event.getEntity().getKiller());
        handleDuelElimination(event.getEntity(), match, "death");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (frozenDuelPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        DuelMatch match = matchesByPlayer.get(player.getUniqueId());
        if (match == null) return;
        if (event.getFinalDamage() < player.getHealth()) return;
        if (hasUsableTotem(player)) return;
        playKillEffect(killerFromDamage(event));
        event.setCancelled(true);
        handleDuelElimination(player, match, "death");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ACTIVE_DUEL_PLAYERS.remove(event.getPlayer().getUniqueId());
        frozenDuelPlayers.remove(event.getPlayer().getUniqueId());
        if (mapEditorMode.remove(event.getPlayer().getUniqueId())) restoreEditorInventory(event.getPlayer());
        QueueUnit unit = queueByPlayer.get(event.getPlayer().getUniqueId());
        if (unit != null) removeQueueUnit(unit, false);
        DuelMatch match = matchesByPlayer.get(event.getPlayer().getUniqueId());
        if (match != null) handleDuelElimination(event.getPlayer(), match, "quit");
    }

    private boolean hasUsableTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    private Player killerFromDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent damage)) return null;
        Entity damager = damage.getDamager();
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    private void playKillEffect(Player player) {
        if (player == null || !player.isOnline()) return;
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.1, 0), 38, 0.45, 0.65, 0.45, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.55f);
    }
    private void handleDuelElimination(Player eliminated, DuelMatch match, String reason) {
        if (!endingMatches.add(match.id())) return;
        Set<UUID> winners = match.teamOne().contains(eliminated.getUniqueId()) ? match.teamTwo() : match.teamOne();
        try {
            eliminated.setHealth(Math.max(1.0, Math.min(20.0, eliminated.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : eliminated.getAttribute(Attribute.MAX_HEALTH).getValue())));
            eliminated.setFireTicks(0);
            eliminated.getInventory().clear();
            eliminated.getInventory().setArmorContents(new ItemStack[4]);
            eliminated.getInventory().setItemInOffHand(null);
            eliminated.setGameMode(GameMode.SPECTATOR);
            Player target = firstOnline(winners);
            if (target != null) eliminated.setSpectatorTarget(target);
        } catch (Exception ignored) {}
        String winnerNames = names(winners);
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(match.teamOne());
        all.addAll(match.teamTwo());
        notifyMembers(all, "<gradient:#60a5fa:#c084fc>Duel finished.</gradient> <gray>Winner:</gray> <white>" + winnerNames + "</white>");
        titleMembers(all, "<gradient:#60a5fa:#c084fc>" + winnerNames + " wins</gradient>", "<gray>Returning to spawn in 5 seconds</gray>");
        Bukkit.getScheduler().runTaskLater(plugin, () -> endMatch(match, winners, reason), 100L);
    }

    private Player firstOnline(Collection<UUID> uuids) {
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) return player;
        }
        return null;
    }
    private void spawnEditorMarker(Player player, String type) {
        placeEditorMarker(player, type, editorPlacementLocation(player, null));
    }

    private void placeEditorMarkerFromTool(Player player, String type, Location clickedLocation) {
        if (!mapEditorMode.contains(player.getUniqueId())) {
            Text.send(player, "<yellow>Enable map editor mode from /duel devpanel first.</yellow>");
            return;
        }
        placeEditorMarker(player, type, editorPlacementLocation(player, clickedLocation));
    }

    private void placeEditorMarker(Player player, String type, Location loc) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.equals("save-arena")) { saveArenaCommand(player); return; }
        if (!List.of("lobby", "spawn-a", "spawn-b", "spectator").contains(normalized)) {
            Text.send(player, "<red>Unknown marker type.</red>");
            return;
        }
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, armorStand -> {
            armorStand.setGravity(false);
            armorStand.setVisible(true);
            armorStand.setInvulnerable(true);
            armorStand.setCustomNameVisible(true);
            armorStand.setArms(true);
            armorStand.setBasePlate(false);
            armorStand.setRotation(loc.getYaw(), loc.getPitch());
            armorStand.customName(Text.mm("<gradient:#1A2A4A:#f59e0b>3SMP Duel " + normalized + "</gradient>"));
            armorStand.getPersistentDataContainer().set(new NamespacedKey(plugin, "duel_editor_marker"), PersistentDataType.STRING, normalized);
        });
        Text.send(player, "<green>Placed duel editor marker:</green> <white>" + normalized + "</white> <gray>yaw " + Math.round(loc.getYaw()) + " pitch " + Math.round(loc.getPitch()) + "</gray>");
    }

    private Location editorPlacementLocation(Player player, Location clickedLocation) {
        Location loc = player.getLocation().clone();
        if (clickedLocation != null && clickedLocation.getWorld() != null) loc = clickedLocation;
        loc.setWorld(player.getWorld());
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        return loc;
    }

    private void toggleMapEditorMode(Player player) {
        if (mapEditorMode.remove(player.getUniqueId())) {
            restoreEditorInventory(player);
            Text.send(player, "<yellow>Duel map editor mode disabled.</yellow>");
        } else {
            mapEditorMode.add(player.getUniqueId());
            giveEditorTools(player);
            Text.send(player, "<green>Duel map editor mode enabled.</green> <gray>Right-click marker tools to place stands.</gray>");
        }
    }

    private void giveEditorTools(Player player) {
        mapEditorMode.add(player.getUniqueId());
        editorInventorySnapshots.putIfAbsent(player.getUniqueId(), player.getInventory().getContents());
        player.getInventory().clear();
        String[] types = {"lobby", "spawn-a", "spawn-b", "spectator"};
        Material[] icons = {Material.LECTERN, Material.DIAMOND_SWORD, Material.SHIELD, Material.COMPASS};
        for (int i = 0; i < types.length; i++) player.getInventory().setItem(i, createEditorTool(icons[i], types[i]));
        player.getInventory().setItem(8, createEditorTool(Material.STRUCTURE_BLOCK, "save-arena"));
        Text.send(player, "<green>Duel editor tools loaded.</green> <gray>Right-click marker tools, then use Save Arena.</gray>");
    }

    private void removeEditorTools(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (editorToolId(player.getInventory().getItem(i)) != null) player.getInventory().setItem(i, null);
        }
    }

    private ItemStack createEditorTool(Material material, String type) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm("<gradient:#1A2A4A:#f59e0b>Marker: " + type + "</gradient>"));
        meta.lore(List.of(Text.mm("<gray>Right-click to place this marker exactly at your position.</gray>"), Text.mm("<gray>Your yaw and pitch are saved for spawn markers.</gray>")));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, EDITOR_TOOL_KEY), PersistentDataType.STRING, type);
        stack.setItemMeta(meta);
        return stack;
    }

    private String editorToolId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, EDITOR_TOOL_KEY), PersistentDataType.STRING);
    }

    private void saveMarkers(Player player, String mapId) {
        DuelMap base = maps.get(mapId);
        if (base == null) { Text.send(player, "<red>Map not found. Create it first with /duel map create " + mapId + ".</red>"); return; }
        Map<String, Location> found = new HashMap<>();
        NamespacedKey key = new NamespacedKey(plugin, "duel_editor_marker");
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            String type = stand.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (type != null) found.put(type, stand.getLocation());
        }
        DuelMap updated = base;
        if (found.containsKey("lobby")) updated = updated.withLobby(found.get("lobby"));
        if (found.containsKey("spawn-a")) updated = updated.withSpawnA(found.get("spawn-a"));
        if (found.containsKey("spawn-b")) updated = updated.withSpawnB(found.get("spawn-b"));
        if (found.containsKey("spectator")) updated = updated.withSpectator(found.get("spectator"));
        int removedMarkers = removeEditorMarkers(player.getWorld());
        if (player.getWorld() != null) player.getWorld().save();
        World live = worldService.publishArena(mapId, player.getWorld());
        if (live != null) {
            updated = new DuelMap(updated.id(), updated.displayName(), updated.enabled(), live.getName(), toWorld(updated.lobby(), live), toWorld(updated.spawnA(), live), toWorld(updated.spawnB(), live), toWorld(updated.spectator(), live));
        } else if (player.getWorld() != null && player.getWorld().getName().startsWith("arena_")) {
            updated = new DuelMap(updated.id(), updated.displayName(), updated.enabled(), player.getWorld().getName(), updated.lobby(), updated.spawnA(), updated.spawnB(), updated.spectator());
        }
        maps.put(mapId, updated);
        saveMaps();        Text.send(player, "<green>Saved arena " + mapId + ". Found markers: " + String.join(", ", found.keySet()) + ".</green> <gray>Removed " + removedMarkers + " editor markers.</gray>");
        finishMapEditorAfterSave(player);
    }

    private Location toWorld(Location location, World world) {
        if (location == null || world == null) return location;
        return new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }
    private void finishMapEditorAfterSave(Player player) {
        selectedEditorMap.remove(player.getUniqueId());
        mapEditorMode.remove(player.getUniqueId());
        restoreEditorInventory(player);
        Location spawn = spawnLocation();
        player.setGameMode(GameMode.SURVIVAL);
        if (spawn != null && spawn.getWorld() != null) player.teleport(spawn);
        reloadItems(player);
        partyService.givePartyItems(player);
        for (Consumer<Player> refresher : postMatchItemRefreshers) refresher.accept(player);
        player.updateInventory();
        Text.send(player, "<gray>Arena editor saved and closed.</gray>");
    }

    private int removeEditorMarkers(World world) {
        if (world == null) return 0;
        NamespacedKey key = new NamespacedKey(plugin, "duel_editor_marker");
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (!stand.getPersistentDataContainer().has(key, PersistentDataType.STRING)) continue;
            stand.remove();
            removed++;
        }
        return removed;
    }
    private void selectMapEditor(Player player, String mapId) {
        DuelMap map = maps.get(mapId);
        if (map == null) { Text.send(player, "<red>Map not found.</red>"); return; }
        World source = Bukkit.getWorld(map.worldName());
        World editor = worldService.createEditorWorld(mapId, source);
        if (editor == null) { Text.send(player, "<red>Could not create editor world.</red>"); return; }
        selectedEditorMap.put(player.getUniqueId(), mapId);
        player.teleport(editor.getSpawnLocation());
        mapEditorMode.add(player.getUniqueId());
        giveEditorTools(player);
        Text.send(player, "<green>Editing map " + mapId + " in world " + editor.getName() + ".</green>");
        openMapEditor(player);
    }

    private void deleteMap(Player player, String mapId) {
        maps.remove(mapId);
        saveMaps();
        worldService.deleteEditorWorld(mapId);
        Text.send(player, "<yellow>Deleted map " + mapId + " and its editor world if present.</yellow>");
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
                selectMapEditor(player, id);
                Text.send(player, "<green>Map created: " + id + "</green>");
            }
            case "select" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map select <id></red>"); return; } selectMapEditor(player, context.arg(2).toLowerCase(Locale.ROOT)); }
            case "delete" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map delete <id></red>"); return; } deleteMap(player, context.arg(2).toLowerCase(Locale.ROOT)); }
            case "editor" -> { openMapEditor(player); Text.send(player, "<gray>Select a map with /duel map select <id>, place armor stand markers, then save.</gray>"); }
            case "marker" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map marker <lobby|spawn-a|spawn-b|spectator></red>"); return; } spawnEditorMarker(player, context.arg(2).toLowerCase(Locale.ROOT)); }
            case "savemarkers" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map savemarkers <mapId></red>"); return; } saveMarkers(player, context.arg(2).toLowerCase(Locale.ROOT)); }
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
        try { yaml.save(new java.io.File(plugin.getDataFolder(), "duels/maps.yml")); } catch (Exception e) { throw new IllegalStateException("Failed to save duel maps", e); }
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
            for (Player player : Bukkit.getOnlinePlayers()) giveQueueItem(player);
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
            if (mode == DuelMode.PARTY) matchPartyQueue(list);
            else while (list.size() >= 2) {
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

    private void matchPartyQueue(List<QueueUnit> list) {
        while (list.size() >= 2) {
            QueueUnit a = selectBestUnit(list);
            if (a == null) return;
            list.remove(a);
            QueueUnit b = selectMatchFor(list, a);
            if (b == null) return;
            list.remove(b);
            if (a.members().size() == 1 && b.members().size() == 1 && list.size() >= 2) {
                QueueUnit c = selectBestUnit(list);
                list.remove(c);
                QueueUnit d = selectMatchFor(list, c);
                if (d == null || d.members().size() != 1) { list.add(c); startMatch(a, b); return; }
                list.remove(d);
                QueueUnit teamOne = combinedParty(a, c);
                QueueUnit teamTwo = combinedParty(b, d);
                removeQueueUnit(a, false); removeQueueUnit(b, false); removeQueueUnit(c, false); removeQueueUnit(d, false);
                startMatch(teamOne, teamTwo);
            } else startMatch(a, b);
        }
    }

    private QueueUnit combinedParty(QueueUnit first, QueueUnit second) {
        Set<UUID> members = new LinkedHashSet<>(first.members());
        members.addAll(second.members());
        return new QueueUnit(UUID.randomUUID(), DuelMode.PARTY, first.kitId(), members, Math.min(first.joinedAt(), second.joinedAt()));
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
        UUID matchId = UUID.randomUUID();
        DuelWorldService.InstancedMap instanced = worldService.create(map, matchId);
        DuelMap activeMap = instanced.map();
        DuelMatch match = new DuelMatch(matchId, first.mode(), first.kitId(), activeMap.id(), first.members(), second.members(), System.currentTimeMillis());
        if (instanced.world() != null) instanceWorldsByMatch.put(matchId, instanced.world());
        first.members().forEach(member -> { matchesByPlayer.put(member, match); ACTIVE_DUEL_PLAYERS.add(member); });
        second.members().forEach(member -> { matchesByPlayer.put(member, match); ACTIVE_DUEL_PLAYERS.add(member); });
        removeQueueUnit(first, false);
        removeQueueUnit(second, false);
        notifyMembers(match.teamOne(), "<green>Match found. Starting on " + activeMap.displayName() + "</green>");
        notifyMembers(match.teamTwo(), "<green>Match found. Starting on " + activeMap.displayName() + "</green>");
        prepareMatch(match, activeMap);
        int seconds = Math.max(1, configs.get("duels/duels.yml").getInt("duels.countdown.seconds", 5));
        new BukkitRunnable() {
            int remaining = seconds;
            @Override public void run() {
                if (!matchesByPlayer.containsKey(match.teamOne().iterator().next()) || remaining < 0) { cancel(); return; }
                if (remaining == 0) {
                    beginFight(match, activeMap);
                    cancel();
                    return;
                }
                notifyMembers(match.teamOne(), "<gradient:#60a5fa:#c084fc>Duel starts in " + remaining + "</gradient>");
                notifyMembers(match.teamTwo(), "<gradient:#60a5fa:#c084fc>Duel starts in " + remaining + "</gradient>");
                titleMembers(match.teamOne(), "<gradient:#60a5fa:#c084fc>" + remaining + "</gradient>", "<gray>Get ready</gray>");
                titleMembers(match.teamTwo(), "<gradient:#60a5fa:#c084fc>" + remaining + "</gradient>", "<gray>Get ready</gray>");
                soundMembers(match.teamOne(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.25f);
                soundMembers(match.teamTwo(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.25f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void prepareMatch(DuelMatch match, DuelMap map) {
        applyTeam(match.teamOne(), map.spawnA(), match.kitId());
        applyTeam(match.teamTwo(), map.spawnB(), match.kitId());
        frozenDuelPlayers.addAll(match.teamOne());
        frozenDuelPlayers.addAll(match.teamTwo());
        titleMembers(match.teamOne(), "<gradient:#60a5fa:#c084fc>Get Ready</gradient>", "<gray>You can arrange your hotbar.</gray>");
        titleMembers(match.teamTwo(), "<gradient:#60a5fa:#c084fc>Get Ready</gradient>", "<gray>You can arrange your hotbar.</gray>");
    }

    private void beginFight(DuelMatch match, DuelMap map) {
        frozenDuelPlayers.removeAll(match.teamOne());
        frozenDuelPlayers.removeAll(match.teamTwo());
        notifyMembers(match.teamOne(), "<green>Fight!</green>");
        notifyMembers(match.teamTwo(), "<green>Fight!</green>");
        titleMembers(match.teamOne(), "<gradient:#34d399:#22c55e>START!</gradient>", "<gray>Good luck.</gray>");
        titleMembers(match.teamTwo(), "<gradient:#34d399:#22c55e>START!</gradient>", "<gray>Good luck.</gray>");
        soundMembers(match.teamOne(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        soundMembers(match.teamTwo(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        autoSplashTeam(match.teamOne(), match.kitId());
        autoSplashTeam(match.teamTwo(), match.kitId());
    }

    private void autoSplashTeam(Set<UUID> members, String kitId) {
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null || !kit.autoApplyPotions()) return;
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) autoSplashPotions(player, kit);
        }
    }

    private void autoSplashPotions(Player player, DuelKit kit) {
        for (String encoded : kit.autoPotions()) {
            ItemStack stack = materialItem(encoded);
            if (stack == null || stack.getType() != Material.SPLASH_POTION) continue;
            if (!(stack.getItemMeta() instanceof PotionMeta)) continue;
            ItemStack potion = stack.clone();
            potion.setAmount(1);
            player.getWorld().spawn(player.getEyeLocation().add(0, 0.4, 0), ThrownPotion.class, thrown -> {
                thrown.setShooter(player);
                thrown.setItem(potion);
                thrown.setVelocity(player.getLocation().toVector().subtract(thrown.getLocation().toVector()).normalize().multiply(0.15).setY(-0.45));
            });
        }
    }

    private void resetDuelState(Player player) {
        for (org.bukkit.potion.PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        player.setFoodLevel(20);
        player.setSaturation(0.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
    }

    private void soundMembers(Set<UUID> members, Sound sound, float volume, float pitch) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.playSound(player.getLocation(), sound, volume, pitch);
        }
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
            resetDuelState(player);
            stripHubItems(player);
            if (kit != null) applyKit(player, kit);
            player.teleport(spawn);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(0.0f);
            player.setFireTicks(0);
        }
    }


    private void refreshPostMatchItems(Player player) {
        if (player == null || !player.isOnline()) return;
        reloadItems(player);
        partyService.givePartyItems(player);
        for (Consumer<Player> refresher : postMatchItemRefreshers) refresher.accept(player);
        player.updateInventory();
    }

    private void stripHubItems(Player player) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private void applyKit(Player player, DuelKit kit) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        for (String item : kit.contents()) addItem(player, item);
        for (int i = 0; i < kit.armor().size() && i < 4; i++) setArmor(player, i, kit.armor().get(i));
        if (!kit.offhand().isEmpty()) setOffhand(player, kit.offhand().get(0));
    }

    private void addItem(Player player, String encoded) {
        SlottedItem slotted = parseSlottedItem(encoded);
        if (slotted.item() == null) return;
        ItemStack item = decorateDurability(slotted.item());
        if (slotted.slot() >= 0 && slotted.slot() < 36) player.getInventory().setItem(slotted.slot(), item);
        else player.getInventory().addItem(item);
    }

    private void setArmor(Player player, int index, String encoded) {
        ItemStack item = materialItem(encoded);
        if (item == null) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null || armor.length < 4) armor = new ItemStack[4];
        armor[index] = decorateDurability(item);
        player.getInventory().setArmorContents(armor);
    }

    private void setOffhand(Player player, String encoded) {
        ItemStack item = materialItem(encoded);
        if (item != null) player.getInventory().setItemInOffHand(decorateDurability(item));
    }

    private ItemStack decorateDurability(ItemStack item) {
        if (item == null || item.getType().getMaxDurability() <= 0) return item;
        ItemStack copy = item.clone();
        if (!(copy.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)) return copy;
        int max = copy.getType().getMaxDurability();
        int left = Math.max(0, max - damageable.getDamage());
        List<net.kyori.adventure.text.Component> lore = damageable.lore() == null ? new ArrayList<>() : new ArrayList<>(damageable.lore());
        lore.removeIf(component -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component).startsWith("Durability:"));
        lore.add(Text.mm("<dark_gray>Durability:</dark_gray> <white>" + left + "</white><gray>/" + max + "</gray>"));
        damageable.lore(lore);
        copy.setItemMeta((ItemMeta) damageable);
        return copy;
    }
    private Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return null; }
    }

    private void endMatch(DuelMatch match, Set<UUID> winners, String reason) {
        if (!endingMatches.contains(match.id())) endingMatches.add(match.id());
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(match.teamOne());
        all.addAll(match.teamTwo());
        Location spawn = spawnLocation();
        String winnerNames = names(winners);
        try {
            for (UUID uuid : all) {
                matchesByPlayer.remove(uuid);
                ACTIVE_DUEL_PLAYERS.remove(uuid);
                frozenDuelPlayers.remove(uuid);
                Snapshot snapshot = snapshots.remove(uuid);
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                if (player.isDead()) player.spigot().respawn();
                try {
                    if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
                } catch (IllegalArgumentException ignored) {}
                if (snapshot != null) snapshot.restore(player, spawn);
                player.setGameMode(GameMode.SURVIVAL);
                stripHubItems(player);
                player.teleport(spawn);
                if (player.getAttribute(Attribute.MAX_HEALTH) != null) player.setHealth(Math.max(1.0, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
                player.setFireTicks(0);
                Bukkit.getScheduler().runTaskLater(plugin, () -> refreshPostMatchItems(player), 2L);
                boolean win = winners.contains(uuid);
                Text.send(player, win ? "<green>You won the duel.</green> <gray>Winner: <white>" + winnerNames + "</white></gray>" : "<gray>Duel ended: " + reason + ". Winner: <white>" + winnerNames + "</white></gray>");
            }
            balanceRatings(match.teamOne(), match.teamTwo(), winners);
        } finally {
            for (UUID uuid : all) {
                matchesByPlayer.remove(uuid);
                ACTIVE_DUEL_PLAYERS.remove(uuid);
                frozenDuelPlayers.remove(uuid);
                snapshots.remove(uuid);
            }
            org.bukkit.World instance = instanceWorldsByMatch.remove(match.id());
            if (instance != null) worldService.cleanup(instance);
            endingMatches.remove(match.id());
        }
    }

    private Location spawnLocation() {
        String worldName = configs.get("core/config.yml").getString("spawn.world", "spawn");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
        if (world == null) return new Location(null, 0, 80, 0);
        org.bukkit.configuration.ConfigurationSection section = configs.get("core/config.yml").getConfigurationSection("spawn.location");
        if (section == null) return world.getSpawnLocation();
        return new Location(world, section.getDouble("x", world.getSpawnLocation().getX()), section.getDouble("y", world.getSpawnLocation().getY()), section.getDouble("z", world.getSpawnLocation().getZ()), (float) section.getDouble("yaw", 0.0), (float) section.getDouble("pitch", 0.0));
    }
    private String names(Collection<UUID> uuids) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            names.add(player == null ? uuid.toString().substring(0, 8) : player.getName());
        }
        return String.join(", ", names);
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

    private boolean canUseInWorld(Player player) {
        String world = player.getWorld().getName();
        java.util.List<String> denied = configs.get("duels/duels.yml").getStringList("duels.worlds.disabled");
        java.util.List<String> allowed = configs.get("duels/duels.yml").getStringList("duels.worlds.enabled");
        if (!denied.isEmpty() && denied.stream().anyMatch(w -> w.equalsIgnoreCase(world))) return false;
        return allowed.isEmpty() || allowed.stream().anyMatch(w -> w.equalsIgnoreCase(world));
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

    private void actionBarMembers(Collection<UUID> members, String message) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) Text.actionBar(player, message);
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

    private DuelMap pickMap() {
        List<DuelMap> enabledMaps = maps.values().stream().filter(DuelMap::enabled).toList();
        if (enabledMaps.isEmpty()) return null;
        if (enabledMaps.size() == 1) {
            lastPickedMapId = enabledMaps.get(0).id();
            return enabledMaps.get(0);
        }
        List<DuelMap> choices = enabledMaps.stream().filter(map -> !map.id().equalsIgnoreCase(lastPickedMapId)).toList();
        DuelMap picked = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        lastPickedMapId = picked.id();
        return picked;
    }

    private String selectedKitForParty() { return kits.values().stream().filter(DuelKit::enabled).findFirst().map(DuelKit::id).orElse(null); }

    private void loadKits() {
        var section = configs.get("duels/kits.yml").getConfigurationSection("kits");
        if (section == null) return;
        int fallbackSlot = 10;
        for (String id : section.getKeys(false)) {
            var kit = section.getConfigurationSection(id);
            if (kit == null) continue;
            int slot = kit.getInt("slot", fallbackSlot++);
            DuelKit definition = new DuelKit(id.toLowerCase(Locale.ROOT), kit.getString("display-name", id), parseMaterialOrDefault(kit.getString("icon", "IRON_SWORD"), Material.IRON_SWORD), kit.getString("permission", ""), slot, kit.getBoolean("enabled", true), kit.getStringList("lore"), kit.getStringList("contents"), kit.getStringList("armor"), kit.getStringList("offhand"), kit.getBoolean("auto-apply-potions", false), kit.getStringList("auto-potions"), Math.max(1, kit.getInt("rounds", 1)), kit.getBoolean("health-indicator", false));
            kits.put(definition.id(), definition);
            kitSlots.put(slot, definition.id());
        }
    }

    private void loadMaps() {
        var section = configs.get("duels/maps.yml").getConfigurationSection("maps");
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
    private record SpectatorSnapshot(Location location, GameMode gameMode) { static SpectatorSnapshot capture(Player player) { return new SpectatorSnapshot(player.getLocation(), player.getGameMode()); } }
    private record PendingKitRoundEdit(String kitId, Location location, org.bukkit.block.data.BlockData previous) {}

    private static final class Snapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final Location location;
        private final GameMode gameMode;
        private final double health;
        private final int food;

        private Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, Location location, GameMode gameMode, double health, int food) {
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
            this.location = location;
            this.gameMode = gameMode;
            this.health = health;
            this.food = food;
        }

        static Snapshot capture(Player player) {
            return new Snapshot(player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand(), player.getLocation(), player.getGameMode(), player.getHealth(), player.getFoodLevel());
        }

        void restore(Player player, Location fallbackSpawn) {
            player.setGameMode(gameMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : gameMode);
            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offhand);
            player.teleport(fallbackSpawn == null || fallbackSpawn.getWorld() == null ? location : fallbackSpawn);
            if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
                player.setHealth(Math.max(1.0, Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue())));
            }
            player.setFoodLevel(food);
        }
    }
}











































