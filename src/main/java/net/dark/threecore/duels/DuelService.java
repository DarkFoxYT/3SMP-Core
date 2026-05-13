package net.dark.threecore.duels;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.gui.DuelMenu;
import net.dark.threecore.duels.DuelLeaderboardService;
import net.dark.threecore.duels.DuelGuiManager;
import net.dark.threecore.duels.DuelMessageService;
import net.dark.threecore.duels.DuelQueueManager;
import net.dark.threecore.duels.DuelScoreService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.duels.model.DuelKit;
import net.dark.threecore.duels.model.DuelGateRegion;
import net.dark.threecore.duels.model.DuelMap;
import net.dark.threecore.duels.model.DuelMatch;
import net.dark.threecore.duels.model.DuelMode;
import net.dark.threecore.duels.rank.DuelRankedService;
import net.dark.threecore.duels.rank.DuelRankedUpdate;
import net.dark.threecore.duels.stats.DuelMatchStatsService;
import net.dark.threecore.duels.stats.DuelMatchStatsService.DuelMatchStats;
import net.dark.threecore.duels.stats.DuelMatchStatsService.PlayerDuelStats;
import net.dark.threecore.duels.validation.RankedMatchValidationResult;
import net.dark.threecore.duels.validation.RankedMatchValidationService;
import net.dark.threecore.duels.gate.DuelGateService;
import net.dark.threecore.duels.event.DuelRoundWinEvent;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.launchpads.LaunchpadService;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.potion.PotionEffectType;

public final class DuelService implements Listener {
    private static final String ITEM_ID_KEY = "3smpcore_duel_item";
    private static final String QUEUE_ITEM_ID = "queue_sword";
    private static final String LOADOUT_ITEM_ID = "loadout_editor";
    private static final String LOADOUT_PANE_ID = "loadout_pane";
    private static final String LOADOUT_SAVE_ID = "loadout_save";
    private static final String LOADOUT_CLOSE_ID = "loadout_close";
    private static final String LOADOUT_KIT_PREFIX = "loadout_kit_";
    private static final String SPECTATOR_TP_ONE_ID = "spectator_tp_one";
    private static final String SPECTATOR_TP_TWO_ID = "spectator_tp_two";
    private static final String SPECTATOR_VISIBILITY_ID = "spectator_visibility";
    private static final String SPECTATOR_INFO_ID = "spectator_info";
    private static final String SPECTATOR_LEAVE_ID = "spectator_leave";
    private static final String EDITOR_TOOL_KEY = "3smpcore_duel_editor_tool";
    private static final String HEALTH_OBJECTIVE = "duel_health";
    private static final String DUEL_SIDEBAR_OBJECTIVE = "duel_side";
    private static final Set<UUID> ACTIVE_DUEL_PLAYERS = new HashSet<>();
    private static final Set<Material> ROUND_RESET_MATERIALS = EnumSet.of(
            Material.OAK_PLANKS,
            Material.COBWEB,
            Material.OBSIDIAN,
            Material.COBBLESTONE,
            Material.STONE,
            Material.LAVA,
            Material.WATER
    );
    
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MenuService menuService;
    private final PartyService partyService;
    private final DuelLeaderboardService leaderboardService;
    private final DuelGuiManager guiManager;
    private final DuelMessageService messageService;
    private final DuelScoreService scoreService;
    private final DuelQueueManager queueManager;
    private final DuelGateService gateService;
    private final DuelRankedService rankedService;
    private final DuelMatchStatsService matchStatsService;
    private final RankedMatchValidationService rankedValidationService;
    private final LaunchpadService launchpadService;
    private DungeonService dungeonService;
    private final DuelWorldService worldService;
    private final Map<String, DuelKit> kits = new LinkedHashMap<>();
    private final Map<Integer, String> kitSlots = new HashMap<>();
    private final Map<String, DuelMap> maps = new LinkedHashMap<>();
    private final Map<UUID, QueueUnit> queueByPlayer = new HashMap<>();
    private final Map<UUID, QueueUnit> queueUnits = new HashMap<>();
    private final Map<UUID, Integer> queueSearchRangeNotices = new HashMap<>();
    private final Map<UUID, UUID> recentOpponents = new HashMap<>();
    private final Map<String, Deque<UUID>> soloQueues = new HashMap<>();
    private final Map<String, Deque<UUID>> partyQueues = new HashMap<>();
    private final Map<String, Deque<UUID>> ffaQueues = new HashMap<>();
    private final Map<UUID, DuelMatch> matchesByPlayer = new HashMap<>();
    private final Map<UUID, BukkitTask> countdownTasksByMatch = new HashMap<>();
    private final Map<UUID, String> pendingChallengeTargets = new HashMap<>();
    private final Map<UUID, DuelMode> pendingDeluxeKitMode = new HashMap<>();
    private final Set<UUID> pendingRankedKitSelection = new HashSet<>();
    private final Set<UUID> frozenDuelPlayers = new HashSet<>();
    private final Set<UUID> preparingDuelPlayers = new HashSet<>();
    private final Map<UUID, SpectatorSnapshot> spectatorSnapshots = new HashMap<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, org.bukkit.World> instanceWorldsByMatch = new HashMap<>();
    private final Map<UUID, DuelMap> activeMapsByMatch = new HashMap<>();
    private final Map<UUID, String> selectedEditorMap = new HashMap<>();
    private final Set<UUID> devMode = new HashSet<>();
    private final Set<UUID> mapEditorMode = new HashSet<>();
    private final Set<UUID> endingMatches = new HashSet<>();
    private final Set<String> placedMatchBlocks = new HashSet<>();
    private final Map<UUID, MatchTeams> scoreboardTeamsByMatch = new HashMap<>();
    private final Map<UUID, Map<UUID, Scoreboard>> duelScoreboardsByMatch = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboardsByPlayer = new HashMap<>();
    private final Map<UUID, PlayerNameState> duelNameStates = new HashMap<>();
    private final Set<String> healthIndicatorEntries = new HashSet<>();
    private final Map<UUID, ItemStack[]> editorInventorySnapshots = new HashMap<>();
    private final Map<UUID, LoadoutButtonDisplacement> loadoutButtonDisplacements = new HashMap<>();
    private final Map<UUID, PendingKitRoundEdit> pendingKitRoundEdits = new HashMap<>();
    private final Map<String, Location> gateSelectionPos1 = new HashMap<>();
    private final Map<String, Location> gateSelectionPos2 = new HashMap<>();
    private final Map<UUID, GateEditorTarget> gateEditorTargets = new HashMap<>();
    private final Map<UUID, DuelChallenge> challengesByTarget = new HashMap<>();
    private final Map<UUID, DuelChallenge> challengesByChallenger = new HashMap<>();
    private final List<Consumer<Player>> postMatchItemRefreshers = new ArrayList<>();
    private BukkitTask hudTask;
    private BukkitTask healthIndicatorTask;
    private boolean enabled = true;
    private String lastPickedMapId = "";

    public DuelService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService, PartyService partyService, DuelLeaderboardService leaderboardService, LaunchpadService launchpadService, DungeonService dungeonService) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
        this.partyService = partyService;
        this.leaderboardService = leaderboardService;
        this.guiManager = new DuelGuiManager(this);
        this.messageService = new DuelMessageService(plugin, configs);
        this.scoreService = new DuelScoreService(repository);
        this.queueManager = new DuelQueueManager();
        this.gateService = new DuelGateService(plugin, configs);
        this.rankedService = new DuelRankedService(repository, configs);
        this.matchStatsService = new DuelMatchStatsService();
        this.rankedValidationService = new RankedMatchValidationService(plugin, configs);
        this.launchpadService = launchpadService;
        this.dungeonService = dungeonService;
        this.worldService = new DuelWorldService(plugin, configs);
        this.worldService.cleanupStaleMatchWorlds();
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
        ensureDeluxeMenusExamples();
        if (hudTask != null) startHudTask();
    }

    public void handle(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        String sub = context.arg(0).toLowerCase(Locale.ROOT);
        if (!canUseInWorld(player) && !List.of("editor", "map", "arena", "admin", "devpanel", "mapeditor", "savearena").contains(sub)) {
            Text.send(player, "<red>Duels are not available in this world.</red>");
            return;
        }
        boolean adminAccess = player.isOp() || player.hasPermission("3smpcore.duel.admin");
        Player directTarget = sub.isBlank() ? null : Bukkit.getPlayerExact(context.arg(0));
        if (!adminAccess && directTarget == null && !isPublicDuelSubcommand(sub)) {
            Text.send(player, "<yellow>Use <white>/duel <player></white> to challenge someone.</yellow>");
            return;
        }
        switch (sub) {
            case "", "menu" -> openMainMenu(player);
            case "queueui", "queues", "chest", "chestui" -> openPluginMainMenu(player);
            case "solo", "1v1" -> {
                pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.SOLO);
                openPluginKitMenu(player);
            }
            case "ranked" -> {
                if (context.args().length > 1) queueRankedSolo(player, resolveKit(context.arg(1)));
                else {
                    pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.SOLO);
                    pendingRankedKitSelection.add(player.getUniqueId());
                    openPluginKitMenu(player);
                }
            }
            case "duo", "party", "2v2" -> {
                pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.PARTY);
                openPluginKitMenu(player);
            }
            case "ffa" -> {
                Text.send(player, "<yellow>FFA is only available from party duels.</yellow> <gray>Use <white>/party duel</white>.</gray>");
                partyService.openPartyDuelMenu(player);
            }
            case "deluxe", "dm" -> handleDeluxeAction(player, context);
            case "accept" -> acceptChallenge(player);
            case "deny", "decline" -> denyChallenge(player);
            case "leaderboard", "top" -> openLeaderboard(player);
            case "challenge" -> challenge(player, context.arg(1), context.arg(2), context.arg(3));
            case "queue" -> {
                if (context.args().length > 1 && context.arg(1).equalsIgnoreCase("party")) queueParty(player);
                else openPluginMainMenu(player);
            }
            case "leave" -> leaveDuelOrQueue(player);
            case "spec", "spectate" -> spectate(player, context.arg(1));
            case "kiteditor" -> { if (context.args().length >= 2) openKitEditor(player, context.arg(1)); else openKitEditorSelector(player); }
            case "devpanel", "mapeditor" -> openDevMenu(player);
            case "test" -> runTestDuel(player);
            case "admin" -> handleAdmin(player, context);
            case "editor" -> handleDuelEditor(player, context);
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
        if (context.args().length <= 1) { java.util.List<String> out = new java.util.ArrayList<>(); if (context.sender() instanceof Player p && (p.isOp() || p.hasPermission("3smpcore.duel.admin") || p.hasPermission("3smpcore.duel.editor"))) out.addAll(List.of("menu", "queueui", "challenge", "accept", "deny", "queue", "ranked", "leave", "spec", "spectate", "leaderboard", "test", "kiteditor", "devpanel", "mapeditor", "editor", "map", "arena", "admin")); else out.addAll(List.of("menu", "queueui", "ranked", "accept", "deny", "leave")); for (Player online : Bukkit.getOnlinePlayers()) out.add(online.getName()); return out; }
        if ((context.arg(0).equalsIgnoreCase("spec") || context.arg(0).equalsIgnoreCase("spectate")) && context.args().length <= 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (context.arg(0).equalsIgnoreCase("queue") && context.args().length <= 2) return List.of("party");
        if (context.arg(0).equalsIgnoreCase("ranked") && context.args().length <= 2) return kits.values().stream().filter(DuelKit::enabled).map(DuelKit::id).toList();
        if (context.arg(0).equalsIgnoreCase("editor") && context.args().length <= 2) return List.of("wand", "gate", "preview", "clear", "save", "marker");
        if (context.arg(0).equalsIgnoreCase("editor") && context.arg(1).equalsIgnoreCase("wand") && context.args().length <= 3) return List.of("red", "blue", "red-zone", "blue-zone");
        if (context.arg(0).equalsIgnoreCase("editor") && context.arg(1).equalsIgnoreCase("gate") && context.args().length <= 3) return List.of("red", "blue");
        if (context.arg(0).equalsIgnoreCase("editor") && context.arg(1).equalsIgnoreCase("zone") && context.args().length <= 3) return List.of("red", "blue");
        if (context.arg(0).equalsIgnoreCase("editor") && context.arg(1).equalsIgnoreCase("marker") && context.args().length <= 3) return List.of("red-spawn", "blue-spawn", "ffa-spawn", "red-gate-out", "blue-gate-out", "lobby", "spectator");
        if (context.arg(0).equalsIgnoreCase("editor") && context.arg(1).equalsIgnoreCase("clear") && context.args().length <= 4) return List.of("gate", "zone");
        if (context.arg(0).equalsIgnoreCase("map") && context.args().length <= 2) return List.of("create", "select", "delete", "editor", "marker", "savemarkers", "setlobby", "setspawna", "setspawnb", "setffa", "setspec", "save", "list", "enable", "disable");
        if (context.arg(0).equalsIgnoreCase("test") && context.args().length <= 2) return List.of("arena");
        if (context.arg(0).equalsIgnoreCase("admin") && context.args().length <= 2) return List.of("reload", "toggle");
        return List.of();
    }

    public Collection<DuelKit> kits() { return kits.values(); }
    public Collection<DuelMap> maps() { return maps.values(); }
    public void shutdown() {
        gateService.resetAll();
        matchStatsService.clearAll();
        rankedValidationService.clear();
        clearAllHealthIndicators();
        restoreAllDuelVisualStates();
        scoreboardTeamsByMatch.clear();
        duelScoreboardsByMatch.clear();
    }
    public boolean isQueuedForKit(UUID uuid, String kitId) { QueueUnit unit = queueByPlayer.get(uuid); return unit != null && unit.kitId().equalsIgnoreCase(kitId); }
    public String queueSummary(UUID uuid) {
        QueueUnit unit = queueByPlayer.get(uuid);
        if (unit == null) return "none";
        String mode = unit.ranked() ? "Ranked " + modeLabel(unit.mode()) : modeLabel(unit.mode());
        DuelKit kit = kits.get(unit.kitId().toLowerCase(Locale.ROOT));
        return mode + " / " + (kit == null ? unit.kitId() : kit.displayName());
    }
    public String queueModeName(UUID uuid) {
        QueueUnit unit = queueByPlayer.get(uuid);
        if (unit == null) return "none";
        return unit.ranked() ? "Ranked " + modeLabel(unit.mode()) : modeLabel(unit.mode());
    }
    public String queueKitName(UUID uuid) {
        QueueUnit unit = queueByPlayer.get(uuid);
        if (unit == null) return "none";
        DuelKit kit = kits.get(unit.kitId().toLowerCase(Locale.ROOT));
        return kit == null ? unit.kitId() : kit.displayName();
    }
    public boolean isPlayerInDuel(UUID uuid) { return matchesByPlayer.containsKey(uuid); }
    public boolean isInMatch(UUID uuid) { return matchesByPlayer.containsKey(uuid); }
    public String teamColorId(UUID uuid) {
        DuelMatch match = matchesByPlayer.get(uuid);
        if (match == null) return "";
        if (match.mode() == DuelMode.FFA) return "ffa";
        if (match.teamOne().contains(uuid)) return "red";
        if (match.teamTwo().contains(uuid)) return "blue";
        return "";
    }
    public static boolean isDuelPlayer(Player player) { return player != null && ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId()); }
    public void addPostMatchItemRefresher(Consumer<Player> refresher) { postMatchItemRefreshers.add(refresher); }

    private boolean isFrozenDuelPlayer(Player player) {
        return player != null && frozenDuelPlayers.contains(player.getUniqueId());
    }

    private void refreshFrozenInventory(Player player) {
        if (player != null) Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    public void openMainMenu(Player player) {
        if (openDeluxeMenu(player, "menus.deluxemenus.main-menu", "duels_menu")) return;
        menuService.open(player, guiManager.buildMain(player));
    }
    public void openPluginMainMenu(Player player) { menuService.open(player, guiManager.buildMain(player)); }
    public void openSummary(Player player) { menuService.open(player, guiManager.buildSummary(player)); }

    public void openKitMenu(Player player) {
        if (openDeluxeMenu(player, "menus.deluxemenus.kits-menu", "duels_kits")) return;
        menuService.open(player, guiManager.buildKitSelector(player));
    }
    public void openPluginKitMenu(Player player) { menuService.open(player, guiManager.buildKitSelector(player)); }
    private void openChallengeKitMenu(Player player, Player target) {
        pendingChallengeTargets.put(player.getUniqueId(), target.getName());
        menuService.open(player, new DuelMenu(this).buildKitMenu(player, "<gradient:#60a5fa:#c084fc>Choose Kit vs " + target.getName() + "</gradient>"));
        Text.actionBar(player, "<gray>Pick a kit to challenge</gray> <white>" + target.getName() + "</white>");
    }
    public void openDevMenu(Player player) { menuService.open(player, new DuelMenu(this).buildDev(player)); }
    public void openLeaderboard(Player player) { leaderboardService.open(player); }

    private boolean openDeluxeMenu(Player player, String path, String fallbackMenu) {
        if (!configs.get("menus/duels.yml").getBoolean("menus.deluxemenus.enabled", true)) return false;
        if (Bukkit.getPluginManager().getPlugin("DeluxeMenus") == null) return false;
        String menu = configs.get("menus/duels.yml").getString(path, fallbackMenu);
        if (menu == null || menu.isBlank()) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open " + menu + " " + player.getName());
        return true;
    }

    private void handleDeluxeAction(Player player, CommandContext context) {
        String action = context.arg(1).toLowerCase(Locale.ROOT);
        switch (action) {
            case "", "menu", "main" -> openMainMenu(player);
            case "queueui", "queues", "chest", "chestui" -> openPluginMainMenu(player);
            case "kits", "kit" -> openKitMenu(player);
            case "solo" -> {
                pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.SOLO);
                openPluginKitMenu(player);
            }
            case "party", "duo", "2v2" -> {
                pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.PARTY);
                openPluginKitMenu(player);
            }
            case "ffa" -> {
                Text.send(player, "<yellow>FFA is only available from party duels.</yellow> <gray>Use <white>/party duel</white>.</gray>");
                partyService.openPartyDuelMenu(player);
            }
            case "leaderboard", "top" -> openLeaderboard(player);
            case "leave" -> leaveDuelOrQueue(player);
            case "queue" -> {
                String kit = resolveKit(context.arg(2));
                if (kit == null) Text.send(player, "<red>No enabled duel kit is configured.</red>");
                else {
                    DuelMode mode = pendingDeluxeKitMode.remove(player.getUniqueId());
                    boolean ranked = pendingRankedKitSelection.remove(player.getUniqueId());
                    if (ranked) queueRankedSolo(player, kit);
                    else if (mode == DuelMode.PARTY) queueParty(player, kit);
                    else queueSolo(player, kit);
                }
            }
            case "ranked" -> {
                pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.SOLO);
                pendingRankedKitSelection.add(player.getUniqueId());
                openPluginKitMenu(player);
            }
            default -> Text.send(player, "<yellow>Use /duel deluxe menu|solo|party|leaderboard|leave|queue <kit></yellow>");
        }
    }

    private void ensureDeluxeMenusExamples() {
        var yaml = configs.get("menus/duels.yml");
        if (!yaml.getBoolean("menus.deluxemenus.enabled", true)) return;
        if (!yaml.getBoolean("menus.deluxemenus.install-examples", true)) return;
        Path deluxeDir = plugin.getDataFolder().toPath().getParent();
        if (deluxeDir == null) return;
        Path menusDir = deluxeDir.resolve("DeluxeMenus").resolve("gui_menus");
        try {
            Files.createDirectories(menusDir);
            writeIfMissing(menusDir.resolve(yaml.getString("menus.deluxemenus.main-menu", "duels_menu") + ".yml"), deluxeMainMenuTemplate());
            writeIfMissing(menusDir.resolve(yaml.getString("menus.deluxemenus.kits-menu", "duels_kits") + ".yml"), deluxeKitsMenuTemplate());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not install DeluxeMenus duel examples: " + ex.getMessage());
        }
    }

    private void writeIfMissing(Path path, String content) throws IOException {
        if (Files.exists(path)) return;
        Files.writeString(path, content, StandardCharsets.UTF_8);
        plugin.getLogger().info("Installed DeluxeMenus starter: " + path.getFileName());
    }

    private String deluxeMainMenuTemplate() {
                return """
                        # Generated by 3SMPCore. Safe to edit; it will not be overwritten while this file exists.
                        # ItemsAdder font images belong in menu_title/backgrounds.
                        # If %img_duels_menu% does not parse on your setup, use the single unicode char instead, e.g. "\\uE001".
                        # Clickable slots need an item. Use a transparent ItemsAdder item named threesmp:gui_clickzone to avoid visible vanilla icons.
                        menu_title: ":offset_-8::duels_menu::duels_solo::duels_duo:"
                        open_command: []
                        size: 27
                        # 3SMPCore owns /duel and opens this with /dm open, so DeluxeMenus should not steal the command.
                        update_interval: 20
                        items:
                          solo_click:
                            material: "itemsadder-threesmp:gui_clickzone"
                            slot: 10
                            display_name: "&b&lSolo Duel"
                            lore:
                              - "&7Click to pick a kit."
                              - "&7Queued: &f%3smpcore_duel_solo_queue%"
                              - "&7Your queue: &f%3smpcore_duel_queue_summary%"
                            left_click_commands:
                              - "[player] duel deluxe solo"
                          party_click:
                            material: "itemsadder-threesmp:gui_clickzone"
                            slot: 16
                            display_name: "&#eda323&lParty Duel"
                            lore:
                              - "&7Queue your party for team duels."
                              - "&7Queued: &f%3smpcore_duel_party_queue%"
                            left_click_commands:
                              - "[player] duel deluxe duo"
                          leave_click:
                            material: BARRIER
                            slot: 26
                            display_name: "&cClose"
                            lore:
                              - "&7Current: &f%3smpcore_duel_queue_summary%"
                            left_click_commands:
                              - "[close]"
                        """;
    }

    private String deluxeKitsMenuTemplate() {
        StringBuilder out = new StringBuilder("""
                # Generated by 3SMPCore. Safe to edit; it will not be overwritten while this file exists.
                menu_title: ":offset_-8::duels_menu:"
                size: 54
                update_interval: 20
                items:
                  back:
                    material: ARROW
                    slot: 49
                    display_name: "&#eda323Back"
                    left_click_commands:
                      - "[player] duel deluxe menu"
                """);
        int slot = 10;
        for (DuelKit kit : kits.values()) {
            if (!kit.enabled()) continue;
            if (slot >= 44) break;
            out.append("  kit_").append(kit.id()).append(":\n");
            out.append("    material: DIAMOND_SWORD\n");
            out.append("    slot: ").append(slot).append("\n");
            out.append("    display_name: \"").append(kit.displayName().replace("\"", "\\\"")).append("\"\n");
            out.append("    lore:\n");
            out.append("      - \"&7Queued: &f%3smpcore_duel_kit_queue_").append(kit.id()).append("%\"\n");
            out.append("      - \"&7Click to queue with selected mode.\"\n");
            out.append("    left_click_commands:\n");
            out.append("      - \"[player] duel deluxe queue ").append(kit.id()).append("\"\n");
            slot++;
            if (slot % 9 == 8) slot += 2;
        }
        return out.toString();
    }

    public ItemStack guiIcon(String path, Material fallback) {
        var yaml = configs.get("menus/duels.yml");
        String itemsAdder = yaml.getString(path + ".itemsadder", yaml.getString(path + ".items-adder", ""));
        Material material = parseMaterialOrDefault(yaml.getString(path + ".material", fallback.name()), fallback);
        if (itemsAdder != null && !itemsAdder.isBlank() && Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = customStack.getMethod("getInstance", String.class).invoke(null, itemsAdder);
                if (stack != null) {
                    Object itemStack = stack.getClass().getMethod("getItemStack").invoke(stack);
                    if (itemStack instanceof ItemStack custom) return custom.clone();
                }
            } catch (Throwable ignored) {
            }
        }
        return new ItemStack(material);
    }

    public String guiText(String path, String fallback) {
        return replaceGuiSymbols(configs.get("menus/duels.yml").getString(path, fallback));
    }

    public List<String> guiTextList(String path) {
        return configs.get("menus/duels.yml").getStringList(path).stream().map(this::replaceGuiSymbols).toList();
    }

    private String replaceGuiSymbols(String input) {
        if (input == null || input.isBlank()) return input;
        var yaml = configs.get("menus/duels.yml");
        org.bukkit.configuration.ConfigurationSection symbols = yaml.getConfigurationSection("itemsadder-font-symbols");
        if (symbols != null) {
            for (String key : symbols.getKeys(false)) {
                input = input.replace(":" + key + ":", decodeUnicodeEscapes(symbols.getString(key, "")));
            }
        }
        return decodeUnicodeEscapes(input);
    }

    private String decodeUnicodeEscapes(String input) {
        if (input == null || input.isBlank() || !input.contains("\\u")) return input;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            if (i + 5 < input.length() && input.charAt(i) == '\\' && input.charAt(i + 1) == 'u') {
                String hex = input.substring(i + 2, i + 6);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
            out.append(input.charAt(i));
        }
        return out.toString();
    }

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
        else if (slot == 10) {
            pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.SOLO);
            openPluginKitMenu(player);
        }
        else if (slot == 16) {
            pendingDeluxeKitMode.put(player.getUniqueId(), DuelMode.PARTY);
            openPluginKitMenu(player);
        }
        else if (slot == 22) leaveDuelOrQueue(player);
    }

    public void handleSummaryClick(Player player, int slot) {
        if (slot == 22) openMainMenu(player);
        else if (slot == 11) openKitMenu(player);
    }

    public void handleKitMenuClick(Player player, int slot) {
        if (slot == 49) { openPluginMainMenu(player); return; }
        String kitId = kitSlots.get(slot);
        if (kitId == null) return;
        String target = pendingChallengeTargets.remove(player.getUniqueId());
        if (target != null) { challenge(player, target, kitId, ""); return; }
        if (isQueuedForKit(player.getUniqueId(), kitId)) leaveQueue(player, false);
        else {
            DuelMode mode = pendingDeluxeKitMode.remove(player.getUniqueId());
            boolean ranked = pendingRankedKitSelection.remove(player.getUniqueId());
            if (ranked) queueRankedSolo(player, kitId);
            else if (mode == DuelMode.PARTY) queueParty(player, kitId);
            else queueSolo(player, kitId);
        }
    }

    public void handleMenuClick(Player player, int slot) { handleMainMenuClick(player, slot); }
    public int soloQueueCount() { return soloQueues.values().stream().mapToInt(Deque::size).sum(); }
    public int partyQueueCount() { return partyQueues.values().stream().mapToInt(Deque::size).sum(); }
    public int ffaQueueCount() { return ffaQueues.values().stream().mapToInt(Deque::size).sum(); }
    public int queuedPlayersForKit(String kitId) {
        if (kitId == null || kitId.isBlank()) return 0;
        String id = kitId.toLowerCase(Locale.ROOT);
        return (int) queueByPlayer.values().stream().filter(unit -> unit.kitId().equalsIgnoreCase(id)).count();
    }
    public int rankedMmr(UUID uuid, String kitId) { return rankedService.mmr(uuid, resolveKitOrDefault(kitId)); }
    public String rankedRank(UUID uuid, String kitId) { return rankedService.rankName(rankedMmr(uuid, kitId)); }
    public String rankedRankDisplay(UUID uuid, String kitId) { return rankedService.rankDisplay(rankedMmr(uuid, kitId)); }
    public int rankedWins(UUID uuid, String kitId) { return rankedService.stats(uuid, resolveKitOrDefault(kitId)).rankedWins(); }
    public int rankedLosses(UUID uuid, String kitId) { return rankedService.stats(uuid, resolveKitOrDefault(kitId)).rankedLosses(); }
    public int duelKitWins(UUID uuid, String kitId) { return rankedService.stats(uuid, resolveKitOrDefault(kitId)).wins(); }
    public int duelKitLosses(UUID uuid, String kitId) { return rankedService.stats(uuid, resolveKitOrDefault(kitId)).losses(); }
    public int duelKitStreak(UUID uuid, String kitId) { return rankedService.stats(uuid, resolveKitOrDefault(kitId)).currentStreak(); }
    public int duelKitBestStreak(UUID uuid, String kitId) { return rankedService.stats(uuid, resolveKitOrDefault(kitId)).bestStreak(); }
    public Collection<DuelKit> kitsView() { return Collections.unmodifiableCollection(kits.values()); }
    public Collection<DuelMap> enabledMapsView() { return maps.values().stream().filter(DuelMap::enabled).toList(); }
    public DuelKit kit(String id) { return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT)); }
    public DuelMap map(String id) { return id == null ? null : maps.get(id.toLowerCase(Locale.ROOT)); }

    public boolean startConfiguredPartyDuel(Player requester, Set<UUID> red, Set<UUID> blue, String kitId, int rounds, String mapId) {
        return startConfiguredPartyDuel(requester, red, blue, kitId, rounds, mapId, false);
    }

    public boolean startConfiguredPartyDuel(Player requester, Set<UUID> red, Set<UUID> blue, String kitId, int rounds, String mapId, boolean ffa) {
        red = onlineOnly(red);
        blue = onlineOnly(blue);
        if (!ffa && (red == null || blue == null || red.isEmpty() || blue.isEmpty())) {
            Text.send(requester, "<red>Select at least one online player for each team.</red>");
            return false;
        }
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(red);
        all.addAll(blue);
        if (ffa && all.size() < 2) {
            Text.send(requester, "<red>Select at least two online party players for FFA.</red>");
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
        for (UUID uuid : all) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                Text.send(requester, "<red>All selected party duel members must be online.</red>");
                return false;
            }
            if (matchesByPlayer.containsKey(uuid) || preparingDuelPlayers.contains(uuid) || queueByPlayer.containsKey(uuid)) {
                Text.send(requester, "<red>One selected player is already queued or dueling.</red>");
                return false;
            }
        }
        DuelMap map = mapId == null || mapId.isBlank() ? null : maps.get(mapId.toLowerCase(Locale.ROOT));
        if (map == null || !map.enabled()) {
            Text.send(requester, "<red>Select a valid arena first.</red>");
            return false;
        }
        QueueUnit first = new QueueUnit(UUID.randomUUID(), ffa ? DuelMode.FFA : DuelMode.PARTY, kit.id(), false, new LinkedHashSet<>(ffa ? all : red), System.currentTimeMillis());
        QueueUnit second = new QueueUnit(UUID.randomUUID(), ffa ? DuelMode.FFA : DuelMode.PARTY, kit.id(), false, ffa ? Set.of() : new LinkedHashSet<>(blue), System.currentTimeMillis());
        startMatch(first, second, map, Math.max(1, rounds));
        return true;
    }

    private Set<UUID> onlineOnly(Set<UUID> members) {
        if (members == null || members.isEmpty()) return Set.of();
        Set<UUID> online = new LinkedHashSet<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) online.add(uuid);
        }
        return online;
    }
    public int kitCount() { return kits.size(); }
    public int mapCount() { return maps.size(); }
    public boolean isEnabled() { return enabled; }
    public boolean isDevEnabled(UUID uuid) { return devMode.contains(uuid); }
    public boolean isMapEditorEnabled(UUID uuid) { return mapEditorMode.contains(uuid); }
    public String selectedEditorMap(UUID uuid) { return selectedEditorMap.getOrDefault(uuid, "none"); }
    public void setDungeonService(DungeonService dungeonService) { this.dungeonService = dungeonService; }
    public void handleDevMenuClick(Player player, int slot) {
        switch (slot) {
            case 10 -> runTestDuel(player);
            case 12 -> openArenaSelector(player);
            case 14 -> openMapEditor(player);
            case 16 -> openLeaderboard(player);
            case 18 -> openKitEditorSelector(player);
            case 20 -> {
                if (dungeonService == null) {
                    Text.send(player, "<red>Dungeon editor is not ready yet.</red>");
                    return;
                }
                dungeonService.openDungeonEditor(player);
            }
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
        inv.setItem(37, createTagged(Material.CLOCK, "<gradient:#f4cd2a:#eda323:#d28d0d>Rounds: " + kit.rounds() + "</gradient>", "kit_editor_rounds"));
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
        int topSize = event.getView().getTopInventory().getSize();
        if (raw >= 0 && raw < topSize) {
            if (tryKitEditorAttributeTransfer(event, player, raw)) return;
            if (!isKitEditorEditableSlot(raw) || isInvalidKitEditorIncoming(event)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            moveKitEditorShiftClick(event, player);
        }
    }

    public void handleKitEditorDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesEditor = false;
        for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                touchesEditor = true;
                if (!isKitEditorEditableSlot(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (touchesEditor && !validKitEditorIncomingItem(event.getOldCursor())) event.setCancelled(true);
    }

    private boolean isKitEditorEditableSlot(int raw) {
        return (raw >= 0 && raw < 36)
                || (raw >= 38 && raw <= 41)
                || raw == 43
                || (raw >= 45 && raw <= 48)
                || raw == 50;
    }

    private boolean isInvalidKitEditorIncoming(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (!validKitEditorIncomingItem(cursor)) return true;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            if (button >= 0 && button < 9 && event.getWhoClicked() instanceof Player player) {
                return !validKitEditorIncomingItem(player.getInventory().getItem(button));
            }
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND && event.getWhoClicked() instanceof Player player) {
            return !validKitEditorIncomingItem(player.getInventory().getItemInOffHand());
        }
        return false;
    }

    private boolean validKitEditorIncomingItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        if (!validKitItem(item)) return false;
        return configs.get("duels/duels.yml").getBoolean("duels.kit-editor.allow-attribute-swapping", true) || !hasAttributeModifiers(item);
    }

    private boolean tryKitEditorAttributeTransfer(InventoryClickEvent event, Player player, int raw) {
        if (!configs.get("duels/duels.yml").getBoolean("duels.kit-editor.allow-attribute-swapping", true)) return false;
        if (event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return false;
        if (!isKitEditorEditableSlot(raw)) return false;
        ItemStack source = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (isEmpty(source) || isEmpty(target)) return false;
        if (!validKitItem(source) || !validKitItem(target)) return false;
        if (!hasTransferableCombatMeta(source)) return false;
        ItemStack merged = copyCombatMeta(source, target);
        if (merged == null) return false;
        event.setCancelled(true);
        event.setCurrentItem(merged);
        Text.actionBar(player, "<gradient:#60a5fa:#c084fc>Copied weapon attributes and enchants.</gradient>");
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        return true;
    }

    private boolean hasTransferableCombatMeta(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && (meta.hasAttributeModifiers() || !meta.getEnchants().isEmpty() || meta.isUnbreakable());
    }

    private ItemStack copyCombatMeta(ItemStack source, ItemStack target) {
        if (source == null || target == null || !source.hasItemMeta() || !target.hasItemMeta()) return null;
        ItemMeta sourceMeta = source.getItemMeta();
        ItemStack merged = target.clone();
        ItemMeta targetMeta = merged.getItemMeta();
        if (sourceMeta == null || targetMeta == null) return null;
        for (org.bukkit.enchantments.Enchantment enchantment : new HashSet<>(targetMeta.getEnchants().keySet())) {
            targetMeta.removeEnchant(enchantment);
        }
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : sourceMeta.getEnchants().entrySet()) {
            targetMeta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        var existingModifiers = targetMeta.getAttributeModifiers();
        if (existingModifiers != null) {
            for (Attribute attribute : new HashSet<>(existingModifiers.keySet())) targetMeta.removeAttributeModifier(attribute);
        }
        var modifiers = sourceMeta.getAttributeModifiers();
        if (modifiers != null) {
            for (Map.Entry<Attribute, org.bukkit.attribute.AttributeModifier> entry : modifiers.entries()) {
                targetMeta.addAttributeModifier(entry.getKey(), entry.getValue());
            }
        }
        targetMeta.setUnbreakable(sourceMeta.isUnbreakable());
        if (!sourceMeta.getItemFlags().isEmpty()) {
            targetMeta.addItemFlags(sourceMeta.getItemFlags().toArray(new ItemFlag[0]));
        }
        merged.setItemMeta(targetMeta);
        return merged;
    }

    private void moveKitEditorShiftClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (!validKitEditorIncomingItem(current)) {
            Text.send(player, "<red>That item cannot be saved into a duel kit.</red>");
            return;
        }
        org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
        int targetSlot = preferredKitEditorSlot(current, top);
        if (targetSlot < 0) {
            Text.send(player, "<red>No open kit slot for that item.</red>");
            return;
        }
        top.setItem(targetSlot, current.clone());
        event.setCurrentItem(null);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private int preferredKitEditorSlot(ItemStack item, org.bukkit.inventory.Inventory inv) {
        if (item == null) return -1;
        int armorSlot = armorEditorSlot(item.getType());
        if (armorSlot >= 0 && isEmpty(inv.getItem(armorSlot))) return armorSlot;
        if (isOffhandItem(item.getType()) && isEmpty(inv.getItem(50))) return 50;
        for (int i = 0; i < 36; i++) {
            if (isEmpty(inv.getItem(i))) return i;
        }
        for (int i = 38; i <= 41; i++) {
            if (item.getType() == Material.SPLASH_POTION && isEmpty(inv.getItem(i))) return i;
        }
        return -1;
    }

    private int armorEditorSlot(Material material) {
        String name = material == null ? "" : material.name();
        if (name.endsWith("_BOOTS")) return 45;
        if (name.endsWith("_LEGGINGS")) return 46;
        if (name.endsWith("_CHESTPLATE") || material == Material.ELYTRA) return 47;
        if (name.endsWith("_HELMET") || material == Material.TURTLE_HELMET || material == Material.CARVED_PUMPKIN) return 48;
        return -1;
    }

    private boolean isOffhandItem(Material material) {
        return material == Material.SHIELD || material == Material.TOTEM_OF_UNDYING;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    public void openLoadoutEditor(Player player) {
        if (matchesByPlayer.containsKey(player.getUniqueId()) || ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId())) {
            Text.send(player, "<yellow>Edit duel kit layouts at spawn before queueing.</yellow>");
            return;
        }
        if (!isSpawnWorld(player)) {
            Text.send(player, "<yellow>Duel kit layouts can only be edited at spawn.</yellow>");
            return;
        }
        openLoadoutKitSelector(player);
    }

    private void openLoadoutKitSelector(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_LOADOUT, "loadout-kits"), 54, "Duel Kit Layouts");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, createTagged(Material.GRAY_STAINED_GLASS_PANE, " ", LOADOUT_PANE_ID, List.of()));
        int[] slots = {10,12,14,16,19,21,23,25,28,30,32,34};
        int index = 0;
        for (DuelKit kit : kits.values()) {
            if (!kit.enabled() || index >= slots.length) continue;
            inv.setItem(slots[index++], createTagged(kit.icon(), kit.displayName(), LOADOUT_KIT_PREFIX + kit.id(), List.of(
                    "<gray>Edit your personal layout for this kit.</gray>",
                    "<gray>This saves at spawn and applies when a duel starts.</gray>"
            )));
        }
        inv.setItem(49, createTagged(Material.BARRIER, "<gray>Close</gray>", LOADOUT_CLOSE_ID, List.of("<gray>Return to spawn.</gray>")));
        player.openInventory(inv);
    }

    private void openLoadoutEditor(Player player, String kitId) {
        DuelKit kit = kit(kitId);
        if (kit == null || !kit.enabled()) {
            Text.send(player, "<red>That duel kit is not available.</red>");
            openLoadoutKitSelector(player);
            return;
        }
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_LOADOUT, "loadout:" + kit.id()), 54, "Layout: " + plainKitName(kit));
        for (int i = 36; i < inv.getSize(); i++) inv.setItem(i, createTagged(Material.GRAY_STAINED_GLASS_PANE, " ", LOADOUT_PANE_ID, List.of()));

        DuelLoadout layout = loadoutForEditor(player, kit);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, cleanLoadoutItem(layout.contents()[i]));
        }

        inv.setItem(36, createTagged(Material.IRON_HELMET, "<gradient:#D6E8F7:#60a5fa>Helmet</gradient>", LOADOUT_PANE_ID, List.of("<gray>Place helmets in the slot below.</gray>")));
        inv.setItem(37, createTagged(Material.IRON_CHESTPLATE, "<gradient:#D6E8F7:#60a5fa>Chestplate</gradient>", LOADOUT_PANE_ID, List.of("<gray>Place chestplates in the slot below.</gray>")));
        inv.setItem(38, createTagged(Material.IRON_LEGGINGS, "<gradient:#D6E8F7:#60a5fa>Leggings</gradient>", LOADOUT_PANE_ID, List.of("<gray>Place leggings in the slot below.</gray>")));
        inv.setItem(39, createTagged(Material.IRON_BOOTS, "<gradient:#D6E8F7:#60a5fa>Boots</gradient>", LOADOUT_PANE_ID, List.of("<gray>Place boots in the slot below.</gray>")));
        inv.setItem(41, createTagged(Material.SHIELD, "<gradient:#D6E8F7:#60a5fa>Offhand</gradient>", LOADOUT_PANE_ID, List.of("<gray>Place your offhand item below.</gray>")));

        inv.setItem(45, cleanLoadoutItem(layout.helmet()));
        inv.setItem(46, cleanLoadoutItem(layout.chestplate()));
        inv.setItem(47, cleanLoadoutItem(layout.leggings()));
        inv.setItem(48, cleanLoadoutItem(layout.boots()));
        inv.setItem(50, cleanLoadoutItem(layout.offhand()));
        inv.setItem(52, createTagged(Material.LIME_DYE, "<green>Save and Close</green>", LOADOUT_SAVE_ID, List.of("<gray>Your layout also saves when this menu closes.</gray>")));
        inv.setItem(53, createTagged(Material.BARRIER, "<gray>Close</gray>", LOADOUT_CLOSE_ID, List.of("<gray>Close and save your current layout.</gray>")));
        player.openInventory(inv);
    }

    public void handleLoadoutClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String context = event.getView().getTopInventory().getHolder() instanceof CoreMenuHolder holder ? holder.context() : "";
        if ("loadout-kits".equalsIgnoreCase(context)) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
            String id = itemId(event.getCurrentItem());
            if (LOADOUT_CLOSE_ID.equals(id)) {
                player.closeInventory();
                return;
            }
            if (id != null && id.startsWith(LOADOUT_KIT_PREFIX)) {
                openLoadoutEditor(player, id.substring(LOADOUT_KIT_PREFIX.length()));
            }
            return;
        }
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (raw < 0 || raw >= topSize) {
            event.setCancelled(true);
            return;
        }
        if (raw == 52 || raw == 53) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        if (!isLoadoutEditableSlot(raw)) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR
                || event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        if (isLoadoutUtilityItem(event.getCursor()) || isLoadoutUtilityItem(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!isEmpty(cursor) && !validKitItem(cursor)) {
            event.setCancelled(true);
            return;
        }
        if (isArmorLoadoutSlot(raw) && !isEmpty(cursor) && !armorFitsLoadoutSlot(cursor, raw)) {
            event.setCancelled(true);
            Text.send(player, "<red>That armor piece does not fit this slot.</red>");
        }
    }

    public void handleLoadoutDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CoreMenuHolder holder && holder.context().equalsIgnoreCase("loadout-kits")) {
            event.setCancelled(true);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        ItemStack cursor = event.getOldCursor();
        if (!isEmpty(cursor) && (!validKitItem(cursor) || isLoadoutUtilityItem(cursor))) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < 0 || raw >= topSize || !isLoadoutEditableSlot(raw)) {
                event.setCancelled(true);
                return;
            }
            if (isArmorLoadoutSlot(raw) && !isEmpty(cursor) && !armorFitsLoadoutSlot(cursor, raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onLoadoutClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof CoreMenuHolder holder)) return;
        if (holder.type() != CoreMenuType.DUEL_LOADOUT) return;
        if (holder.context().equalsIgnoreCase("loadout-kits")) return;
        if (holder.context().startsWith("loadout:")) {
            saveLoadoutProfile(player, holder.context().substring("loadout:".length()), event.getInventory());
            return;
        }
        saveLoadout(player, event.getInventory());
    }

    private void saveLoadoutProfile(Player player, String kitId, org.bukkit.inventory.Inventory inv) {
        List<ItemStack> overflow = new ArrayList<>();
        ItemStack cursor = cleanLoadoutItem(player.getItemOnCursor());
        if (cursor != null) {
            int open = firstEmptyLoadoutStorage(inv);
            if (open >= 0) inv.setItem(open, cursor);
            else overflow.add(cursor);
            player.setItemOnCursor(null);
        }
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < 36; i++) storage[i] = cleanLoadoutItem(inv.getItem(i));
        ItemStack helmet = validatedLoadoutArmor(inv.getItem(45), 45, overflow);
        ItemStack chest = validatedLoadoutArmor(inv.getItem(46), 46, overflow);
        ItemStack legs = validatedLoadoutArmor(inv.getItem(47), 47, overflow);
        ItemStack boots = validatedLoadoutArmor(inv.getItem(48), 48, overflow);
        ItemStack offhand = cleanLoadoutItem(inv.getItem(50));
        repository.saveInventoryProfile(player.getUniqueId(), loadoutProfileKey(kitId), storage, new ItemStack[]{boots, legs, chest, helmet}, offhand);
        for (ItemStack item : overflow) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                player.setItemOnCursor(leftovers.values().iterator().next());
                break;
            }
        }
        Text.actionBar(player, "<gradient:#D6E8F7:#60a5fa>Saved duel kit layout.</gradient>");
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private void saveLoadout(Player player, org.bukkit.inventory.Inventory inv) {
        List<ItemStack> overflow = new ArrayList<>();
        ItemStack cursor = cleanLoadoutItem(player.getItemOnCursor());
        if (cursor != null) {
            int open = firstEmptyLoadoutStorage(inv);
            if (open >= 0) inv.setItem(open, cursor);
            else overflow.add(cursor);
            player.setItemOnCursor(null);
        }

        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < 36; i++) storage[i] = cleanLoadoutItem(inv.getItem(i));
        ItemStack helmet = validatedLoadoutArmor(inv.getItem(45), 45, overflow);
        ItemStack chest = validatedLoadoutArmor(inv.getItem(46), 46, overflow);
        ItemStack legs = validatedLoadoutArmor(inv.getItem(47), 47, overflow);
        ItemStack boots = validatedLoadoutArmor(inv.getItem(48), 48, overflow);
        ItemStack offhand = cleanLoadoutItem(inv.getItem(50));

        loadoutButtonDisplacements.remove(player.getUniqueId());
        for (int i = 0; i < storage.length; i++) player.getInventory().setItem(i, storage[i]);
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
        player.getInventory().setItemInOffHand(offhand);
        for (ItemStack item : overflow) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                player.setItemOnCursor(leftovers.values().iterator().next());
                break;
            }
        }
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private ItemStack validatedLoadoutArmor(ItemStack item, int slot, List<ItemStack> overflow) {
        ItemStack clean = cleanLoadoutItem(item);
        if (clean == null) return null;
        if (armorFitsLoadoutSlot(clean, slot)) return clean;
        overflow.add(clean);
        return null;
    }

    private int firstEmptyLoadoutStorage(org.bukkit.inventory.Inventory inv) {
        for (int i = 0; i < 36; i++) {
            if (isEmpty(inv.getItem(i))) return i;
        }
        return -1;
    }

    private ItemStack cleanLoadoutItem(ItemStack item) {
        if (isEmpty(item) || isLoadoutUtilityItem(item)) return null;
        return item.clone();
    }

    private boolean isLoadoutUtilityItem(ItemStack item) {
        String id = itemId(item);
        return LOADOUT_ITEM_ID.equals(id) || LOADOUT_PANE_ID.equals(id) || LOADOUT_SAVE_ID.equals(id) || LOADOUT_CLOSE_ID.equals(id) || QUEUE_ITEM_ID.equals(id) || editorToolId(item) != null;
    }

    private boolean isLoadoutEditableSlot(int raw) {
        return (raw >= 0 && raw < 36) || isArmorLoadoutSlot(raw) || raw == 50;
    }

    private boolean isArmorLoadoutSlot(int raw) {
        return raw >= 45 && raw <= 48;
    }

    private boolean armorFitsLoadoutSlot(ItemStack item, int raw) {
        if (isEmpty(item)) return true;
        Material material = item.getType();
        String name = material.name();
        return switch (raw) {
            case 45 -> name.endsWith("_HELMET") || material == Material.TURTLE_HELMET || material == Material.CARVED_PUMPKIN;
            case 46 -> name.endsWith("_CHESTPLATE") || material == Material.ELYTRA;
            case 47 -> name.endsWith("_LEGGINGS");
            case 48 -> name.endsWith("_BOOTS");
            default -> true;
        };
    }

    private DuelLoadout loadoutForEditor(Player player, DuelKit kit) {
        DuelLoadout saved = savedLoadout(player.getUniqueId(), kit.id());
        return saved == null ? defaultLoadout(kit) : saved;
    }

    private DuelLoadout savedLoadout(UUID uuid, String kitId) {
        PlayerDataRepository.InventoryProfile profile = repository.loadInventoryProfile(uuid, loadoutProfileKey(kitId));
        if (!profileHasAnyItem(profile)) return null;
        ItemStack[] contents = normalizedContents(profile.contents());
        ItemStack[] armor = normalizedArmor(profile.armor());
        return new DuelLoadout(contents, cleanLoadoutItem(armor[3]), cleanLoadoutItem(armor[2]), cleanLoadoutItem(armor[1]), cleanLoadoutItem(armor[0]), cleanLoadoutItem(profile.offhand()));
    }

    private DuelLoadout defaultLoadout(DuelKit kit) {
        ItemStack[] contents = new ItemStack[36];
        for (String encoded : kit.contents()) {
            SlottedItem slotted = parseSlottedItem(encoded);
            ItemStack item = cleanLoadoutItem(slotted.item());
            if (item == null) continue;
            if (slotted.slot() >= 0 && slotted.slot() < 36) contents[slotted.slot()] = item;
            else {
                int open = firstEmpty(contents);
                if (open >= 0) contents[open] = item;
            }
        }
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < kit.armor().size() && i < 4; i++) armor[i] = cleanLoadoutItem(materialItem(kit.armor().get(i)));
        ItemStack offhand = kit.offhand().isEmpty() ? null : cleanLoadoutItem(materialItem(kit.offhand().get(0)));
        return new DuelLoadout(contents, armor[3], armor[2], armor[1], armor[0], offhand);
    }

    private void applySavedLoadout(Player player, DuelKit kit) {
        DuelLoadout saved = savedLoadout(player.getUniqueId(), kit.id());
        if (saved == null) return;
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, cleanLoadoutItem(saved.contents()[i]));
        player.getInventory().setHelmet(cleanLoadoutItem(saved.helmet()));
        player.getInventory().setChestplate(cleanLoadoutItem(saved.chestplate()));
        player.getInventory().setLeggings(cleanLoadoutItem(saved.leggings()));
        player.getInventory().setBoots(cleanLoadoutItem(saved.boots()));
        player.getInventory().setItemInOffHand(cleanLoadoutItem(saved.offhand()));
    }

    private boolean profileHasAnyItem(PlayerDataRepository.InventoryProfile profile) {
        if (profile == null) return false;
        if (profile.contents() != null) for (ItemStack item : profile.contents()) if (!isEmpty(item)) return true;
        if (profile.armor() != null) for (ItemStack item : profile.armor()) if (!isEmpty(item)) return true;
        return !isEmpty(profile.offhand());
    }

    private ItemStack[] normalizedContents(ItemStack[] source) {
        ItemStack[] contents = new ItemStack[36];
        if (source == null) return contents;
        for (int i = 0; i < Math.min(36, source.length); i++) contents[i] = cleanLoadoutItem(source[i]);
        return contents;
    }

    private ItemStack[] normalizedArmor(ItemStack[] source) {
        ItemStack[] armor = new ItemStack[4];
        if (source == null) return armor;
        for (int i = 0; i < Math.min(4, source.length); i++) armor[i] = cleanLoadoutItem(source[i]);
        return armor;
    }

    private int firstEmpty(ItemStack[] items) {
        for (int i = 0; i < items.length; i++) if (isEmpty(items[i])) return i;
        return -1;
    }

    private String loadoutProfileKey(String kitId) {
        return "duel-layout:" + (kitId == null ? "default" : kitId.toLowerCase(Locale.ROOT));
    }

    private String plainKitName(DuelKit kit) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Text.mm(kit.displayName()));
    }

    private void giveLoadoutEditorItem(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!configs.get("core/config.yml").getBoolean("spawn.hotbar-items.enabled", false)) {
            clearTaggedItem(player, LOADOUT_ITEM_ID);
            return;
        }
        if (!isSpawnWorld(player) || net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId()) || matchesByPlayer.containsKey(player.getUniqueId())) {
            clearTaggedItem(player, LOADOUT_ITEM_ID);
            return;
        }
        if (!configs.get("duels/duels.yml").getBoolean("duels.layout-editor.enabled", true)) return;
        clearTaggedItem(player, LOADOUT_ITEM_ID);
        int slot = Math.max(0, Math.min(35, configs.get("duels/duels.yml").getInt("duels.layout-editor.slot", 8)));
        ItemStack item = createTagged(Material.CHEST, configs.get("duels/duels.yml").getString("duels.layout-editor.name", "<gradient:#D6E8F7:#60a5fa>Kit Layout</gradient>"), LOADOUT_ITEM_ID, List.of(
                "<gray>Edit personal duel kit layouts at spawn.</gray>",
                "<gray>Your saved layout applies when the duel starts.</gray>"
        ));
        player.getInventory().setItem(slot, item);
        player.updateInventory();
    }

    private void removeLoadoutEditorItem(Player player) {
        if (player == null || !player.isOnline()) return;
        clearTaggedItem(player, LOADOUT_ITEM_ID);
        restoreLoadoutDisplacement(player);
        player.updateInventory();
    }

    private void restoreLoadoutDisplacement(Player player) {
        LoadoutButtonDisplacement displacement = loadoutButtonDisplacements.remove(player.getUniqueId());
        if (displacement == null) return;
        ItemStack current = player.getInventory().getItem(displacement.slot());
        if (LOADOUT_ITEM_ID.equals(itemId(current)) || isEmpty(current)) {
            player.getInventory().setItem(displacement.slot(), displacement.item().clone());
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(displacement.item().clone());
        if (!leftovers.isEmpty()) player.setItemOnCursor(leftovers.values().iterator().next());
    }

    private void closeLoadoutEditors(Collection<UUID> members) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CoreMenuHolder holder && holder.type() == CoreMenuType.DUEL_LOADOUT) {
                player.closeInventory();
            }
        }
    }

    private void clearTaggedItem(Player player, String id) {
        if (player == null) return;
        for (int i = 0; i < 36; i++) {
            if (id.equals(itemId(player.getInventory().getItem(i)))) player.getInventory().setItem(i, null);
        }
        if (id.equals(itemId(player.getInventory().getItemInOffHand()))) player.getInventory().setItemInOffHand(null);
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            if (id.equals(itemId(armor[i]))) {
                armor[i] = null;
                changed = true;
            }
        }
        if (changed) player.getInventory().setArmorContents(armor);
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
        ItemStack item = slotted.item().clone();
        if (slotted.slot() >= 0 && slotted.slot() < 36) inv.setItem(slotted.slot(), item);
        else inv.addItem(item);
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
            return object instanceof ItemStack stack ? stack.clone() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean validKitItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && itemId(item) == null && editorToolId(item) == null;
    }

    private record SlottedItem(int slot, ItemStack item) {}
    public void queueSolo(Player player, String kitId) {
        queueSolo(player, kitId, false);
    }

    public void queueRankedSolo(Player player, String kitId) {
        queueSolo(player, kitId, true);
    }

    private void queueSolo(Player player, String kitId, boolean ranked) {
        if (!enabled) { Text.send(player, "<red>Duels are currently disabled.</red>"); return; }
        if (ranked && !rankedService.enabled()) { Text.send(player, duelMessage("ranked-disabled", "<red>Ranked duels are currently disabled.</red>")); return; }
        if (ranked && !rankedValidationService.canJoinRanked(player)) return;
        if (!canUseInWorld(player)) { Text.send(player, "<red>Duels are not available in this world.</red>"); return; }
        if (matchesByPlayer.containsKey(player.getUniqueId()) || preparingDuelPlayers.contains(player.getUniqueId())) { Text.send(player, "<red>You are already in a duel.</red>"); return; }
        if (queueByPlayer.containsKey(player.getUniqueId())) leaveQueue(player, true);
        if (kitId == null) { Text.send(player, "<red>No enabled duel kit is configured.</red>"); return; }
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null || !kit.enabled()) { Text.send(player, "<red>That kit is unavailable.</red>"); return; }
        QueueUnit unit = new QueueUnit(UUID.randomUUID(), DuelMode.SOLO, kitId, ranked, Set.of(player.getUniqueId()), System.currentTimeMillis());
        queueUnits.put(unit.unitId(), unit);
        queueByPlayer.put(player.getUniqueId(), unit);
        soloQueues.computeIfAbsent(kitId, ignored -> new ArrayDeque<>()).add(unit.unitId());
        giveQueueItem(player);
        messageService.queue(player, ranked ? "Ranked 1v1" : "1v1", kit.displayName());
        if (ranked) notifyRankedSearch(unit, rankedSearchRange(unit), false);
        tryMatchAll();
    }

    public void queueParty(Player player) {
        queueParty(player, selectedKitForParty());
    }

    public void queueParty(Player player, String kitId) {
        if (!enabled) { Text.send(player, "<red>Duels are currently disabled.</red>"); return; }
        if (!canUseInWorld(player)) { Text.send(player, "<red>Duels are not available in this world.</red>"); return; }
        if (matchesByPlayer.containsKey(player.getUniqueId()) || preparingDuelPlayers.contains(player.getUniqueId())) { Text.send(player, "<red>You are already in a duel.</red>"); return; }
        UUID leader = partyService.partyLeader(player.getUniqueId());
        if (leader != null && !leader.equals(player.getUniqueId())) {
            Player onlineLeader = Bukkit.getPlayer(leader);
            if (onlineLeader != null && onlineLeader.isOnline()) { Text.send(player, "<red>Only the party leader can queue party duels.</red>"); return; }
        }
        Set<UUID> members = leader == null ? Set.of(player.getUniqueId()) : partyService.onlinePartyMembers(player.getUniqueId());
        if (members == null || members.isEmpty()) members = Set.of(player.getUniqueId());
        int minMembers = Math.max(1, configs.get("duels/duels.yml").getInt("duels.matchmaking.party-min-size", 2));
        if (members.size() < minMembers) { Text.send(player, "<red>Your party needs at least " + minMembers + " online players for party duels.</red>"); return; }
        int maxMembers = Math.max(minMembers, configs.get("duels/duels.yml").getInt("duels.matchmaking.party-max-size", 3));
        if (members.size() > maxMembers) { Text.send(player, "<red>Your party can have at most " + maxMembers + " online players for party duels.</red>"); return; }
        if (!canQueueMembers(player, members)) return;
        if (members.stream().anyMatch(queueByPlayer::containsKey)) { Text.send(player, "<yellow>Your party is already queued.</yellow>"); return; }
        if (kitId == null) { Text.send(player, "<red>No enabled kits are configured.</red>"); return; }
        DuelKit kit = kits.get(kitId.toLowerCase(Locale.ROOT));
        if (kit == null || !kit.enabled()) { Text.send(player, "<red>That kit is unavailable.</red>"); return; }
        QueueUnit unit = new QueueUnit(UUID.randomUUID(), DuelMode.PARTY, kitId, false, new LinkedHashSet<>(members), System.currentTimeMillis());
        queueUnits.put(unit.unitId(), unit);
        for (UUID member : members) queueByPlayer.put(member, unit);
        partyQueues.computeIfAbsent(kitId, ignored -> new ArrayDeque<>()).add(unit.unitId());
        messageService.queue(player, "2v2", kit.displayName());
        tryMatchAll();
    }

    public void queueFfa(Player player, String kitId) {
        Text.send(player, "<yellow>FFA is party-only now.</yellow> <gray>Use <white>/party duel</white> and toggle FFA in the middle.</gray>");
        partyService.openPartyDuelMenu(player);
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
    }

    public void leaveDuelOrQueue(Player player) {
        if (spectatorSnapshots.containsKey(player.getUniqueId())) { stopSpectating(player); return; }
        DuelMatch match = matchesByPlayer.get(player.getUniqueId());
        if (match != null) {
            Set<UUID> winners = forfeitWinners(match, player.getUniqueId());
            announceLeave(player, false);
            endMatch(match, winners, "forfeit");
            return;
        }
        if (preparingDuelPlayers.contains(player.getUniqueId())) {
            Text.send(player, "<yellow>Your duel arena is preparing. Disconnecting now may count as a ranked dodge.</yellow>");
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
        String permission = configs.get("duels/duels.yml").getString("duels.spectators.permission", "3smpcore.duel.spectate");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission) && !player.hasPermission("3smpcore.duel.admin")) {
            Text.send(player, duelMessage("spectator-no-permission", "<red>You do not have permission to spectate duels.</red>"));
            return;
        }
        if (match.ranked() && !configs.get("duels/duels.yml").getBoolean("duels.spectators.allow-ranked", true)) {
            Text.send(player, duelMessage("spectator-ranked-disabled", "<red>Spectators are disabled for ranked matches.</red>"));
            return;
        }
        spectatorSnapshots.putIfAbsent(player.getUniqueId(), SpectatorSnapshot.capture(player, match.id()));
        DuelMap activeMap = activeMapsByMatch.getOrDefault(match.id(), maps.get(match.mapId()));
        Location spec = activeMap == null ? null : activeMap.spectator();
        if (spec == null) spec = target.getLocation();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        giveSpectatorHotbar(player, match);
        player.teleport(spec);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvisible(true);
        player.setCollidable(false);
        Text.send(player, "<gradient:#60a5fa:#c084fc>Spectating</gradient> <white>" + target.getName() + "</white><gray>. Use /leave to exit.</gray>");
    }

    private void stopSpectating(Player player) {
        SpectatorSnapshot snapshot = spectatorSnapshots.remove(player.getUniqueId());
        if (snapshot == null) return;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (IllegalArgumentException ignored) {}
        }
        snapshot.restore(player);
        Text.send(player, "<gray>Stopped spectating.</gray>");
    }

    private boolean isSpectating(Player player) {
        return player != null && spectatorSnapshots.containsKey(player.getUniqueId());
    }

    private void giveSpectatorHotbar(Player player, DuelMatch match) {
        player.getInventory().setItem(0, createTagged(Material.RED_BANNER, "<#f87171>Teleport Red</#f87171>", SPECTATOR_TP_ONE_ID, List.of("<gray>Jump to player/team one.</gray>")));
        player.getInventory().setItem(1, createTagged(Material.BLUE_BANNER, "<#60a5fa>Teleport Blue</#60a5fa>", SPECTATOR_TP_TWO_ID, List.of("<gray>Jump to player/team two.</gray>")));
        player.getInventory().setItem(4, createTagged(Material.ENDER_EYE, "<gradient:#60a5fa:#c084fc>Match Info</gradient>", SPECTATOR_INFO_ID, List.of("<gray>" + match.kitId() + " on " + match.mapId() + "</gray>")));
        player.getInventory().setItem(7, createTagged(Material.LIME_DYE, "<green>Spectator Visibility</green>", SPECTATOR_VISIBILITY_ID, List.of("<gray>Toggle whether match players can see you.</gray>")));
        player.getInventory().setItem(8, createTagged(Material.BARRIER, "<red>Leave Spectator</red>", SPECTATOR_LEAVE_ID, List.of("<gray>Return to where you were.</gray>")));
        player.updateInventory();
    }

    private void handleSpectatorInteract(Player player, ItemStack item, Material clickedMaterial) {
        String id = itemId(item);
        if (id == null) return;
        SpectatorSnapshot session = spectatorSnapshots.get(player.getUniqueId());
        DuelMatch match = session == null ? null : activeMatch(session.matchId());
        switch (id) {
            case SPECTATOR_TP_ONE_ID -> teleportSpectatorTo(player, match == null ? Set.of() : match.teamOne());
            case SPECTATOR_TP_TWO_ID -> teleportSpectatorTo(player, match == null ? Set.of() : match.teamTwo());
            case SPECTATOR_VISIBILITY_ID -> toggleSpectatorVisibility(player);
            case SPECTATOR_INFO_ID -> sendSpectatorInfo(player, match);
            case SPECTATOR_LEAVE_ID -> stopSpectating(player);
            default -> { }
        }
    }

    private void teleportSpectatorTo(Player spectator, Set<UUID> targets) {
        Player target = firstOnline(targets);
        if (target == null) {
            Text.send(spectator, "<red>That side has no online player right now.</red>");
            return;
        }
        spectator.teleport(target.getLocation());
        Text.actionBar(spectator, "<gray>Spectating</gray> <white>" + target.getName() + "</white>");
    }

    private void toggleSpectatorVisibility(Player spectator) {
        SpectatorSnapshot session = spectatorSnapshots.get(spectator.getUniqueId());
        if (session == null) return;
        session.visible(!session.visible());
        spectator.setInvisible(!session.visible());
        Text.actionBar(spectator, session.visible() ? "<green>Visible to duel players.</green>" : "<gray>Hidden from duel players.</gray>");
    }

    private void sendSpectatorInfo(Player player, DuelMatch match) {
        if (match == null) {
            Text.send(player, "<red>This match is no longer active.</red>");
            return;
        }
        Text.send(player, "<gradient:#60a5fa:#c084fc>Match</gradient> <gray>Kit:</gray> <white>" + match.kitId() + "</white> <gray>Arena:</gray> <white>" + match.mapId() + "</white> <gray>Time:</gray> <yellow>" + formatDuration(System.currentTimeMillis() - match.startedAt()) + "</yellow>");
        Text.send(player, "<#f87171>Red:</#f87171> <white>" + names(match.teamOne()) + "</white> <dark_gray>|</dark_gray> <#60a5fa>Blue:</#60a5fa> <white>" + names(match.teamTwo()) + "</white>");
    }

    private void updateSpectatorHud() {
        for (Map.Entry<UUID, SpectatorSnapshot> entry : new ArrayList<>(spectatorSnapshots.entrySet())) {
            Player spectator = Bukkit.getPlayer(entry.getKey());
            if (spectator == null || !spectator.isOnline()) continue;
            DuelMatch match = activeMatch(entry.getValue().matchId());
            if (match == null) {
                stopSpectating(spectator);
                continue;
            }
            Player one = firstOnline(match.teamOne());
            Player two = firstOnline(match.teamTwo());
            String oneHealth = one == null ? "--" : formatOneDecimal(one.getHealth());
            String twoHealth = two == null ? "--" : formatOneDecimal(two.getHealth());
            Text.actionBar(spectator, "<gradient:#60a5fa:#c084fc>" + match.kitId() + "</gradient> <dark_gray>|</dark_gray> <gray>" + match.mapId() + "</gray> <dark_gray>|</dark_gray> <#f87171>" + (one == null ? "Red" : one.getName()) + " " + oneHealth + "</#f87171> <gray>vs</gray> <#60a5fa>" + (two == null ? "Blue" : two.getName()) + " " + twoHealth + "</#60a5fa> <dark_gray>|</dark_gray> <yellow>" + formatDuration(System.currentTimeMillis() - match.startedAt()) + "</yellow>");
        }
    }

    private DuelMatch activeMatch(UUID matchId) {
        if (matchId == null) return null;
        for (DuelMatch match : new HashSet<>(matchesByPlayer.values())) {
            if (match.id().equals(matchId)) return match;
        }
        return null;
    }

    public void challenge(Player challenger, String targetName, String kitIdInput, String mapIdInput) {
        if (!enabled) { Text.send(challenger, "<red>Duels are currently disabled.</red>"); return; }
        if (!canUseInWorld(challenger)) { Text.send(challenger, "<red>Duels are not available in this world.</red>"); return; }
        if (targetName == null || targetName.isBlank()) { Text.send(challenger, "<red>Usage: /duel <player> [kit] [arena]</red>"); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || target.equals(challenger)) { Text.send(challenger, "<red>Player not found.</red>"); return; }
        if (matchesByPlayer.containsKey(challenger.getUniqueId()) || matchesByPlayer.containsKey(target.getUniqueId()) || preparingDuelPlayers.contains(challenger.getUniqueId()) || preparingDuelPlayers.contains(target.getUniqueId())) { Text.send(challenger, "<red>One of you is already in a duel.</red>"); return; }
        String kitId = resolveKit(kitIdInput);
        String mapId = resolveMap(mapIdInput);
        if (kitId == null || mapId == null) { Text.send(challenger, "<red>No valid kit or arena configured.</red>"); return; }
        Set<UUID> challengers = challengeTeam(challenger);
        Set<UUID> targets = challengeTeam(target);
        if (challengers.size() != targets.size()) { Text.send(challenger, "<red>Both sides need the same party size.</red>"); return; }
        if (!canQueueMembers(challenger, challengers) || !canQueueMembers(challenger, targets)) return;
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
        startMatch(new QueueUnit(UUID.randomUUID(), challenge.mode(), challenge.kitId(), false, challenge.challengers(), System.currentTimeMillis()), new QueueUnit(UUID.randomUUID(), challenge.mode(), challenge.kitId(), false, challenge.targets(), System.currentTimeMillis()), maps.get(challenge.mapId()));
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
        Set<UUID> members = partyService.onlinePartyMembers(player.getUniqueId());
        return members.size() >= 2 && members.size() <= 3 ? new LinkedHashSet<>(members) : Set.of(player.getUniqueId());
    }

    public void runTestDuel(Player player) {
        if (!enabled) {
            Text.send(player, "<red>Duels are currently disabled.</red>");
            return;
        }
        if (matchesByPlayer.containsKey(player.getUniqueId()) || queueByPlayer.containsKey(player.getUniqueId())) {
            Text.send(player, "<red>Leave your current duel or queue before starting a test duel.</red>");
            return;
        }
        String kitId = resolveKit(null);
        DuelKit kit = kit(kitId);
        if (kit == null || !kit.enabled()) {
            Text.send(player, "<red>No enabled duel kit is configured.</red>");
            return;
        }
        DuelMap map = null;
        String selected = selectedEditorMap.get(player.getUniqueId());
        if (selected != null && !selected.isBlank()) map = maps.get(selected.toLowerCase(Locale.ROOT));
        if (map == null) map = pickMap();
        if (map == null) {
            Text.send(player, "<red>No enabled duel map is configured.</red>");
            return;
        }
        player.closeInventory();
        Text.send(player, "<green>Starting one-player duel test.</green> <gray>Kit:</gray> <white>" + kit.displayName() + "</white> <gray>Map:</gray> <white>" + map.displayName() + "</white>");
        QueueUnit first = new QueueUnit(UUID.randomUUID(), DuelMode.SOLO, kit.id(), false, Set.of(player.getUniqueId()), System.currentTimeMillis());
        QueueUnit second = new QueueUnit(UUID.randomUUID(), DuelMode.SOLO, kit.id(), false, Set.of(), System.currentTimeMillis());
        startMatch(first, second, map, 1, true);
    }
    public void handleMapEditorClick(Player player, int slot) {
        switch (slot) {
            case 10 -> spawnEditorMarker(player, "lobby");
            case 11 -> spawnEditorMarker(player, "red-spawn");
            case 12 -> spawnEditorMarker(player, "blue-spawn");
            case 13 -> spawnEditorMarker(player, "ffa-spawn");
            case 14 -> spawnEditorMarker(player, "red-gate-out");
            case 15 -> spawnEditorMarker(player, "blue-gate-out");
            case 17 -> spawnEditorMarker(player, "spectator");
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
        inv.setItem(11, createTagged(Material.RED_BANNER, "<#f87171>Set Red Spawn</#f87171>", "map_set_spawn_a"));
        inv.setItem(12, createTagged(Material.BLUE_BANNER, "<#60a5fa>Set Blue Spawn</#60a5fa>", "map_set_spawn_b"));
        inv.setItem(13, createTagged(Material.COMPASS, "<gradient:#a78bfa:#f472b6>Set Spectator</gradient>", "map_set_spectator"));
        inv.setItem(14, createTagged(Material.RED_BANNER, "<#f87171>Set Red Gate Outside TP</#f87171>", "map_red_gate_out"));
        inv.setItem(15, createTagged(Material.BLUE_BANNER, "<#60a5fa>Set Blue Gate Outside TP</#60a5fa>", "map_blue_gate_out"));
        inv.setItem(16, createTagged(Material.STRUCTURE_BLOCK, "<gradient:#22d3ee:#8b5cf6>Save Arena</gradient>", "map_save_item"));
        inv.setItem(22, createTagged(Material.BOOK, "<gradient:#D6E8F7:#FFFFFF>Editor Active</gradient>", "map_editor_info"));
        inv.setItem(24, createTagged(Material.SUNFLOWER, "<gradient:#22d3ee:#8b5cf6>Save Markers</gradient>", "map_save"));
        inv.setItem(26, createTagged(Material.BARRIER, "<red>Back</red>", "map_close"));
        return inv;
    }
    public void giveQueueItem(Player player) {
        if (!configs.get("core/config.yml").getBoolean("spawn.hotbar-items.enabled", false)) {
            clearQueueItem(player);
            return;
        }
        if (!isSpawnWorld(player) || net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId()) || matchesByPlayer.containsKey(player.getUniqueId())) { clearQueueItem(player); return; }
        if (!configs.get("duels/duels.yml").getBoolean("duels.queue-item.enabled", true)) return;
        int slot = configs.get("duels/duels.yml").getInt("duels.queue-item.slot", 0);
        ItemStack item = createTagged(Material.DIAMOND_SWORD, configs.get("duels/duels.yml").getString("duels.queue-item.name", "<gradient:#60a5fa:#c084fc>Duel Queue</gradient>"), QUEUE_ITEM_ID);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Text.mm("<gray>Click to open duel modes.</gray>"),
                Text.mm("<gray>1v1 queued:</gray> <white>" + soloQueueCount() + "</white>"),
                Text.mm("<gray>2v2 queued:</gray> <white>" + partyQueueCount() + "</white>"),
                Text.mm("<gray>Your queue:</gray> <white>" + queueSummary(player.getUniqueId()) + "</white>")
        ));
        item.setItemMeta(meta);
        player.getInventory().setItem(slot, item);
    }

    private void clearQueueItem(Player player) { for (int i = 0; i < player.getInventory().getSize(); i++) if (QUEUE_ITEM_ID.equals(itemId(player.getInventory().getItem(i)))) player.getInventory().setItem(i, null); }

    public void reloadItems(Player player) {
        if (!matchesByPlayer.containsKey(player.getUniqueId()) && !ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId())) {
            clearHealthIndicatorEntry(player);
            updateHealthIndicatorScores();
        }
        giveQueueItem(player);
        giveLoadoutEditorItem(player);
    }

    private boolean isSpawnWorld(Player player) {
        String configured = configs.get("core/config.yml").getString("spawn.world", "spawn");
        return player.getWorld() != null && (player.getWorld().getName().equalsIgnoreCase(configured) || player.getWorld().getName().equalsIgnoreCase("spawn"));
    }

    private boolean isDuelEditorWorld(World world) {
        return world != null && world.getName().contains("_arena_edit");
    }

    private boolean isTemporaryDuelWorld(World world) {
        if (world == null) return false;
        String prefix = configs.get("duels/duels.yml").getString("duels.world-instances.prefix", "arena_");
        return world.getName().startsWith(prefix) && world.getName().contains("_match_");
    }

    private boolean shouldDisableChiseledBookshelf(World world) {
        if (world == null) return false;
        if (isDuelEditorWorld(world)) return configs.get("duels/duels.yml").getBoolean("duels.editor-worlds.disable-chiseled-bookshelves", true);
        if (isLiveMatchWorld(world)) return configs.get("duels/duels.yml").getBoolean("duels.world-instances.disable-chiseled-bookshelves", true);
        return false;
    }

    private boolean isDisabledChiseledBookshelf(Block block) {
        return block != null
            && block.getType() == Material.CHISELED_BOOKSHELF
            && shouldDisableChiseledBookshelf(block.getWorld());
    }

    private boolean isDisabledChiseledBookshelfInventory(Inventory inventory) {
        if (inventory == null || inventory.getLocation() == null) return false;
        return isDisabledChiseledBookshelf(inventory.getLocation().getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChiseledBookshelfInteract(PlayerInteractEvent event) {
        if (!isDisabledChiseledBookshelf(event.getClickedBlock())) return;
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChiseledBookshelfInventoryOpen(InventoryOpenEvent event) {
        if (isDisabledChiseledBookshelfInventory(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChiseledBookshelfInventoryMove(InventoryMoveItemEvent event) {
        if (isDisabledChiseledBookshelfInventory(event.getSource())
            || isDisabledChiseledBookshelfInventory(event.getDestination())
            || isDisabledChiseledBookshelfInventory(event.getInitiator())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isDisabledChiseledBookshelf(event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            handleSpectatorInteract(event.getPlayer(), event.getItem(), event.getClickedBlock() == null ? null : event.getClickedBlock().getType());
            return;
        }
        if (isFrozenDuelPlayer(event.getPlayer())) {
            event.setCancelled(true);
            if (event.getItem() != null && LOADOUT_ITEM_ID.equals(itemId(event.getItem()))) openLoadoutEditor(event.getPlayer());
            else refreshFrozenInventory(event.getPlayer());
            return;
        }
        if (event.getItem() == null) return;
        String editorTool = editorToolId(event.getItem());
        if (editorTool != null && editorTool.startsWith("gate-wand")) {
            handleGateWandClick(event, gateToolTarget(editorTool));
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (editorTool != null) {
            event.setCancelled(true);
            placeEditorMarkerFromTool(event.getPlayer(), editorTool, event.getClickedBlock() == null ? null : event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5));
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getItem().getType() == Material.END_CRYSTAL && matchesByPlayer.containsKey(event.getPlayer().getUniqueId())) {
            matchStatsService.recordCrystalPlaced(matchesByPlayer.get(event.getPlayer().getUniqueId()), event.getPlayer().getUniqueId());
        }
        String id = itemId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        if (QUEUE_ITEM_ID.equals(id)) { if (canUseInWorld(event.getPlayer())) openMainMenu(event.getPlayer()); else Text.send(event.getPlayer(), "<red>Duels are not available in this world.</red>"); }
        else if (LOADOUT_ITEM_ID.equals(id)) openLoadoutEditor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceInDuel(BlockPlaceEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (!isLiveMatchWorld(event.getBlockPlaced().getWorld())) return;
        if (isFrozenDuelPlayer(event.getPlayer())) {
            event.setCancelled(true);
            refreshFrozenInventory(event.getPlayer());
            return;
        }
        if (!matchesByPlayer.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        placedMatchBlocks.add(blockKey(event.getBlockPlaced().getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmptyInDuel(PlayerBucketEmptyEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (isFrozenDuelPlayer(event.getPlayer())) {
            event.setCancelled(true);
            refreshFrozenInventory(event.getPlayer());
            return;
        }
        if (!matchesByPlayer.containsKey(event.getPlayer().getUniqueId())) return;
        if (!isLiveMatchWorld(event.getBlock().getWorld())) return;
        org.bukkit.block.Block placed = event.getBlockClicked().getRelative(event.getBlockFace());
        placedMatchBlocks.add(blockKey(placed.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFillInDuel(PlayerBucketFillEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (!isFrozenDuelPlayer(event.getPlayer())) return;
        event.setCancelled(true);
        refreshFrozenInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakInDuel(BlockBreakEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (!isLiveMatchWorld(event.getBlock().getWorld())) return;
        if (isFrozenDuelPlayer(event.getPlayer())) {
            event.setCancelled(true);
            refreshFrozenInventory(event.getPlayer());
            return;
        }
        String key = blockKey(event.getBlock().getLocation());
        if (matchesByPlayer.containsKey(event.getPlayer().getUniqueId()) && placedMatchBlocks.remove(key)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (recoverStaleDuelPlayer(event.getPlayer())) return;
        reloadItems(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> worldService.keepPlayerAreaLoaded(event.getPlayer()));
    }
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, () -> { if (!recoverStaleDuelPlayer(event.getPlayer())) reloadItems(event.getPlayer()); }); }
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (mapEditorMode.remove(player.getUniqueId())) {
            restoreEditorInventory(player);
            Text.send(player, "<yellow>Duel map editor mode disabled because you changed worlds.</yellow>");
        }
        worldService.keepPlayerAreaLoaded(player);
        if (matchesByPlayer.containsKey(player.getUniqueId()) || ACTIVE_DUEL_PLAYERS.contains(player.getUniqueId())) return;
        if (recoverStaleDuelPlayer(player)) return;
        reloadItems(player);
    }
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (isFrozenDuelPlayer(event.getPlayer())) {
            event.setCancelled(true);
            refreshFrozenInventory(event.getPlayer());
            return;
        }
        ItemStack stack = event.getItemDrop().getItemStack();
        String id = itemId(stack);
        if (QUEUE_ITEM_ID.equals(id) || LOADOUT_ITEM_ID.equals(id) || editorToolId(stack) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onConsumeInDuel(PlayerItemConsumeEvent event) {
        if (isFrozenDuelPlayer(event.getPlayer())) {
            event.setCancelled(true);
            refreshFrozenInventory(event.getPlayer());
            return;
        }
        if (!matchesByPlayer.containsKey(event.getPlayer().getUniqueId())) return;
        Material type = event.getItem().getType();
        applyDuelConsumeCooldown(event.getPlayer(), type);
        if (type == Material.POTION) {
            matchStatsService.recordPotionUsed(matchesByPlayer.get(event.getPlayer().getUniqueId()), event.getPlayer().getUniqueId());
        }
        if (type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE) {
            matchStatsService.recordGoldenApple(matchesByPlayer.get(event.getPlayer().getUniqueId()), event.getPlayer().getUniqueId());
        }
        if (type != Material.GOLDEN_APPLE && type != Material.ENCHANTED_GOLDEN_APPLE) return;
        Bukkit.getScheduler().runTaskLater(plugin, event.getPlayer()::updateInventory, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDuelItemDamage(PlayerItemDamageEvent event) {
        if (!matchesByPlayer.containsKey(event.getPlayer().getUniqueId())) return;
        if (!configs.get("duels/duels.yml").getBoolean("duels.pvp.disable-item-durability-loss", true)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isSpectating(player)) {
            event.setCancelled(true);
            event.setCursor(null);
            refreshFrozenInventory(player);
            return;
        }
        if (event.getWhoClicked() instanceof Player player && mapEditorMode.contains(player.getUniqueId()) && event.getClickedInventory() == player.getInventory()) {
            if (event.isShiftClick() && event.getView().getTopInventory().getHolder() instanceof CoreMenuHolder) {
                event.setCancelled(true);
                refreshFrozenInventory(player);
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player player && (LOADOUT_ITEM_ID.equals(itemId(event.getCursor())) || QUEUE_ITEM_ID.equals(itemId(event.getCursor())) || editorToolId(event.getCursor()) != null)) {
            event.setCancelled(true);
            refreshFrozenInventory(player);
            return;
        }
        if (event.getWhoClicked() instanceof Player player && event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            ItemStack hotbar = button >= 0 && button < 9 ? player.getInventory().getItem(button) : null;
            if (LOADOUT_ITEM_ID.equals(itemId(hotbar)) || QUEUE_ITEM_ID.equals(itemId(hotbar)) || editorToolId(hotbar) != null) {
                event.setCancelled(true);
                refreshFrozenInventory(player);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player && event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (LOADOUT_ITEM_ID.equals(itemId(offhand)) || QUEUE_ITEM_ID.equals(itemId(offhand)) || editorToolId(offhand) != null) {
                event.setCancelled(true);
                refreshFrozenInventory(player);
                return;
            }
        }
        String editorTool = editorToolId(event.getCurrentItem());
        if (editorTool != null) {
            if (event.getWhoClicked() instanceof Player player
                    && mapEditorMode.contains(player.getUniqueId())
                    && event.getClickedInventory() == player.getInventory()) {
                return;
            }
            event.setCancelled(true);
            event.setCurrentItem(event.getCurrentItem());
            if (event.getWhoClicked() instanceof Player p) Bukkit.getScheduler().runTask(plugin, p::updateInventory);
            return;
        }
        String id = itemId(event.getCurrentItem());
        if (id == null) return;
        if (LOADOUT_ITEM_ID.equals(id)) {
            event.setCancelled(true);
            event.setCursor(null);
            event.setCurrentItem(event.getCurrentItem());
            if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == player.getInventory()) {
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.setItemOnCursor(null);
                    player.updateInventory();
                    openLoadoutEditor(player);
                }, 1L);
            }
            return;
        }
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
        worldService.keepPlayerAreaLoaded(event.getPlayer());
        if (!frozenDuelPlayers.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (gateService.enforceCollision(event.getPlayer(), to)) {
            Location safe = event.getPlayer().getLocation();
            safe.setYaw(to.getYaw());
            safe.setPitch(to.getPitch());
            event.setTo(safe);
            return;
        }
        event.getPlayer().setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        event.getPlayer().setFallDistance(0.0f);
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDuelChunkMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        gateService.enforceCollision(event.getPlayer(), to);
        if (event.getFrom().getBlockX() >> 4 == to.getBlockX() >> 4 && event.getFrom().getBlockZ() >> 4 == to.getBlockZ() >> 4) return;
        if (!matchesByPlayer.containsKey(event.getPlayer().getUniqueId()) && !ACTIVE_DUEL_PLAYERS.contains(event.getPlayer().getUniqueId())) return;
        worldService.keepPlayerAreaLoaded(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeamDamage(EntityDamageByEntityEvent event) {
        Player attackerCheck = killerFromDamage(event);
        if (attackerCheck != null && isSpectating(attackerCheck)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attackerCheck;
        if (attacker == null) return;
        DuelMatch attackerMatch = matchesByPlayer.get(attacker.getUniqueId());
        DuelMatch victimMatch = matchesByPlayer.get(victim.getUniqueId());
        if (attackerMatch == null || victimMatch == null || !attackerMatch.id().equals(victimMatch.id())) return;
        if (frozenDuelPlayers.contains(attacker.getUniqueId()) || frozenDuelPlayers.contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (sameTeam(attackerMatch, attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        applyDuelPvpDamage(event, attacker, victim);
        matchStatsService.recordDamage(attackerMatch, attacker.getUniqueId(), victim.getUniqueId(), Math.min(event.getFinalDamage(), Math.max(0.0D, victim.getHealth())), event.getDamager() instanceof Projectile);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isSpectating(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemPop(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        DuelMatch match = matchesByPlayer.get(player.getUniqueId());
        if (match == null) return;
        matchStatsService.recordTotem(match, player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalBreak(EntityDamageByEntityEvent event) {
        if (!event.getEntity().getType().name().equals("END_CRYSTAL")) return;
        Player attacker = killerFromDamage(event);
        if (attacker == null) return;
        DuelMatch match = matchesByPlayer.get(attacker.getUniqueId());
        if (match == null) return;
        matchStatsService.recordCrystalBroken(match, attacker.getUniqueId());
    }

    private boolean isPublicDuelSubcommand(String sub) {
        return sub.isBlank() || Set.of("menu", "queueui", "queues", "chest", "chestui", "solo", "1v1", "ranked", "duo", "party", "2v2", "ffa", "accept", "deny", "decline", "leave", "queue", "leaderboard", "top", "deluxe", "dm").contains(sub);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        DuelMatch match = matchesByPlayer.get(event.getEntity().getUniqueId());
        if (match == null) return;
        event.getDrops().clear();
        Player killer = validDuelKiller(match, event.getEntity(), event.getEntity().getKiller());
        if (killer != null) playKillEffect(killer);
        handleDuelElimination(event.getEntity(), match, "death", killer);
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
        Player killer = validDuelKiller(match, player, killerFromDamage(event));
        playKillEffect(killer);
        event.setCancelled(true);
        handleDuelElimination(player, match, "death", killer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ACTIVE_DUEL_PLAYERS.remove(uuid);
        frozenDuelPlayers.remove(uuid);
        preparingDuelPlayers.remove(uuid);
        loadoutButtonDisplacements.remove(uuid);
        SpectatorSnapshot spectatorSnapshot = spectatorSnapshots.remove(uuid);
        if (spectatorSnapshot != null) spectatorSnapshot.restore(event.getPlayer());
        if (mapEditorMode.remove(event.getPlayer().getUniqueId())) restoreEditorInventory(event.getPlayer());
        QueueUnit unit = queueByPlayer.get(uuid);
        if (unit != null) removeQueueUnit(unit, false);
        DuelMatch match = matchesByPlayer.get(uuid);
        if (match != null) {
            announceLeave(event.getPlayer(), true);
            Set<UUID> winners = forfeitWinners(match, uuid);
            snapshots.remove(uuid);
            returnPlayerToSpawnAfterDuel(event.getPlayer(), spawnLocation());
            Bukkit.getScheduler().runTaskLater(plugin, () -> endMatch(match, winners, "quit"), 10L);
        }
        clearHealthIndicatorEntry(event.getPlayer());
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
        if (!matchesByPlayer.containsKey(player.getUniqueId())) return;
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.1, 0), 38, 0.45, 0.65, 0.45, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.55f);
    }

    private Player validDuelKiller(DuelMatch match, Player eliminated, Player killer) {
        if (match == null || eliminated == null || killer == null) return null;
        if (killer.getUniqueId().equals(eliminated.getUniqueId())) return null;
        DuelMatch killerMatch = matchesByPlayer.get(killer.getUniqueId());
        if (killerMatch == null || !killerMatch.id().equals(match.id())) return null;
        if (sameTeam(match, killer.getUniqueId(), eliminated.getUniqueId())) return null;
        return killer;
    }

    private void applyDuelPvpDamage(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (!configs.get("duels/duels.yml").getBoolean("duels.pvp.enabled", true)) return;
        double multiplier = configs.get("duels/duels.yml").getDouble("duels.pvp.damage.global-multiplier", 1.0D);
        if (event.getDamager() instanceof Projectile projectile) {
            multiplier *= configs.get("duels/duels.yml").getDouble("duels.pvp.damage.projectile-multiplier", 1.0D);
            multiplier *= projectileMultiplier(projectile);
        } else {
            Material weapon = attacker.getInventory().getItemInMainHand().getType();
            multiplier *= configs.get("duels/duels.yml").getDouble("duels.pvp.damage.melee-multiplier", 1.0D);
            if (isSword(weapon)) multiplier *= configs.get("duels/duels.yml").getDouble("duels.pvp.damage.sword-multiplier", 1.0D);
            else if (isAxe(weapon)) multiplier *= configs.get("duels/duels.yml").getDouble("duels.pvp.damage.axe-multiplier", 1.0D);
            else if (weapon == Material.MACE) multiplier *= configs.get("duels/duels.yml").getDouble("duels.pvp.damage.mace-multiplier", 1.0D);
        }
        if (multiplier != 1.0D) event.setDamage(Math.max(0.0D, event.getDamage() * Math.max(0.0D, multiplier)));
        int fireCap = configs.get("duels/duels.yml").getInt("duels.pvp.fire-tick-cap", -1);
        if (fireCap >= 0 && victim.getFireTicks() > fireCap) victim.setFireTicks(fireCap);
    }

    private double projectileMultiplier(Projectile projectile) {
        String type = projectile.getType().name();
        if (type.equals("ARROW") || type.equals("SPECTRAL_ARROW")) return configs.get("duels/duels.yml").getDouble("duels.pvp.damage.arrow-multiplier", 1.0D);
        if (type.equals("TRIDENT")) return configs.get("duels/duels.yml").getDouble("duels.pvp.damage.trident-multiplier", 1.0D);
        if (type.equals("WIND_CHARGE") || type.equals("BREEZE_WIND_CHARGE")) return configs.get("duels/duels.yml").getDouble("duels.pvp.damage.wind-charge-multiplier", 1.0D);
        return 1.0D;
    }

    private void applyDuelProjectileLaunchRules(Player player, ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl) {
            int cooldown = Math.max(0, configs.get("duels/duels.yml").getInt("duels.pvp.cooldowns.ender-pearl-ticks", 200));
            if (cooldown > 0) player.setCooldown(Material.ENDER_PEARL, cooldown);
        }
    }

    private void applyDuelConsumeCooldown(Player player, Material type) {
        int ticks = switch (type) {
            case GOLDEN_APPLE -> configs.get("duels/duels.yml").getInt("duels.pvp.cooldowns.golden-apple-ticks", 0);
            case ENCHANTED_GOLDEN_APPLE -> configs.get("duels/duels.yml").getInt("duels.pvp.cooldowns.enchanted-golden-apple-ticks", 0);
            default -> 0;
        };
        if (ticks > 0) Bukkit.getScheduler().runTask(plugin, () -> player.setCooldown(type, ticks));
    }

    private boolean isSword(Material material) {
        return material != null && material.name().endsWith("_SWORD");
    }

    private boolean isAxe(Material material) {
        return material != null && material.name().endsWith("_AXE");
    }

    private void handleDuelElimination(Player eliminated, DuelMatch match, String reason, Player killer) {
        if (!match.markEliminated(eliminated.getUniqueId())) return;
        scoreService.recordDeath(eliminated.getUniqueId());
        if (killer != null) scoreService.recordKill(killer.getUniqueId());
        Set<UUID> winners = match.roundWinners();
        eliminated.setHealth(Math.max(1.0, Math.min(20.0, eliminated.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : eliminated.getAttribute(Attribute.MAX_HEALTH).getValue())));
        eliminated.setFireTicks(0);
        eliminated.getInventory().clear();
        eliminated.getInventory().setArmorContents(new ItemStack[4]);
        eliminated.getInventory().setItemInOffHand(null);
        eliminated.setGameMode(GameMode.SPECTATOR);
        Player target = firstOnline(winners.isEmpty() ? opposingTeam(match, eliminated.getUniqueId()) : winners);
        if (target != null) {
            try {
                eliminated.setSpectatorTarget(target);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (winners.isEmpty()) return;
        if (match.awardWin(winners) && !match.isComplete()) {
            Set<UUID> all = new LinkedHashSet<>();
            all.addAll(match.teamOne());
            all.addAll(match.teamTwo());
            announceRoundWin(match, winners, false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> resetAndRestartRound(match), nextRoundDelayTicks());
            return;
        }
        if (!endingMatches.add(match.id())) return;
        String winnerNames = names(winners);
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(match.teamOne());
        all.addAll(match.teamTwo());
        announceRoundWin(match, winners, true);
        notifyMembers(all, "<gradient:#f4cd2a:#eda323:#d28d0d>Duel finished.</gradient> <gray>Winner:</gray> <white>" + winnerNames + "</white>");
        for (UUID uuid : winners) scoreService.recordWin(uuid);
        for (UUID uuid : all) if (!winners.contains(uuid)) scoreService.recordLoss(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> endMatch(match, winners, reason), 100L);
    }

    private void announceRoundWin(DuelMatch match, Set<UUID> winners, boolean finalRound) {
        Bukkit.getPluginManager().callEvent(new DuelRoundWinEvent(match, winners));
        Set<UUID> all = matchMembers(match);
        if (match.mode() == DuelMode.FFA) {
            String winnerLabel = names(winners);
            String score = ffaScoreSummary(match);
            notifyMembers(all, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>DUELS</bold></gradient> <dark_gray>»</dark_gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + winnerLabel + "</gradient> <#dbeafe>won the FFA!</#dbeafe>");
            notifyMembers(all, score);
            titleMembers(all, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>" + winnerLabel.toUpperCase(Locale.ROOT) + " WINS</bold></gradient>", finalRound ? "<#dbeafe>Final</#dbeafe>" : score);
            sendScoreDisplay(match);
            return;
        }
        Set<UUID> losers = match.teamOne().containsAll(winners) ? match.teamTwo() : match.teamOne();
        boolean solo = match.mode() == DuelMode.SOLO && match.teamOne().size() == 1 && match.teamTwo().size() == 1;
        String winnerLabel = solo ? names(winners) : (match.teamOne().containsAll(winners) ? "Red" : "Blue");
        String loserLabel = solo ? names(losers) : (match.teamOne().containsAll(winners) ? "Blue" : "Red");
        if (solo) {
            notifyMembers(all, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>DUELS</bold></gradient> <dark_gray>»</dark_gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + winnerLabel + "</gradient> <#dbeafe>won against</#dbeafe> <#60a5fa>" + loserLabel + "</#60a5fa><#dbeafe>!</#dbeafe>");
        } else {
            notifyMembers(all, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>DUELS</bold></gradient> <dark_gray>»</dark_gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + winnerLabel + " Team</gradient> <#dbeafe>defeated</#dbeafe> <#60a5fa>" + loserLabel + " Team</#60a5fa><#dbeafe>!</#dbeafe>");
        }
        String title = "<gradient:#f4cd2a:#eda323:#d28d0d><bold>" + winnerLabel.toUpperCase(Locale.ROOT) + " WINS</bold></gradient>";
        String subtitle = "<#f87171>" + match.teamOneWins() + "</#f87171> <#dbeafe>-</#dbeafe> <#60a5fa>" + match.teamTwoWins() + "</#60a5fa>" + (finalRound ? " <#dbeafe>Final</#dbeafe>" : "");
        titleMembers(all, title, subtitle);
        sendScoreDisplay(match);
    }

    private void sendScoreDisplay(DuelMatch match) {
        if (!configs.get("duels/duels.yml").getBoolean("duels.ui.score-actionbar", true)) return;
        if (match.mode() == DuelMode.FFA) {
            actionBarMembers(matchMembers(match), "<gradient:#f4cd2a:#eda323:#d28d0d>FFA</gradient> <gray>Leader:</gray> " + ffaLeaderSummary(match) + " <dark_gray>|</dark_gray> <gray>Remaining:</gray> <white>" + activeFfaPlayers(match).size() + "</white>");
            return;
        }
        String format = configs.get("duels/duels.yml").getString("duels.ui.score-format", "<#f87171>Red %red_score%</#f87171> <#dbeafe>-</#dbeafe> <#60a5fa>%blue_score% Blue</#60a5fa>");
        String message = format.replace("%red_score%", String.valueOf(match.teamOneWins())).replace("%blue_score%", String.valueOf(match.teamTwoWins()));
        actionBarMembers(matchMembers(match), message);
    }

    private void resetAndRestartRound(DuelMatch match) {
        DuelMap map = activeMapsByMatch.getOrDefault(match.id(), maps.get(match.mapId()));
        if (map == null) return;
        if (!matchMembersStillValid(match)) {
            Set<UUID> winners = firstOnline(match.teamOne()) == null ? match.teamTwo() : match.teamOne();
            endMatch(match, winners, "player unavailable");
            return;
        }
        match.resetRoundState();
        gateService.resetGates(match);
        cleanupRoundArena(match);
        applyScoreboardTeams(match);
        applyHealthIndicator(match);
        normalizeMatchVisibility(match);
        if (match.mode() == DuelMode.FFA) {
            List<Location> spawns = ffaSpawnLocations(map);
            int index = 0;
            for (UUID uuid : matchMembers(match)) resetForNextRound(uuid, spawns.get(index++ % spawns.size()));
        } else {
            for (UUID uuid : match.teamOne()) resetForNextRound(uuid, map.spawnA());
            for (UUID uuid : match.teamTwo()) resetForNextRound(uuid, map.spawnB());
        }
        notifyMembers(match.teamOne(), "<gray>Next round starting soon...</gray>");
        notifyMembers(match.teamTwo(), "<gray>Next round starting soon...</gray>");
        prepareMatch(match, map);
        startArenaCountdown(match, map);
    }

    private void resetForNextRound(UUID uuid, Location spawn) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        DuelMatch match = matchesByPlayer.get(uuid);
        if (match == null) return;
        if (player.isDead()) player.spigot().respawn();
        normalizeDuelPlayerState(player);
        player.setGameMode(GameMode.SURVIVAL);
        resetDuelState(player);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) player.setHealth(Math.max(1.0, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        else player.setHealth(20.0);
        applyDuelFoodState(player);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        worldService.keepPlayerAreaLoaded(player);
        teleportToRoundSpawn(player, spawn);
        worldService.keepPlayerAreaLoaded(player);
        loadoutButtonDisplacements.remove(uuid);
        stripHubItems(player);
        DuelKit kit = kits.get(match.kitId().toLowerCase(Locale.ROOT));
        if (kit != null) applyKit(player, kit);
        player.updateInventory();
    }

    private void teleportToRoundSpawn(Player player, Location spawn) {
        if (player == null || spawn == null || spawn.getWorld() == null) return;
        player.teleport(spawn);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.setFallDistance(0.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> retryRoundSpawn(player, spawn), 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> retryRoundSpawn(player, spawn), 8L);
    }

    private void retryRoundSpawn(Player player, Location spawn) {
        if (player == null || !player.isOnline() || spawn == null || spawn.getWorld() == null) return;
        DuelMatch match = matchesByPlayer.get(player.getUniqueId());
        if (match == null) return;
        if (!player.getWorld().equals(spawn.getWorld()) || player.getLocation().distanceSquared(spawn) > 16.0D) {
            player.teleport(spawn);
        }
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.setFallDistance(0.0f);
    }

    private boolean matchMembersStillValid(DuelMatch match) {
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(match.teamOne());
        all.addAll(match.teamTwo());
        for (UUID uuid : all) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return false;
            if (matchesByPlayer.get(uuid) != match) return false;
        }
        return true;
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
        String normalized = normalizeEditorMarkerType(type);
        if (normalized.equals("save-arena")) { saveArenaCommand(player); return; }
        if (!List.of("lobby", "red-spawn", "blue-spawn", "ffa-spawn", "red-gate-out", "blue-gate-out", "spectator").contains(normalized)) {
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
            armorStand.customName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>3SMP Duel " + normalized + "</gradient>"));
            armorStand.getPersistentDataContainer().set(new NamespacedKey(plugin, "duel_editor_marker"), PersistentDataType.STRING, normalized);
        });
        Text.send(player, "<green>Placed duel editor marker:</green> <white>" + displayMarkerType(normalized) + "</white> <gray>yaw " + Math.round(loc.getYaw()) + " pitch " + Math.round(loc.getPitch()) + "</gray>");
    }

    private String normalizeEditorMarkerType(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT).replace("_", "-");
        return switch (normalized) {
            case "spawn-a", "red", "redspawn" -> "red-spawn";
            case "spawn-b", "blue", "bluespawn" -> "blue-spawn";
            case "ffa", "ffaspawn", "player-spawn", "playerspawn", "player" -> "ffa-spawn";
            case "red-gate-exit", "red-gate-outside", "redout", "red-out" -> "red-gate-out";
            case "blue-gate-exit", "blue-gate-outside", "blueout", "blue-out" -> "blue-gate-out";
            default -> normalized;
        };
    }

    private String displayMarkerType(String type) {
        return switch (normalizeEditorMarkerType(type)) {
            case "red-spawn" -> "Red Spawn";
            case "blue-spawn" -> "Blue Spawn";
            case "ffa-spawn" -> "FFA Player Spawn";
            case "red-gate-out" -> "Red Gate Outside TP";
            case "blue-gate-out" -> "Blue Gate Outside TP";
            case "lobby" -> "Lobby";
            case "spectator" -> "Spectator";
            default -> type;
        };
    }

    private Location editorPlacementLocation(Player player, Location clickedLocation) {
        Location loc = player.getLocation().clone();
        if (clickedLocation != null && clickedLocation.getWorld() != null) loc = clickedLocation;
        loc.setWorld(player.getWorld());
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        return loc;
    }

    private void handleGateWandClick(PlayerInteractEvent event, GateEditorTarget target) {
        if (target == null) return;
        Player player = event.getPlayer();
        if (!mapEditorMode.contains(player.getUniqueId())) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        String key = gateKey(player, target);
        String label = gateTargetLabel(target);
        if (player.isSneaking()) {
            gateSelectionPos1.remove(key);
            gateSelectionPos2.remove(key);
            Text.send(player, "<yellow>" + label + " selection cleared.</yellow>");
            return;
        }
        Location clicked = event.getClickedBlock() == null ? player.getLocation().getBlock().getLocation() : event.getClickedBlock().getLocation();
        if (action == Action.LEFT_CLICK_BLOCK) {
            gateSelectionPos1.put(key, clicked);
            Text.send(player, "<green>" + label + " pos1 set.</green> <gray>" + clicked.getBlockX() + "," + clicked.getBlockY() + "," + clicked.getBlockZ() + "</gray>");
        } else {
            gateSelectionPos2.put(key, clicked);
            Text.send(player, "<green>" + label + " pos2 set.</green> <gray>" + clicked.getBlockX() + "," + clicked.getBlockY() + "," + clicked.getBlockZ() + "</gray>");
        }
        gateEditorTargets.put(player.getUniqueId(), target);
        previewCurrentGateSelection(player, target);
    }

    private void saveSelectedGate(Player player, GateEditorTarget target) {
        String mapId = selectedEditorMap.get(player.getUniqueId());
        DuelMap map = mapId == null ? null : maps.get(mapId);
        if (map == null) {
            Text.send(player, "<red>Select an arena first.</red>");
            return;
        }
        String key = gateKey(player, target);
        DuelGateRegion region = DuelGateRegion.from(gateSelectionPos1.get(key), gateSelectionPos2.get(key));
        if (region == null) {
            Text.send(player, "<red>Cannot save arena: " + (target == GateEditorTarget.RED_GATE ? "red" : "blue") + " gate missing pos1/pos2.</red>");
            return;
        }
        region = regionForMapWorld(map, region);
        DuelMap updated = target == GateEditorTarget.RED_GATE ? map.withRedGate(region) : map.withBlueGate(region);
        maps.put(mapId, updated);
        saveMaps();
        gateEditorTargets.put(player.getUniqueId(), target);
        String color = target == GateEditorTarget.RED_GATE ? "Red" : "Blue";
        Text.send(player, "<green>" + color + " gate region set:</green> <white>" + region.blockCount() + " blocks</white> <gray>(" + region.dimensions() + ")</gray>");
        Text.send(player, "<green>Arena gate regions saved.</green>");
        gateService.previewGates(player, updated);
    }

    private void saveSelectedZone(Player player, GateEditorTarget target) {
        String mapId = selectedEditorMap.get(player.getUniqueId());
        DuelMap map = mapId == null ? null : maps.get(mapId);
        if (map == null) {
            Text.send(player, "<red>Select an arena first.</red>");
            return;
        }
        String key = gateKey(player, target);
        DuelGateRegion region = DuelGateRegion.from(gateSelectionPos1.get(key), gateSelectionPos2.get(key));
        if (region == null) {
            Text.send(player, "<red>Cannot save arena: " + gateTargetLabel(target).toLowerCase(Locale.ROOT) + " missing pos1/pos2.</red>");
            return;
        }
        region = regionForMapWorld(map, region);
        DuelMap updated = target == GateEditorTarget.RED_CLOSE_ZONE ? map.withRedGateCloseZone(region) : map.withBlueGateCloseZone(region);
        maps.put(mapId, updated);
        saveMaps();
        gateEditorTargets.put(player.getUniqueId(), target);
        Text.send(player, "<green>" + gateTargetLabel(target) + " set:</green> <white>" + region.blockCount() + " blocks</white> <gray>(" + region.dimensions() + ")</gray>");
        Text.send(player, "<green>Arena gate zones saved.</green>");
        gateService.previewGates(player, updated);
    }

    private void clearGate(Player player, String team) {
        String mapId = selectedEditorMap.get(player.getUniqueId());
        DuelMap map = mapId == null ? null : maps.get(mapId);
        if (map == null) {
            Text.send(player, "<red>Select an arena first.</red>");
            return;
        }
        if (team.equals("red")) {
            maps.put(mapId, map.withRedGate(null));
            saveMaps();
            Text.send(player, "<yellow>Red gate cleared.</yellow>");
        } else if (team.equals("blue")) {
            maps.put(mapId, map.withBlueGate(null));
            saveMaps();
            Text.send(player, "<yellow>Blue gate cleared.</yellow>");
        } else {
            Text.send(player, "<yellow>/3smpcore duel editor clear gate <red|blue></yellow>");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isSpectating(player)) {
            event.setCancelled(true);
            refreshFrozenInventory(player);
            return;
        }
        if (mapEditorMode.contains(player.getUniqueId())) {
            int topSize = event.getView().getTopInventory().getSize();
            boolean bottomOnly = event.getRawSlots().stream().allMatch(raw -> raw >= topSize);
            if (bottomOnly) return;
            if (editorToolId(event.getOldCursor()) != null || LOADOUT_ITEM_ID.equals(itemId(event.getOldCursor())) || QUEUE_ITEM_ID.equals(itemId(event.getOldCursor()))) {
                event.setCancelled(true);
                refreshFrozenInventory(player);
            }
            return;
        }
        if (!isFrozenDuelPlayer(player)) return;
        ItemStack cursor = event.getOldCursor();
        if (LOADOUT_ITEM_ID.equals(itemId(cursor)) || QUEUE_ITEM_ID.equals(itemId(cursor)) || editorToolId(cursor) != null) {
            event.setCancelled(true);
            refreshFrozenInventory(player);
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) continue;
            int slot = event.getView().convertSlot(raw);
            ItemStack target = slot >= 0 && slot < 36 ? player.getInventory().getItem(slot) : null;
            if (LOADOUT_ITEM_ID.equals(itemId(target)) || QUEUE_ITEM_ID.equals(itemId(target)) || editorToolId(target) != null) {
                event.setCancelled(true);
                refreshFrozenInventory(player);
                return;
            }
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (!isFrozenDuelPlayer(event.getPlayer())) return;
        event.setCancelled(true);
        refreshFrozenInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isSpectating(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpectatorPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isSpectating(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunchWhileFrozen(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (isFrozenDuelPlayer(player)) {
            event.setCancelled(true);
            refreshFrozenInventory(player);
            return;
        }
        if (!matchesByPlayer.containsKey(player.getUniqueId())) return;
        matchStatsService.recordProjectileLaunched(matchesByPlayer.get(player.getUniqueId()), player.getUniqueId());
        if (event.getEntity() instanceof ThrownPotion) matchStatsService.recordPotionUsed(matchesByPlayer.get(player.getUniqueId()), player.getUniqueId());
        applyDuelProjectileLaunchRules(player, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDuelProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        if (!matchesByPlayer.containsKey(player.getUniqueId())) return;
        if (event.getHitEntity() instanceof Player hit && matchesByPlayer.get(hit.getUniqueId()) != null) {
            DuelMatch shooterMatch = matchesByPlayer.get(player.getUniqueId());
            DuelMatch hitMatch = matchesByPlayer.get(hit.getUniqueId());
            if (shooterMatch.id().equals(hitMatch.id()) && !sameTeam(shooterMatch, player.getUniqueId(), hit.getUniqueId())) {
                matchStatsService.recordProjectileHit(shooterMatch, player.getUniqueId());
            }
        }
        if (!configs.get("duels/duels.yml").getBoolean("duels.pvp.remove-projectiles-on-hit", true)) return;
        long delay = Math.max(0L, configs.get("duels/duels.yml").getLong("duels.pvp.projectile-remove-delay-ticks", 40L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getEntity().isValid()) event.getEntity().remove();
        }, delay);
    }

    private void clearGateZone(Player player, String team) {
        String mapId = selectedEditorMap.get(player.getUniqueId());
        DuelMap map = mapId == null ? null : maps.get(mapId);
        if (map == null) {
            Text.send(player, "<red>Select an arena first.</red>");
            return;
        }
        if (team.equals("red")) {
            maps.put(mapId, map.withRedGateCloseZone(null));
            saveMaps();
            Text.send(player, "<yellow>Red gate zone cleared.</yellow>");
        } else if (team.equals("blue")) {
            maps.put(mapId, map.withBlueGateCloseZone(null));
            saveMaps();
            Text.send(player, "<yellow>Blue gate zone cleared.</yellow>");
        } else {
            Text.send(player, "<yellow>/3smpcore duel editor clear zone <red|blue></yellow>");
        }
    }

    private void previewCurrentGateSelection(Player player, GateEditorTarget target) {
        DuelGateRegion region = DuelGateRegion.from(gateSelectionPos1.get(gateKey(player, target)), gateSelectionPos2.get(gateKey(player, target)));
        if (region == null) return;
        Color color = target == GateEditorTarget.RED_GATE || target == GateEditorTarget.RED_CLOSE_ZONE ? Color.fromRGB(248, 113, 113) : Color.fromRGB(96, 165, 250);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.1F);
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    player.spawnParticle(Particle.DUST, new Location(player.getWorld(), x + 0.5, y + 0.5, z + 0.5), 1, 0, 0, 0, dust);
                }
            }
        }
    }

    private String gateKey(Player player, GateEditorTarget target) {
        return player.getUniqueId() + ":" + target.name();
    }

    private DuelGateRegion regionForMapWorld(DuelMap map, DuelGateRegion region) {
        if (map == null || region == null || map.worldName() == null || map.worldName().isBlank()) return region;
        return new DuelGateRegion(map.worldName(), region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ());
    }

    private GateEditorTarget gateToolTarget(String tool) {
        return switch (tool.toLowerCase(Locale.ROOT)) {
            case "gate-wand-blue" -> GateEditorTarget.BLUE_GATE;
            case "gate-wand-red-zone" -> GateEditorTarget.RED_CLOSE_ZONE;
            case "gate-wand-blue-zone" -> GateEditorTarget.BLUE_CLOSE_ZONE;
            default -> GateEditorTarget.RED_GATE;
        };
    }

    private String gateTargetLabel(GateEditorTarget target) {
        return switch (target) {
            case RED_GATE -> "Red gate";
            case BLUE_GATE -> "Blue gate";
            case RED_CLOSE_ZONE -> "Red gate zone";
            case BLUE_CLOSE_ZONE -> "Blue gate zone";
        };
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
        String[] types = {"lobby", "red-spawn", "blue-spawn", "ffa-spawn", "red-gate-out", "blue-gate-out", "spectator"};
        Material[] icons = {Material.LECTERN, Material.RED_BANNER, Material.BLUE_BANNER, Material.NETHER_STAR, Material.RED_BANNER, Material.BLUE_BANNER, Material.COMPASS};
        int[] slots = {0, 1, 2, 3, 4, 5, 16};
        for (int i = 0; i < types.length; i++) player.getInventory().setItem(slots[i], createEditorTool(icons[i], types[i]));
        player.getInventory().setItem(6, createEditorTool(Material.WOODEN_AXE, "gate-wand-red"));
        player.getInventory().setItem(7, createEditorTool(Material.GOLDEN_AXE, "gate-wand-blue"));
        player.getInventory().setItem(17, createEditorTool(Material.REDSTONE_TORCH, "gate-wand-red-zone"));
        player.getInventory().setItem(26, createEditorTool(Material.SOUL_TORCH, "gate-wand-blue-zone"));
        player.getInventory().setItem(8, createEditorTool(Material.STRUCTURE_BLOCK, "save-arena"));
        Text.send(player, "<green>Duel editor tools loaded.</green> <gray>Axes select gates. Torches select close teleport zones.</gray>");
    }

    private void giveGateWand(Player player, GateEditorTarget target) {
        mapEditorMode.add(player.getUniqueId());
        editorInventorySnapshots.putIfAbsent(player.getUniqueId(), player.getInventory().getContents());
        Material material = switch (target) {
            case RED_GATE -> Material.WOODEN_AXE;
            case BLUE_GATE -> Material.GOLDEN_AXE;
            case RED_CLOSE_ZONE -> Material.REDSTONE_TORCH;
            case BLUE_CLOSE_ZONE -> Material.SOUL_TORCH;
        };
        String tool = switch (target) {
            case RED_GATE -> "gate-wand-red";
            case BLUE_GATE -> "gate-wand-blue";
            case RED_CLOSE_ZONE -> "gate-wand-red-zone";
            case BLUE_CLOSE_ZONE -> "gate-wand-blue-zone";
        };
        player.getInventory().addItem(createEditorTool(material, tool));
    }

    private void removeEditorTools(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (editorToolId(player.getInventory().getItem(i)) != null) player.getInventory().setItem(i, null);
        }
    }

    private ItemStack createEditorTool(Material material, String type) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>Marker: " + type + "</gradient>"));
        List<net.kyori.adventure.text.Component> lore = type.startsWith("gate-wand")
            ? List.of(Text.mm("<gray>Left click pos1, right click pos2.</gray>"), Text.mm("<gray>Sneak-click clears the current selection.</gray>"))
            : List.of(Text.mm("<gray>Right-click to place this marker exactly at your position.</gray>"), Text.mm("<gray>Your yaw and pitch are saved for spawn markers.</gray>"));
        meta.lore(lore);
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
        if (found.containsKey("red-spawn")) updated = updated.withSpawnA(found.get("red-spawn"));
        else if (found.containsKey("spawn-a")) updated = updated.withSpawnA(found.get("spawn-a"));
        if (found.containsKey("blue-spawn")) updated = updated.withSpawnB(found.get("blue-spawn"));
        else if (found.containsKey("spawn-b")) updated = updated.withSpawnB(found.get("spawn-b"));
        if (found.containsKey("ffa-spawn")) updated = updated.withFfaSpawn(found.get("ffa-spawn"));
        if (found.containsKey("red-gate-out")) updated = updated.withRedGateExit(found.get("red-gate-out"));
        if (found.containsKey("blue-gate-out")) updated = updated.withBlueGateExit(found.get("blue-gate-out"));
        if (found.containsKey("spectator")) updated = updated.withSpectator(found.get("spectator"));
        int removedMarkers = removeEditorMarkers(player.getWorld());
        if (player.getWorld() != null) player.getWorld().save();
        World live = worldService.publishArena(mapId, player.getWorld());
        if (live != null) {
            updated = new DuelMap(updated.id(), updated.displayName(), updated.enabled(), live.getName(), toWorld(updated.lobby(), live), toWorld(updated.spawnA(), live), toWorld(updated.spawnB(), live), toWorld(updated.ffaSpawn(), live), toWorld(updated.spectator(), live), toGateWorld(updated.redGate(), live), toGateWorld(updated.blueGate(), live), toWorld(updated.redGateExit(), live), toWorld(updated.blueGateExit(), live), toGateWorld(updated.redGateCloseZone(), live), toGateWorld(updated.blueGateCloseZone(), live));
        } else if (player.getWorld() != null && player.getWorld().getName().startsWith("arena_")) {
            updated = new DuelMap(updated.id(), updated.displayName(), updated.enabled(), player.getWorld().getName(), updated.lobby(), updated.spawnA(), updated.spawnB(), updated.ffaSpawn(), updated.spectator(), toGateWorld(updated.redGate(), player.getWorld()), toGateWorld(updated.blueGate(), player.getWorld()), toWorld(updated.redGateExit(), player.getWorld()), toWorld(updated.blueGateExit(), player.getWorld()), toGateWorld(updated.redGateCloseZone(), player.getWorld()), toGateWorld(updated.blueGateCloseZone(), player.getWorld()));
        }
        maps.put(mapId, updated);
        saveMaps();
        List<String> gateFailures = gateService.validate(updated);
        Text.send(player, "<green>Saved arena " + mapId + ". Found markers: " + String.join(", ", found.keySet().stream().map(this::displayMarkerType).toList()) + ".</green> <gray>Removed " + removedMarkers + " editor markers.</gray>");
        if (gateFailures.isEmpty()) Text.send(player, "<green>Arena gates validated successfully.</green>");
        else Text.send(player, "<yellow>Arena saved, but gates need work:</yellow> <gray>" + String.join(", ", gateFailures) + "</gray>");
        finishMapEditorAfterSave(player);
    }

    private Location toWorld(Location location, World world) {
        if (location == null || world == null) return location;
        return new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    private DuelGateRegion toGateWorld(DuelGateRegion region, World world) {
        return region == null || world == null ? region : region.inWorld(world);
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
        player.teleport(new Location(editor, 0.5, 64.0, 0.5, 0.0f, 0.0f));
        player.setGameMode(GameMode.CREATIVE);
        player.setFlying(false);
        player.setAllowFlight(true);
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
                maps.put(id, new DuelMap(id, id, true, player.getWorld().getName(), player.getLocation(), player.getLocation(), player.getLocation(), player.getLocation(), player.getLocation(), null, null, null, null, null, null));
                saveMaps();
                selectMapEditor(player, id);
                Text.send(player, "<green>Map created: " + id + "</green>");
            }
            case "select" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map select <id></red>"); return; } selectMapEditor(player, context.arg(2).toLowerCase(Locale.ROOT)); }
            case "delete" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map delete <id></red>"); return; } deleteMap(player, context.arg(2).toLowerCase(Locale.ROOT)); }
            case "editor" -> { openMapEditor(player); Text.send(player, "<gray>Select a map with /duel map select <id>, place armor stand markers, then save.</gray>"); }
            case "marker" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map marker <lobby|red-spawn|blue-spawn|ffa-spawn|red-gate-out|blue-gate-out|spectator></red>"); return; } spawnEditorMarker(player, context.arg(2).toLowerCase(Locale.ROOT)); }
            case "savemarkers" -> { if (context.args().length < 3) { Text.send(player, "<red>Usage: /duel map savemarkers <mapId></red>"); return; } saveMarkers(player, context.arg(2).toLowerCase(Locale.ROOT)); }
            case "setlobby", "setspawna", "setspawnb", "setffa", "setffaspawn", "setspec" -> {
                String id = context.arg(2).toLowerCase(Locale.ROOT);
                DuelMap map = maps.get(id);
                if (map == null) { Text.send(player, "<red>Map not found.</red>"); return; }
                DuelMap updated = switch (sub) {
                    case "setlobby" -> map.withLobby(player.getLocation());
                    case "setspawna" -> map.withSpawnA(player.getLocation());
                    case "setspawnb" -> map.withSpawnB(player.getLocation());
                    case "setffa", "setffaspawn" -> map.withFfaSpawn(player.getLocation());
                    default -> map.withSpectator(player.getLocation());
                };
                maps.put(id, updated);
                saveMaps();
                Text.send(player, "<green>Updated and saved " + sub + " for " + id + "</green>");
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
            default -> Text.send(player, "<gray>Use: create, setlobby, setspawna, setspawnb, setffa, setspec, save, list, enable, disable</gray>");
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
        java.io.File file = new java.io.File(plugin.getDataFolder(), "duels/maps.yml");
        backupMapsFile(file.toPath());
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        Map<String, Object> icons = preservedMapValues(yaml, "icon");
        yaml.set("maps", null);
        for (var entry : maps.entrySet()) {
            DuelMap map = entry.getValue();
            String path = "maps." + entry.getKey();
            yaml.set(path + ".display-name", map.displayName());
            yaml.set(path + ".enabled", map.enabled());
            if (icons.containsKey(entry.getKey().toLowerCase(Locale.ROOT))) yaml.set(path + ".icon", icons.get(entry.getKey().toLowerCase(Locale.ROOT)));
            yaml.set(path + ".world", map.worldName());
            writeLocation(yaml, path + ".lobby", map.lobby());
            writeLocation(yaml, path + ".spawn-a", map.spawnA());
            writeLocation(yaml, path + ".spawn-b", map.spawnB());
            writeLocation(yaml, path + ".ffa-spawn", map.ffaSpawn());
            writeLocation(yaml, path + ".spectator", map.spectator());
            writeGate(yaml, path + ".gates.red", map.redGate());
            writeGate(yaml, path + ".gates.blue", map.blueGate());
            writeLocation(yaml, path + ".gate-exits.red", map.redGateExit());
            writeLocation(yaml, path + ".gate-exits.blue", map.blueGateExit());
            writeGate(yaml, path + ".gate-zones.red", map.redGateCloseZone());
            writeGate(yaml, path + ".gate-zones.blue", map.blueGateCloseZone());
        }
        try {
            yaml.save(file);
            configs.reload("duels/maps.yml");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save duel maps", e);
        }
    }

    private Map<String, Object> preservedMapValues(org.bukkit.configuration.file.YamlConfiguration yaml, String key) {
        Map<String, Object> values = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("maps");
        if (section == null) return values;
        for (String id : section.getKeys(false)) {
            Object value = section.get(id + "." + key);
            if (value != null) values.put(id.toLowerCase(Locale.ROOT), value);
        }
        return values;
    }

    private void backupMapsFile(Path source) {
        if (source == null || !Files.exists(source)) return;
        try {
            Path parent = source.getParent();
            if (parent == null) return;
            Files.createDirectories(parent);
            Path latestBackup = parent.resolve("maps.yml.backup");
            Files.copy(source, latestBackup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            Path backupDir = parent.resolve("backups");
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            Files.copy(source, backupDir.resolve("maps-" + stamp + ".yml"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create duel maps backup before saving: " + ex.getMessage());
        }
    }

    private void writeGate(org.bukkit.configuration.file.YamlConfiguration yaml, String path, DuelGateRegion gate) {
        if (gate == null) return;
        yaml.set(path + ".world", gate.worldName());
        yaml.set(path + ".pos1", List.of(gate.minX(), gate.minY(), gate.minZ()));
        yaml.set(path + ".pos2", List.of(gate.maxX(), gate.maxY(), gate.maxZ()));
        yaml.set(path + ".min", List.of(gate.minX(), gate.minY(), gate.minZ()));
        yaml.set(path + ".max", List.of(gate.maxX(), gate.maxY(), gate.maxZ()));
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
    private void startHudTask() {
        if (hudTask != null) hudTask.cancel();
        long interval = Math.max(1L, configs.get("duels/duels.yml").getLong("duels.ui.scoreboard.update-ticks", 20L));
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!queueUnits.isEmpty()) tryMatchAll();
            updateSpectatorHud();
            updateDuelScoreboards();
            if (queueUnits.isEmpty()) return;
            for (Player player : Bukkit.getOnlinePlayers()) giveQueueItem(player);
            long now = System.currentTimeMillis();
            for (QueueUnit unit : new HashSet<>(queueUnits.values())) {
                String elapsed = formatDuration(now - unit.joinedAt());
                String kitName = kits.containsKey(unit.kitId().toLowerCase(Locale.ROOT)) ? kits.get(unit.kitId().toLowerCase(Locale.ROOT)).displayName() : unit.kitId();
                String modeLabel = unit.ranked() ? "Ranked " + modeLabel(unit.mode()) : modeLabel(unit.mode());
                if (unit.ranked()) {
                    int range = rankedSearchRange(unit);
                    Integer last = queueSearchRangeNotices.get(unit.unitId());
                    if (last == null || range > last) notifyRankedSearch(unit, range, last != null);
                }
                String msg = "<gradient:#60a5fa:#c084fc>Queued " + modeLabel + "</gradient> <gray>" + kitName + "</gray> <dark_gray>|</dark_gray> <white>" + elapsed + "</white>";
                if (unit.ranked()) msg += " <dark_gray>|</dark_gray> <yellow>+/-" + rankedSearchRange(unit) + " MMR</yellow>";
                actionBarMembers(unit.members(), msg);
            }
        }, 20L, interval);
    }

    private void tryMatchAll() {
        matchQueues(DuelMode.SOLO);
        matchQueues(DuelMode.PARTY);
    }

    private void matchQueues(DuelMode mode) {
        purgeInvalidQueueUnits();
        Map<String, List<QueueUnit>> grouped = new HashMap<>();
        for (QueueUnit unit : queueUnits.values()) {
            if (unit.mode() == mode) grouped.computeIfAbsent(unit.kitId().toLowerCase(Locale.ROOT) + ":" + unit.ranked(), ignored -> new ArrayList<>()).add(unit);
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
        return new QueueUnit(UUID.randomUUID(), DuelMode.PARTY, first.kitId(), first.ranked() && second.ranked(), members, Math.min(first.joinedAt(), second.joinedAt()));
    }

    private QueueUnit selectBestUnit(List<QueueUnit> units) {
        return units.stream().min(Comparator.comparingLong(QueueUnit::joinedAt)).orElse(null);
    }

    private QueueUnit selectMatchFor(List<QueueUnit> units, QueueUnit anchor) {
        if (units.isEmpty()) return null;
        int anchorRating = unitRating(anchor);
        int anchorRange = anchor.ranked() ? rankedSearchRange(anchor) : Integer.MAX_VALUE;
        List<QueueUnit> shuffled = new ArrayList<>(units);
        java.util.Collections.shuffle(shuffled);
        QueueUnit best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (QueueUnit candidate : shuffled) {
            if (candidate.ranked() != anchor.ranked()) continue;
            int diff = Math.abs(anchorRating - unitRating(candidate));
            if (anchor.ranked() && (diff > anchorRange || diff > rankedSearchRange(candidate))) continue;
            if (recentlyMatched(anchor, candidate)) diff += 200;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = candidate;
            } else if (diff == bestDiff && ThreadLocalRandom.current().nextBoolean()) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean recentlyMatched(QueueUnit first, QueueUnit second) {
        if (first == null || second == null) return false;
        for (UUID firstMember : first.members()) {
            UUID recent = recentOpponents.get(firstMember);
            if (recent == null) continue;
            for (UUID secondMember : second.members()) {
                if (recent.equals(secondMember)) return true;
            }
        }
        for (UUID secondMember : second.members()) {
            UUID recent = recentOpponents.get(secondMember);
            if (recent == null) continue;
            for (UUID firstMember : first.members()) {
                if (recent.equals(firstMember)) return true;
            }
        }
        return false;
    }

    private int unitRating(QueueUnit unit) {
        if (unit.mode() == DuelMode.SOLO) return ratingOf(unit.members().iterator().next(), unit.kitId(), unit.ranked());
        int total = 0;
        for (UUID member : unit.members()) total += ratingOf(member, unit.kitId(), unit.ranked());
        return unit.members().isEmpty() ? 1000 : total / unit.members().size();
    }

    private int ratingOf(UUID uuid, String kitId, boolean ranked) {
        return ranked ? rankedService.mmr(uuid, kitId) : Math.max(1000, repository.load(uuid).duelRating());
    }

    private int rankedSearchRange(QueueUnit unit) {
        int initial = Math.max(0, configs.get("duels/duels.yml").getInt("duels.ranked.matchmaking.initial-range", 100));
        int step = Math.max(1, configs.get("duels/duels.yml").getInt("duels.ranked.matchmaking.range-step", 100));
        int max = Math.max(initial, configs.get("duels/duels.yml").getInt("duels.ranked.matchmaking.max-range", 600));
        long intervalMs = Math.max(1, configs.get("duels/duels.yml").getInt("duels.ranked.matchmaking.expand-interval-seconds", 10)) * 1000L;
        long steps = Math.max(0L, (System.currentTimeMillis() - unit.joinedAt()) / intervalMs);
        return Math.min(max, initial + (int) steps * step);
    }

    private void notifyRankedSearch(QueueUnit unit, int range, boolean expanded) {
        queueSearchRangeNotices.put(unit.unitId(), range);
        String key = expanded ? "ranked-search-expanded" : "ranked-search";
        String fallback = expanded
                ? "<gradient:#00FBFF:#A855F7>Search expanded</gradient> <gray>to</gray> <yellow>+/-{range} MMR</yellow><gray>...</gray>"
                : "<gradient:#00FBFF:#A855F7>Searching</gradient> <gray>for opponent within</gray> <yellow>+/-{range} MMR</yellow><gray>...</gray>";
        String message = duelMessage(key, fallback).replace("{range}", String.valueOf(range));
        notifyMembers(unit.members(), message);
        actionBarMembers(unit.members(), message);
    }

    private boolean canQueueMembers(Player requester, Collection<UUID> members) {
        for (UUID uuid : members) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null || !member.isOnline()) {
                Text.send(requester, "<red>All duel members must be online.</red>");
                return false;
            }
            if (matchesByPlayer.containsKey(uuid) || ACTIVE_DUEL_PLAYERS.contains(uuid) || preparingDuelPlayers.contains(uuid)) {
                Text.send(requester, "<red>" + member.getName() + " is already in a duel.</red>");
                return false;
            }
            if (!canUseInWorld(member)) {
                Text.send(requester, "<red>" + member.getName() + " is in a world where duels are disabled.</red>");
                return false;
            }
        }
        return true;
    }

    private void purgeInvalidQueueUnits() {
        for (QueueUnit unit : new ArrayList<>(queueUnits.values())) {
            boolean invalid = false;
            for (UUID member : unit.members()) {
                Player player = Bukkit.getPlayer(member);
                if (player == null || !player.isOnline() || matchesByPlayer.containsKey(member) || ACTIVE_DUEL_PLAYERS.contains(member) || preparingDuelPlayers.contains(member)) {
                    invalid = true;
                    break;
                }
            }
            if (invalid) removeQueueUnit(unit, false);
        }
    }

    private void handleDuelEditor(Player player, CommandContext context) {
        if (!player.hasPermission("3smpcore.duel.editor") && !player.hasPermission("3smpcore.duel.admin")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        String action = context.arg(1).toLowerCase(Locale.ROOT);
        switch (action) {
            case "wand" -> {
                String team = context.arg(2).toLowerCase(Locale.ROOT);
                if (team.equals("red")) {
                    giveGateWand(player, GateEditorTarget.RED_GATE);
                    Text.send(player, "<green>Red Gate Wand given.</green> <gray>Left pos1, right pos2, sneak-click clears red.</gray>");
                } else if (team.equals("blue")) {
                    giveGateWand(player, GateEditorTarget.BLUE_GATE);
                    Text.send(player, "<green>Blue Gate Wand given.</green> <gray>Left pos1, right pos2, sneak-click clears blue.</gray>");
                } else if (team.equals("red-zone") || team.equals("redzone")) {
                    giveGateWand(player, GateEditorTarget.RED_CLOSE_ZONE);
                    Text.send(player, "<green>Red Gate Zone Wand given.</green> <gray>Players in this zone get moved to Red Gate Outside TP before close.</gray>");
                } else if (team.equals("blue-zone") || team.equals("bluezone")) {
                    giveGateWand(player, GateEditorTarget.BLUE_CLOSE_ZONE);
                    Text.send(player, "<green>Blue Gate Zone Wand given.</green> <gray>Players in this zone get moved to Blue Gate Outside TP before close.</gray>");
                } else {
                    giveGateWand(player, GateEditorTarget.RED_GATE);
                    giveGateWand(player, GateEditorTarget.BLUE_GATE);
                    giveGateWand(player, GateEditorTarget.RED_CLOSE_ZONE);
                    giveGateWand(player, GateEditorTarget.BLUE_CLOSE_ZONE);
                    Text.send(player, "<green>Duel gate wands given.</green> <gray>Use /3smpcore duel editor wand red|blue|red-zone|blue-zone for one wand.</gray>");
                }
            }
            case "gate" -> {
                String team = context.arg(2).toLowerCase(Locale.ROOT);
                if (!team.equals("red") && !team.equals("blue")) {
                    Text.send(player, "<yellow>/3smpcore duel editor gate <red|blue></yellow>");
                    return;
                }
                saveSelectedGate(player, team.equals("red") ? GateEditorTarget.RED_GATE : GateEditorTarget.BLUE_GATE);
            }
            case "zone" -> {
                String team = context.arg(2).toLowerCase(Locale.ROOT);
                if (!team.equals("red") && !team.equals("blue")) {
                    Text.send(player, "<yellow>/3smpcore duel editor zone <red|blue></yellow>");
                    return;
                }
                saveSelectedZone(player, team.equals("red") ? GateEditorTarget.RED_CLOSE_ZONE : GateEditorTarget.BLUE_CLOSE_ZONE);
            }
            case "preview" -> {
                String id = selectedEditorMap.get(player.getUniqueId());
                DuelMap map = id == null ? null : maps.get(id);
                if (map == null) {
                    Text.send(player, "<red>Select an arena first.</red>");
                    return;
                }
                gateService.previewGates(player, map);
                Text.send(player, "<gray>Previewing duel gates.</gray>");
            }
            case "marker" -> {
                String marker = context.arg(2).toLowerCase(Locale.ROOT);
                if (marker.isBlank()) {
                    Text.send(player, "<yellow>/3smpcore duel editor marker <red-spawn|blue-spawn|ffa-spawn|red-gate-out|blue-gate-out|lobby|spectator></yellow>");
                    return;
                }
                spawnEditorMarker(player, marker);
            }
            case "clear" -> {
                if (context.arg(2).equalsIgnoreCase("gate")) {
                    clearGate(player, context.arg(3).toLowerCase(Locale.ROOT));
                    return;
                }
                if (context.arg(2).equalsIgnoreCase("zone")) {
                    clearGateZone(player, context.arg(3).toLowerCase(Locale.ROOT));
                    return;
                }
                Text.send(player, "<yellow>/3smpcore duel editor clear <gate|zone> <red|blue></yellow>");
            }
            case "save" -> saveArenaCommand(player);
            default -> Text.send(player, "<yellow>/3smpcore duel editor wand red|blue|red-zone|blue-zone, gate red|blue, zone red|blue, preview gates, clear gate|zone red|blue, save</yellow>");
        }
    }

    private void startMatch(QueueUnit first, QueueUnit second) {
        DuelMap map = pickMap();
        startMatch(first, second, map);
    }

    private void startMatch(QueueUnit first, QueueUnit second, String mapId, int roundsToWin) {
        DuelMap map = mapId == null || mapId.isBlank() ? pickMap() : maps.get(mapId.toLowerCase(Locale.ROOT));
        startMatch(first, second, map, roundsToWin);
    }

    private void startMatch(QueueUnit first, QueueUnit second, DuelMap map) {
        startMatch(first, second, map, kitRounds(first.kitId()));
    }

    private void startMatch(QueueUnit first, QueueUnit second, DuelMap map, int roundsToWin) {
        startMatch(first, second, map, roundsToWin, false);
    }

    private void startMatch(QueueUnit first, QueueUnit second, DuelMap map, int roundsToWin, boolean testMode) {
        if (map == null || map.spawnA() == null || map.spawnB() == null) {
            notifyMembers(first.members(), "<red>No valid duel map configured.</red>");
            notifyMembers(second.members(), "<red>No valid duel map configured.</red>");
            removeQueueUnit(first, false);
            removeQueueUnit(second, false);
            return;
        }
        List<String> gateFailures = gateService.validate(map);
        if (!gateFailures.isEmpty() && !testMode) {
            notifyMembers(first.members(), "<red>This duel arena is not ready:</red> <gray>" + String.join(", ", gateFailures) + "</gray>");
            notifyMembers(second.members(), "<red>This duel arena is not ready:</red> <gray>" + String.join(", ", gateFailures) + "</gray>");
            removeQueueUnit(first, false);
            removeQueueUnit(second, false);
            return;
        }
        if (!gateFailures.isEmpty()) {
            notifyMembers(first.members(), "<yellow>Test duel starting with gate warnings:</yellow> <gray>" + String.join(", ", gateFailures) + "</gray>");
            notifyMembers(second.members(), "<yellow>Test duel starting with gate warnings:</yellow> <gray>" + String.join(", ", gateFailures) + "</gray>");
        }
        UUID matchId = UUID.randomUUID();
        removeQueueUnit(first, false);
        removeQueueUnit(second, false);
        preparingDuelPlayers.addAll(first.members());
        preparingDuelPlayers.addAll(second.members());
        notifyMembers(first.members(), "<gray>Preparing duel arena...</gray>");
        notifyMembers(second.members(), "<gray>Preparing duel arena...</gray>");
        worldService.createAsync(map, matchId, instanced -> finishStartMatch(first, second, instanced, matchId, roundsToWin));
    }

    private void finishStartMatch(QueueUnit first, QueueUnit second, DuelWorldService.InstancedMap instanced, UUID matchId, int roundsToWin) {
        Set<UUID> offlineDuringPrepare = new LinkedHashSet<>();
        for (UUID uuid : first.members()) if (Bukkit.getPlayer(uuid) == null) offlineDuringPrepare.add(uuid);
        for (UUID uuid : second.members()) if (Bukkit.getPlayer(uuid) == null) offlineDuringPrepare.add(uuid);
        if (!offlineDuringPrepare.isEmpty()) {
            preparingDuelPlayers.removeAll(first.members());
            preparingDuelPlayers.removeAll(second.members());
            notifyMembers(first.members(), "<red>Duel cancelled because a player went offline while the arena was preparing.</red>");
            notifyMembers(second.members(), "<red>Duel cancelled because a player went offline while the arena was preparing.</red>");
            if (first.ranked() || second.ranked()) {
                rankedValidationService.recordQueueDodge(offlineDuringPrepare);
            }
            removeQueueUnit(first, false);
            removeQueueUnit(second, false);
            if (instanced.world() != null) worldService.cleanup(instanced.world());
            return;
        }
        DuelMap activeMap = instanced.map();
        if (activeMap == null || activeMap.spawnA() == null || activeMap.spawnB() == null) {
            preparingDuelPlayers.removeAll(first.members());
            preparingDuelPlayers.removeAll(second.members());
            notifyMembers(first.members(), "<red>Could not prepare duel arena.</red>");
            notifyMembers(second.members(), "<red>Could not prepare duel arena.</red>");
            removeQueueUnit(first, false);
            removeQueueUnit(second, false);
            if (instanced.world() != null) worldService.cleanup(instanced.world());
            return;
        }
        worldService.ensureCombatWorld(activeMap);
        DuelKit kit = kits.get(first.kitId().toLowerCase(Locale.ROOT));
        roundsToWin = Math.max(1, roundsToWin);
        DuelMatch match = new DuelMatch(matchId, first.mode(), first.kitId(), activeMap.id(), first.members(), second.members(), System.currentTimeMillis(), roundsToWin, first.ranked() || second.ranked());
        match.resetRoundState();
        matchStatsService.start(match);
        if (instanced.world() != null) instanceWorldsByMatch.put(matchId, instanced.world());
        activeMapsByMatch.put(matchId, activeMap);
        purgePlayersFromQueues(first.members());
        purgePlayersFromQueues(second.members());
        first.members().forEach(member -> { matchesByPlayer.put(member, match); ACTIVE_DUEL_PLAYERS.add(member); });
        second.members().forEach(member -> { matchesByPlayer.put(member, match); ACTIVE_DUEL_PLAYERS.add(member); });
        preparingDuelPlayers.removeAll(first.members());
        preparingDuelPlayers.removeAll(second.members());
        removeQueueUnit(first, false);
        removeQueueUnit(second, false);
        rememberRecentOpponents(first.members(), second.members());
        notifyMembers(match.teamOne(), "<green>Match found. Starting on " + activeMap.displayName() + "</green>");
        notifyMembers(match.teamTwo(), "<green>Match found. Starting on " + activeMap.displayName() + "</green>");
        applyMatchFoundEffects(match.teamOne());
        applyMatchFoundEffects(match.teamTwo());
        applyScoreboardTeams(match);
        applyHealthIndicator(match);
        normalizeMatchVisibility(match);
        prepareMatch(match, activeMap);
        startArenaCountdown(match, activeMap);
    }

    private void startArenaCountdown(DuelMatch match, DuelMap map) {
        int seconds = Math.max(1, configs.get("duels/duels.yml").getInt("duels.countdown.seconds", 5));
        BukkitTask task = new BukkitRunnable() {
            int remaining = seconds;
            @Override public void run() {
                if (!isMatchActive(match) || remaining < 0) {
                    countdownTasksByMatch.remove(match.id());
                    cancel();
                    return;
                }
                if (remaining == 0) {
                    beginFight(match, map);
                    countdownTasksByMatch.remove(match.id());
                    cancel();
                    return;
                }
                titleMembers(match.teamOne(), "<gradient:#60a5fa:#c084fc>" + remaining + "</gradient>", "<gray>Get ready</gray>");
                titleMembers(match.teamTwo(), "<gradient:#60a5fa:#c084fc>" + remaining + "</gradient>", "<gray>Get ready</gray>");
                sendScoreDisplay(match);
                soundMembers(match.teamOne(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.25f);
                soundMembers(match.teamTwo(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.25f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        countdownTasksByMatch.put(match.id(), task);
    }

    private boolean isMatchActive(DuelMatch match) {
        if (match == null) return false;
        for (UUID uuid : match.teamOne()) if (!matchesByPlayer.containsKey(uuid)) return false;
        for (UUID uuid : match.teamTwo()) if (!matchesByPlayer.containsKey(uuid)) return false;
        return true;
    }

    private void prepareMatch(DuelMatch match, DuelMap map) {
        if (match.mode() != DuelMode.FFA) gateService.closeGates(match, map);
        frozenDuelPlayers.addAll(match.teamOne());
        frozenDuelPlayers.addAll(match.teamTwo());
        if (match.mode() == DuelMode.FFA) {
            applyFfaPlayers(match, map);
        } else {
            applyTeam(match.teamOne(), map.spawnA(), match.kitId());
            applyTeam(match.teamTwo(), map.spawnB(), match.kitId());
        }
        titleMembers(match.teamOne(), "<gradient:#60a5fa:#c084fc>Get Ready</gradient>", "<gray>You can arrange your hotbar.</gray>");
        titleMembers(match.teamTwo(), "<gradient:#60a5fa:#c084fc>Get Ready</gradient>", "<gray>You can arrange your hotbar.</gray>");
    }

    private void applyFfaPlayers(DuelMatch match, DuelMap map) {
        List<Location> spawns = ffaSpawnLocations(map);
        int index = 0;
        for (UUID uuid : matchMembers(match)) {
            applyTeam(Set.of(uuid), spawns.get(index++ % spawns.size()), match.kitId());
        }
    }

    private List<Location> ffaSpawnLocations(DuelMap map) {
        List<Location> spawns = new ArrayList<>();
        Location center = map.ffaSpawn() != null ? map.ffaSpawn() : map.lobby();
        if (center == null || center.getWorld() == null) {
            return List.of(new Location(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0), 0, 80, 0));
        }
        spawns.add(center.clone());
        double radius = Math.max(0.0D, configs.get("duels/duels.yml").getDouble("duels.ffa.spawn-spread-radius", 2.5D));
        if (radius <= 0.0D) return spawns;
        int count = Math.max(2, configs.get("duels/duels.yml").getInt("duels.ffa.spawn-spread-points", 12));
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * i) / count;
            Location spawn = center.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            float yawToCenter = (float) Math.toDegrees(Math.atan2(center.getZ() - spawn.getZ(), center.getX() - spawn.getX())) - 90.0F;
            spawn.setYaw(yawToCenter);
            spawn.setPitch(center.getPitch());
            spawns.add(spawn);
        }
        return spawns;
    }

    private void beginFight(DuelMatch match, DuelMap map) {
        if (!isMatchActive(match)) return;
        closeLoadoutEditors(match.teamOne());
        closeLoadoutEditors(match.teamTwo());
        for (UUID uuid : match.teamOne()) removeLoadoutEditorItem(Bukkit.getPlayer(uuid));
        for (UUID uuid : match.teamTwo()) removeLoadoutEditorItem(Bukkit.getPlayer(uuid));
        frozenDuelPlayers.removeAll(match.teamOne());
        frozenDuelPlayers.removeAll(match.teamTwo());
        setTemporaryDuelFlight(match.teamOne(), false);
        setTemporaryDuelFlight(match.teamTwo(), false);
        clearMatchFoundEffects(match.teamOne());
        clearMatchFoundEffects(match.teamTwo());
        notifyMembers(match.teamOne(), "<green>Fight!</green>");
        notifyMembers(match.teamTwo(), "<green>Fight!</green>");
        titleMembers(match.teamOne(), "<gradient:#34d399:#22c55e>START!</gradient>", "<gray>Good luck.</gray>");
        titleMembers(match.teamTwo(), "<gradient:#34d399:#22c55e>START!</gradient>", "<gray>Good luck.</gray>");
        if (match.mode() != DuelMode.FFA) gateService.openGates(match);
        soundMembers(match.teamOne(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        soundMembers(match.teamTwo(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        autoSplashTeam(match.teamOne(), match.kitId());
        autoSplashTeam(match.teamTwo(), match.kitId());
    }

    private void applyMatchFoundEffects(Collection<UUID> members) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false, false));
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 0.7f);
            Text.actionBar(player, "<gray>Match found. Preparing arena...</gray>");
        }
    }

    private void clearMatchFoundEffects(Collection<UUID> members) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
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
        for (org.bukkit.potion.PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        applyDuelFoodState(player);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
    }

    private void applyDuelFoodState(Player player) {
        player.setFoodLevel(duelFoodLevel());
        player.setSaturation(duelStartingSaturation());
        player.setExhaustion(0.0f);
    }

    private int duelFoodLevel() {
        return Math.max(1, Math.min(20, configs.get("duels/duels.yml").getInt("duels.pvp.start-food-level", 20)));
    }

    private float duelStartingSaturation() {
        return Math.max(0.0f, Math.min(20.0f, (float) configs.get("duels/duels.yml").getDouble("duels.pvp.start-saturation", 0.0D)));
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
            loadoutButtonDisplacements.remove(uuid);
            normalizeDuelPlayerState(player);
            player.setGameMode(GameMode.SURVIVAL);
            snapshots.putIfAbsent(uuid, Snapshot.capture(player));
            resetDuelState(player);
            stripHubItems(player);
            if (kit != null) applyKit(player, kit);
            player.setAllowFlight(true);
            player.setFlying(false);
            worldService.keepPlayerAreaLoaded(player);
            player.teleport(spawn);
            worldService.keepPlayerAreaLoaded(player);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            player.setFallDistance(0.0f);
            player.setHealth(20.0);
            applyDuelFoodState(player);
            player.setFireTicks(0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !matchesByPlayer.containsKey(uuid)) return;
                loadoutButtonDisplacements.remove(uuid);
                stripHubItems(online);
                if (kit != null) applyKit(online, kit);
                online.updateInventory();
            }, 1L);
        }
    }


    private boolean recoverStaleDuelPlayer(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (matchesByPlayer.containsKey(uuid)) return false;
        boolean stale = ACTIVE_DUEL_PLAYERS.contains(uuid) || frozenDuelPlayers.contains(uuid) || isTemporaryDuelWorld(player.getWorld());
        if (!stale) {
            clearHealthIndicatorEntry(player);
            return false;
        }
        ACTIVE_DUEL_PLAYERS.remove(uuid);
        frozenDuelPlayers.remove(uuid);
        preparingDuelPlayers.remove(uuid);
        loadoutButtonDisplacements.remove(uuid);
        snapshots.remove(uuid);
        purgePlayersFromQueues(Set.of(uuid));
        clearHealthIndicatorEntry(player);
        returnPlayerToSpawnAfterDuel(player, spawnLocation());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            returnPlayerToSpawnAfterDuel(player, spawnLocation());
            refreshPostMatchItems(player);
        }, 2L);
        return true;
    }

    private void refreshPostMatchItems(Player player) {
        if (player == null || !player.isOnline()) return;
        reloadItems(player);
        partyService.givePartyItems(player);
        for (Consumer<Player> refresher : postMatchItemRefreshers) refresher.accept(player);
        player.updateInventory();
    }

    private void returnPlayerToSpawnAfterDuel(Player player, Location spawn) {
        if (player == null) return;
        if (player.isDead()) {
            try { player.spigot().respawn(); } catch (Exception ignored) {}
        }
        clearHealthIndicatorEntry(player);
        normalizeDuelPlayerState(player);
        player.setGameMode(GameMode.SURVIVAL);
        stripHubItems(player);
        if (spawn != null && spawn.getWorld() != null) {
            try { player.teleport(spawn); } catch (Exception ignored) {}
        }
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) player.setHealth(Math.max(1.0, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.setFallDistance(0.0f);
        player.setItemOnCursor(null);
        if (player.isOnline()) player.updateInventory();
    }

    private void stripHubItems(Player player) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
    }

    private void normalizeDuelPlayerState(Player player) {
        try {
            if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        } catch (IllegalArgumentException ignored) {
        }
        player.setInvisible(false);
        player.setGlowing(false);
        player.setCollidable(true);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
            player.showPlayer(plugin, other);
        }
    }

    private void setTemporaryDuelFlight(Collection<UUID> members, boolean enabled) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.setFlying(false);
            player.setAllowFlight(enabled);
        }
    }

    private long nextRoundDelayTicks() {
        return Math.max(0L, configs.get("duels/duels.yml").getLong("duels.rounds.next-round-delay-ticks", 40L));
    }

    private void normalizeMatchVisibility(DuelMatch match) {
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(match.teamOne());
        all.addAll(match.teamTwo());
        for (UUID uuid : all) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) normalizeDuelPlayerState(player);
        }
    }

    private void applyKit(Player player, DuelKit kit) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        for (String item : kit.contents()) addItem(player, item);
        for (int i = 0; i < kit.armor().size() && i < 4; i++) setArmor(player, i, kit.armor().get(i));
        if (!kit.offhand().isEmpty()) setOffhand(player, kit.offhand().get(0));
        applySavedLoadout(player, kit);
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
        if (item == null) return null;
        ItemStack copy = item.clone();
        if (item.getType().getMaxDurability() <= 0) return copy;
        if (configs.get("duels/duels.yml").getBoolean("duels.kit-editor.preserve-attribute-items", true) && hasAttributeModifiers(copy)) {
            return copy;
        }
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

    private boolean hasAttributeModifiers(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasAttributeModifiers();
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
        DuelMatchStats finalStats = matchStatsService.finish(match.id(), System.currentTimeMillis());
        RankedMatchValidationResult validation = finalStats == null ? (match.ranked() ? new RankedMatchValidationResult(true, false, List.of("match already finalized")) : RankedMatchValidationResult.unranked()) : rankedValidationService.validate(match, winners, reason, finalStats);
        Map<UUID, DuelRankedUpdate> rankedUpdates = finalStats == null ? Map.of() : rankedService.recordMatch(match, winners, validation.valid(), validation.reasons());
        if (finalStats != null) {
            rankedValidationService.recordCompleted(match, winners, reason);
            sendPostMatchStats(match, winners, finalStats, rankedUpdates, validation, reason);
        }
        try {
            BukkitTask countdown = countdownTasksByMatch.remove(match.id());
            if (countdown != null) countdown.cancel();
            for (UUID uuid : all) {
                matchesByPlayer.remove(uuid);
                ACTIVE_DUEL_PLAYERS.remove(uuid);
                frozenDuelPlayers.remove(uuid);
                preparingDuelPlayers.remove(uuid);
                loadoutButtonDisplacements.remove(uuid);
                purgePlayersFromQueues(Set.of(uuid));
                snapshots.remove(uuid);
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                returnPlayerToSpawnAfterDuel(player, spawn);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    returnPlayerToSpawnAfterDuel(player, spawn);
                    refreshPostMatchItems(player);
                }, 2L);
                boolean win = winners.contains(uuid);
                Text.send(player, win ? "<green>You won the duel.</green> <gray>Winner: <white>" + winnerNames + "</white></gray>" : "<gray>Duel ended: " + reason + ". Winner: <white>" + winnerNames + "</white></gray>");
            }
        } finally {
            BukkitTask countdown = countdownTasksByMatch.remove(match.id());
            if (countdown != null) countdown.cancel();
            gateService.resetGates(match);
            removeScoreboardTeams(match.id());
            clearHealthIndicator(match);
            stopSpectatorsForMatch(match.id());
            for (UUID uuid : all) {
                matchesByPlayer.remove(uuid);
                ACTIVE_DUEL_PLAYERS.remove(uuid);
                frozenDuelPlayers.remove(uuid);
                preparingDuelPlayers.remove(uuid);
                loadoutButtonDisplacements.remove(uuid);
                snapshots.remove(uuid);
            }
            org.bukkit.World instance = instanceWorldsByMatch.remove(match.id());
            activeMapsByMatch.remove(match.id());
            clearPlacedBlocks(instance);
            clearResetMaterials(instance);
            if (instance != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    worldService.cleanup(instance);
                }, 40L);
            }
            endingMatches.remove(match.id());
        }
    }

    private void stopSpectatorsForMatch(UUID matchId) {
        for (Map.Entry<UUID, SpectatorSnapshot> entry : new ArrayList<>(spectatorSnapshots.entrySet())) {
            if (!entry.getValue().matchId().equals(matchId)) continue;
            Player spectator = Bukkit.getPlayer(entry.getKey());
            if (spectator != null && spectator.isOnline()) stopSpectating(spectator);
            else spectatorSnapshots.remove(entry.getKey());
        }
    }

    private void sendPostMatchStats(DuelMatch match, Set<UUID> winners, DuelMatchStats stats, Map<UUID, DuelRankedUpdate> rankedUpdates, RankedMatchValidationResult validation, String reason) {
        if (match == null) return;
        Set<UUID> all = matchMembers(match);
        String winnerNames = names(winners);
        Set<UUID> losers = new LinkedHashSet<>(all);
        losers.removeAll(winners);
        String loserNames = names(losers);
        String duration = formatDuration(stats == null ? System.currentTimeMillis() - match.startedAt() : stats.durationMillis());
        for (UUID uuid : all) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            PlayerDuelStats self = stats == null ? null : stats.player(uuid);
            UUID opponent = firstOpponent(match, uuid);
            PlayerDuelStats opponentStats = opponent == null || stats == null ? null : stats.player(opponent);
            DuelRankedUpdate update = rankedUpdates.get(uuid);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("winner", winnerNames);
            placeholders.put("loser", loserNames);
            placeholders.put("opponent", opponent == null ? "none" : names(Set.of(opponent)));
            placeholders.put("kit", kitDisplay(match.kitId()));
            placeholders.put("arena", mapDisplay(match.mapId()));
            placeholders.put("duration", duration);
            placeholders.put("reason", reason == null ? "complete" : reason);
            placeholders.put("damage_dealt", statDouble(self == null ? 0.0D : self.damageDealt()));
            placeholders.put("damage_taken", statDouble(self == null ? 0.0D : self.damageTaken()));
            placeholders.put("hits_landed", String.valueOf(self == null ? 0 : self.hitsLanded()));
            placeholders.put("hits_received", String.valueOf(self == null ? 0 : self.hitsReceived()));
            placeholders.put("projectiles_hit", String.valueOf(self == null ? 0 : self.projectilesHit()));
            placeholders.put("projectiles_launched", String.valueOf(self == null ? 0 : self.projectilesLaunched()));
            placeholders.put("potions_used", String.valueOf(self == null ? 0 : self.potionsUsed()));
            placeholders.put("golden_apples", String.valueOf(self == null ? 0 : self.goldenApples()));
            placeholders.put("totems_popped", String.valueOf(self == null ? 0 : self.totemsPopped()));
            placeholders.put("crystals_placed", String.valueOf(self == null ? 0 : self.crystalsPlaced()));
            placeholders.put("crystals_broken", String.valueOf(self == null ? 0 : self.crystalsBroken()));
            placeholders.put("accuracy", statDouble(self == null ? 0.0D : self.accuracyPercent()));
            placeholders.put("opponent_damage_dealt", statDouble(opponentStats == null ? 0.0D : opponentStats.damageDealt()));
            placeholders.put("opponent_hits", String.valueOf(opponentStats == null ? 0 : opponentStats.hitsLanded()));
            placeholders.put("old_mmr", update == null ? String.valueOf(rankedService.mmr(uuid, match.kitId())) : String.valueOf(update.oldMmr()));
            placeholders.put("new_mmr", update == null ? String.valueOf(rankedService.mmr(uuid, match.kitId())) : String.valueOf(update.newMmr()));
            placeholders.put("mmr_change", update == null ? "0" : update.signedChange());
            placeholders.put("old_rank", update == null ? rankedService.rankDisplay(rankedService.mmr(uuid, match.kitId())) : update.oldRankDisplay());
            placeholders.put("new_rank", update == null ? rankedService.rankDisplay(rankedService.mmr(uuid, match.kitId())) : update.newRankDisplay());
            placeholders.put("ranked_invalid_reasons", validation.reasons().isEmpty() ? "none" : String.join(", ", validation.reasons()));
            placeholders.put("rematch_command", opponent == null ? "/duel menu" : "/duel " + names(Set.of(opponent)));
            for (String line : duelMessageList("post-match.lines", defaultPostMatchLines())) Text.raw(player, replace(line, placeholders));
            if (match.ranked()) {
                List<String> rankedLines = validation.valid() ? duelMessageList("post-match.ranked-lines", defaultPostMatchRankedLines()) : duelMessageList("post-match.ranked-invalid-lines", defaultPostMatchInvalidLines());
                for (String line : rankedLines) Text.raw(player, replace(line, placeholders));
            }
            for (String line : duelMessageList("post-match.footer-lines", defaultPostMatchFooterLines())) Text.raw(player, replace(line, placeholders));
        }
    }

    private List<String> defaultPostMatchLines() {
        return List.of(
                "<gradient:#00FBFF:#A855F7><bold>DUEL COMPLETE</bold></gradient>",
                "<dark_gray>----------------------------</dark_gray>",
                "<gray>Winner: <green>{winner}</green>",
                "<gray>Loser: <red>{loser}</red>",
                "<gray>Kit: <aqua>{kit}</aqua> <dark_gray>|</dark_gray> <gray>Arena: <white>{arena}</white>",
                "<gray>Duration: <yellow>{duration}</yellow>",
                "",
                "<gradient:#22C55E:#84CC16>Your Stats</gradient>",
                "<gray>Damage Dealt: <green>{damage_dealt}</green> <dark_gray>|</dark_gray> <gray>Taken: <red>{damage_taken}</red>",
                "<gray>Hits: <yellow>{hits_landed}</yellow><dark_gray>/</dark_gray><yellow>{hits_received}</yellow> <dark_gray>|</dark_gray> <gray>Accuracy: <aqua>{accuracy}%</aqua>",
                "<gray>Projectiles: <aqua>{projectiles_hit}</aqua><dark_gray>/</dark_gray><aqua>{projectiles_launched}</aqua> <dark_gray>|</dark_gray> <gray>Potions: <light_purple>{potions_used}</light_purple>",
                "<gray>Gapples: <gradient:#f4cd2a:#eda323:#d28d0d>{golden_apples}</gradient> <dark_gray>|</dark_gray> <gray>Totems: <yellow>{totems_popped}</yellow> <dark_gray>|</dark_gray> <gray>Crystals: <red>{crystals_placed}</red><dark_gray>/</dark_gray><red>{crystals_broken}</red>"
        );
    }

    private List<String> defaultPostMatchRankedLines() {
        return List.of(
                "",
                "<gradient:#f4cd2a:#eda323:#d28d0d>Ranked</gradient>",
                "<gray>MMR: <yellow>{old_mmr}</yellow> <dark_gray>-></dark_gray> <green>{new_mmr}</green> <gray>({mmr_change})</gray>",
                "<gray>Rank: {old_rank} <dark_gray>-></dark_gray> {new_rank}"
        );
    }

    private List<String> defaultPostMatchInvalidLines() {
        return List.of(
                "",
                "<gradient:#f4cd2a:#eda323:#d28d0d>Ranked</gradient>",
                "<yellow>MMR was not changed.</yellow> <gray>Reason: {ranked_invalid_reasons}</gray>"
        );
    }

    private List<String> defaultPostMatchFooterLines() {
        return List.of(
                "<dark_gray>----------------------------</dark_gray>",
                "<gray><click:run_command:'{rematch_command}'><hover:show_text:'Click to open a rematch challenge'><gradient:#00FBFF:#A855F7>[Rematch]</gradient></hover></click>"
        );
    }

    private String replace(String input, Map<String, String> placeholders) {
        String out = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        return out;
    }

    private String statDouble(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String formatOneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String kitDisplay(String kitId) {
        DuelKit kit = kits.get(kitId == null ? "" : kitId.toLowerCase(Locale.ROOT));
        return kit == null ? String.valueOf(kitId) : kit.displayName();
    }

    private String mapDisplay(String mapId) {
        DuelMap map = maps.get(mapId == null ? "" : mapId.toLowerCase(Locale.ROOT));
        return map == null ? String.valueOf(mapId) : map.displayName();
    }

    private UUID firstOpponent(DuelMatch match, UUID uuid) {
        if (match == null || uuid == null) return null;
        Set<UUID> candidates = new LinkedHashSet<>();
        if (match.teamOne().contains(uuid)) candidates.addAll(match.teamTwo());
        else if (match.teamTwo().contains(uuid)) candidates.addAll(match.teamOne());
        else candidates.addAll(matchMembers(match));
        candidates.remove(uuid);
        return candidates.stream().findFirst().orElse(null);
    }

    private void announceLeave(Player player, boolean quit) {
        Set<UUID> others = new LinkedHashSet<>();
        DuelMatch match = matchesByPlayer.get(player.getUniqueId());
        if (match != null) {
            for (UUID uuid : match.teamOne()) if (!uuid.equals(player.getUniqueId())) others.add(uuid);
            for (UUID uuid : match.teamTwo()) if (!uuid.equals(player.getUniqueId())) others.add(uuid);
        }
        String title = quit ? "<red>Player disconnected</red>" : "<yellow>Player left the duel</yellow>";
        String subtitle = quit ? "<gray>The match will end now.</gray>" : "<gray>Returning everyone to spawn.</gray>";
        if (!others.isEmpty()) titleMembers(others, title, subtitle);
        if (!quit) titleMembers(Set.of(player.getUniqueId()), title, subtitle);
        Text.send(player, quit ? "<red>You disconnected from the duel.</red>" : "<yellow>You left the duel.</yellow>");
    }

    private Location spawnLocation() {
        String worldName = configs.get("core/config.yml").getString("spawn.world", "spawn");
        World world = Bukkit.getWorld(worldName);
        if (world == null) world = Bukkit.getWorld("world");
        if (world == null) world = Bukkit.getWorld("spawn");
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

    private void clearPlacedBlocks(World world) {
        if (world == null) return;
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (String key : placedMatchBlocks) {
            if (!key.startsWith(worldName + ":")) continue;
            String[] parts = key.split(":");
            if (parts.length == 4) {
                try {
                    world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])).setType(Material.AIR, false);
                } catch (Exception ignored) {}
            }
            toRemove.add(key);
        }
        placedMatchBlocks.removeAll(toRemove);
    }

    private void cleanupRoundArena(DuelMatch match) {
        World world = instanceWorldsByMatch.get(match.id());
        if (world == null) return;
        clearPlacedBlocks(world);
        clearResetMaterials(world);
        for (Entity entity : new java.util.ArrayList<>(world.getEntities())) {
            if (entity instanceof Player) continue;
            switch (entity.getType()) {
                case ITEM, ARROW, SPECTRAL_ARROW, TRIDENT, SPLASH_POTION, LINGERING_POTION, EXPERIENCE_ORB -> entity.remove();
                default -> { }
            }
        }
    }

    private boolean isLiveMatchWorld(World world) {
        if (world == null) return false;
        return instanceWorldsByMatch.values().stream().anyMatch(active -> active != null && active.getUID().equals(world.getUID()));
    }

    private void clearResetMaterials(World world) {
        if (world == null) return;
        for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                        if (ROUND_RESET_MATERIALS.contains(block.getType())) {
                            block.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
    }

    private void rememberNameState(Player player) {
        duelNameStates.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerNameState(player.displayName(), player.playerListName()));
    }

    private void rememberDuelVisualState(Player player) {
        if (player == null) return;
        rememberNameState(player);
        previousScoreboardsByPlayer.putIfAbsent(player.getUniqueId(), player.getScoreboard());
    }

    private void applyTeamNameColor(Player player, net.kyori.adventure.text.format.NamedTextColor color) {
        String gradient;
        if (color == net.kyori.adventure.text.format.NamedTextColor.RED) gradient = "<gradient:#f87171:#ef4444:#fecaca>";
        else if (color == net.kyori.adventure.text.format.NamedTextColor.GOLD || color == net.kyori.adventure.text.format.NamedTextColor.YELLOW) gradient = "<gradient:#f4cd2a:#eda323:#d28d0d>";
        else gradient = "<gradient:#60a5fa:#2563eb:#dbeafe>";
        net.kyori.adventure.text.Component name = Text.mm(gradient + player.getName() + "</gradient>");
        player.displayName(name);
        player.playerListName(name);
        player.customName(null);
        player.setCustomNameVisible(false);
    }

    private void restorePlayerNameStates() {
        restorePlayerNameStates(new HashSet<>(duelNameStates.keySet()));
    }

    private void restorePlayerNameStates(Collection<UUID> players) {
        if (players == null) return;
        for (UUID uuid : new ArrayList<>(players)) {
            PlayerNameState state = duelNameStates.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || state == null) continue;
            player.displayName(state.displayName());
            player.playerListName(state.playerListName());
            player.customName(null);
            player.setCustomNameVisible(false);
        }
    }

    private void restoreDuelVisualState(Collection<UUID> players) {
        if (players == null) return;
        for (UUID uuid : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            Scoreboard previous = previousScoreboardsByPlayer.remove(uuid);
            if (player != null && previous != null) player.setScoreboard(previous);
        }
        restorePlayerNameStates(players);
    }

    private void restoreAllDuelVisualStates() {
        restoreDuelVisualState(new LinkedHashSet<>(previousScoreboardsByPlayer.keySet()));
        for (Iterator<Map.Entry<UUID, PlayerNameState>> iterator = duelNameStates.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, PlayerNameState> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.displayName(entry.getValue().displayName());
                player.playerListName(entry.getValue().playerListName());
                player.customName(null);
                player.setCustomNameVisible(false);
            }
            iterator.remove();
        }
    }

    private Set<UUID> matchMembers(DuelMatch match) {
        Set<UUID> all = new LinkedHashSet<>();
        if (match == null) return all;
        all.addAll(match.teamOne());
        all.addAll(match.teamTwo());
        return all;
    }

    private Set<UUID> activeFfaPlayers(DuelMatch match) {
        Set<UUID> active = matchMembers(match);
        active.removeAll(match.teamOneEliminated());
        active.removeAll(match.teamTwoEliminated());
        return active;
    }

    private String ffaScoreSummary(DuelMatch match) {
        return "<gray>FFA score:</gray> " + ffaLeaderSummary(match);
    }

    private String ffaLeaderSummary(DuelMatch match) {
        if (match == null) return "<white>0</white>";
        int top = match.topFfaWins();
        if (top <= 0) return "<white>0</white><gray>/</gray><yellow>" + match.roundsToWin() + "</yellow>";
        Set<UUID> leaders = new LinkedHashSet<>();
        for (UUID uuid : matchMembers(match)) {
            if (match.ffaWins(uuid) == top) leaders.add(uuid);
        }
        return "<gradient:#f4cd2a:#eda323:#d28d0d>" + names(leaders) + "</gradient> <white>" + top + "</white><gray>/</gray><yellow>" + match.roundsToWin() + "</yellow>";
    }

    private Set<UUID> forfeitWinners(DuelMatch match, UUID quitter) {
        if (match == null) return Set.of();
        if (match.mode() != DuelMode.FFA) {
            if (match.teamOne().contains(quitter)) return match.teamTwo();
            if (match.teamTwo().contains(quitter)) return match.teamOne();
            return Set.of();
        }
        Set<UUID> remaining = activeFfaPlayers(match);
        remaining.remove(quitter);
        if (remaining.isEmpty()) return Set.of();
        return Set.of(remaining.iterator().next());
    }

    private Set<UUID> opposingTeam(DuelMatch match, UUID uuid) {
        if (match.mode() == DuelMode.FFA) {
            Set<UUID> others = activeFfaPlayers(match);
            others.remove(uuid);
            return others;
        }
        if (match.teamOne().contains(uuid)) return match.teamTwo();
        if (match.teamTwo().contains(uuid)) return match.teamOne();
        return Set.of();
    }

    private boolean sameTeam(DuelMatch match, UUID first, UUID second) {
        if (match.mode() == DuelMode.FFA) return false;
        return match.teamOne().contains(first) && match.teamOne().contains(second)
                || match.teamTwo().contains(first) && match.teamTwo().contains(second);
    }

    private void applyScoreboardTeams(DuelMatch match) {
        removeScoreboardTeams(match.id(), false);
        if (Bukkit.getScoreboardManager() == null) return;
        String base = match.id().toString().replace("-", "");
        Set<UUID> members = matchMembers(match);
        Map<UUID, Scoreboard> boards = new HashMap<>();
        if (match.mode() == DuelMode.FFA) {
            String ffaName = ("duel_f_" + base).substring(0, Math.min(16, ("duel_f_" + base).length()));
            for (UUID uuid : members) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                rememberDuelVisualState(player);
                Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
                registerMatchTeams(scoreboard, match, ffaName, "");
                boards.put(uuid, scoreboard);
                player.setScoreboard(scoreboard);
                applyTeamNameColor(player, net.kyori.adventure.text.format.NamedTextColor.GOLD);
                runTabColorCommands(player, "ffa");
            }
            duelScoreboardsByMatch.put(match.id(), boards);
            scoreboardTeamsByMatch.put(match.id(), new MatchTeams(ffaName, "", members));
            updateDuelScoreboard(match);
            return;
        }
        String redName = ("duel_r_" + base).substring(0, Math.min(16, ("duel_r_" + base).length()));
        String blueName = ("duel_b_" + base).substring(0, Math.min(16, ("duel_b_" + base).length()));
        for (UUID uuid : match.teamOne()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            rememberDuelVisualState(player);
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            registerMatchTeams(scoreboard, match, redName, blueName);
            boards.put(uuid, scoreboard);
            player.setScoreboard(scoreboard);
            applyTeamNameColor(player, net.kyori.adventure.text.format.NamedTextColor.RED);
            runTabColorCommands(player, "red");
        }
        for (UUID uuid : match.teamTwo()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            rememberDuelVisualState(player);
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            registerMatchTeams(scoreboard, match, redName, blueName);
            boards.put(uuid, scoreboard);
            player.setScoreboard(scoreboard);
            applyTeamNameColor(player, net.kyori.adventure.text.format.NamedTextColor.BLUE);
            runTabColorCommands(player, "blue");
        }
        duelScoreboardsByMatch.put(match.id(), boards);
        scoreboardTeamsByMatch.put(match.id(), new MatchTeams(redName, blueName, members));
        updateDuelScoreboard(match);
    }

    private void registerMatchTeams(Scoreboard scoreboard, DuelMatch match, String redName, String blueName) {
        if (scoreboard == null || match == null) return;
        if (match.mode() == DuelMode.FFA) {
            Team ffa = scoreboard.getTeam(redName);
            if (ffa != null) ffa.unregister();
            ffa = scoreboard.registerNewTeam(redName);
            configureMatchTeam(ffa, net.kyori.adventure.text.format.NamedTextColor.GOLD);
            ffa.setAllowFriendlyFire(true);
            for (UUID uuid : matchMembers(match)) ffa.addEntry(scoreboardEntryName(uuid));
            return;
        }
        Team red = scoreboard.getTeam(redName);
        if (red != null) red.unregister();
        Team blue = scoreboard.getTeam(blueName);
        if (blue != null) blue.unregister();
        red = scoreboard.registerNewTeam(redName);
        blue = scoreboard.registerNewTeam(blueName);
        configureMatchTeam(red, net.kyori.adventure.text.format.NamedTextColor.RED);
        configureMatchTeam(blue, net.kyori.adventure.text.format.NamedTextColor.BLUE);
        for (UUID uuid : match.teamOne()) red.addEntry(scoreboardEntryName(uuid));
        for (UUID uuid : match.teamTwo()) blue.addEntry(scoreboardEntryName(uuid));
    }

    private String scoreboardEntryName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player.getName();
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 16) : name;
    }

    private void configureMatchTeam(Team team, net.kyori.adventure.text.format.NamedTextColor color) {
        team.color(color);
        team.setCanSeeFriendlyInvisibles(true);
        team.setAllowFriendlyFire(false);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
    }

    private void updateDuelScoreboards() {
        if (Bukkit.getScoreboardManager() == null) return;
        Set<UUID> seen = new HashSet<>();
        for (DuelMatch match : new HashSet<>(matchesByPlayer.values())) {
            if (match != null && seen.add(match.id())) updateDuelScoreboard(match);
        }
    }

    private void updateDuelScoreboard(DuelMatch match) {
        if (match == null || Bukkit.getScoreboardManager() == null) return;
        Map<UUID, Scoreboard> scoreboards = duelScoreboardsByMatch.get(match.id());
        if (scoreboards == null || scoreboards.isEmpty()) return;
        boolean reapply = false;
        for (UUID uuid : matchMembers(match)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            Scoreboard scoreboard = scoreboards.get(uuid);
            if (scoreboard == null || player.getScoreboard() != scoreboard) {
                reapply = true;
                break;
            }
        }
        if (reapply) {
            applyScoreboardTeams(match);
            applyHealthIndicator(match);
            return;
        }
        if (!duelSidebarEnabled()) {
            for (Scoreboard scoreboard : scoreboards.values()) clearDuelSidebar(scoreboard);
            return;
        }
        for (UUID uuid : matchMembers(match)) {
            Player player = Bukkit.getPlayer(uuid);
            Scoreboard scoreboard = scoreboards.get(uuid);
            if (player == null || scoreboard == null) continue;
            updateDuelSidebar(player, match, scoreboard);
        }
    }

    private void updateDuelSidebar(Player viewer, DuelMatch match, Scoreboard scoreboard) {
        String title = configs.get("duels/duels.yml").getString("duels.ui.scoreboard.title", "%img_solo_text%");
        Objective objective = scoreboard.getObjective(DUEL_SIDEBAR_OBJECTIVE);
        if (objective == null) objective = scoreboard.registerNewObjective(DUEL_SIDEBAR_OBJECTIVE, Criteria.DUMMY, renderDuelSidebarText(viewer, match, title));
        objective.displayName(renderDuelSidebarText(viewer, match, title));
        applyBlankNumberFormat(objective);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = visibleDuelSidebarLines(duelSidebarLines(viewer, match));
        int score = lines.size();
        Set<String> activeTeams = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String entry = duelSidebarEntry(index);
            String teamName = "duel_sb_" + index;
            activeTeams.add(teamName);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) team = scoreboard.registerNewTeam(teamName);
            if (!team.hasEntry(entry)) team.addEntry(entry);
            String line = configs.get("duels/duels.yml").getBoolean("duels.ui.scoreboard.centered", true)
                    ? centerDuelSidebarLine(viewer, match, lines.get(index))
                    : lines.get(index);
            team.prefix(renderDuelSidebarText(viewer, match, line));
            objective.getScore(entry).setScore(score--);
        }
        for (Team team : new HashSet<>(scoreboard.getTeams())) {
            if (!team.getName().startsWith("duel_sb_") || activeTeams.contains(team.getName())) continue;
            for (String entry : new HashSet<>(team.getEntries())) scoreboard.resetScores(entry);
            team.unregister();
        }
    }

    private boolean duelSidebarEnabled() {
        return configs.get("duels/duels.yml").getBoolean("duels.ui.scoreboard.enabled", true);
    }

    private List<String> duelSidebarLines(Player viewer, DuelMatch match) {
        if (match.mode() == DuelMode.FFA) {
            List<String> configured = configs.get("duels/duels.yml").getStringList("duels.ui.scoreboard.ffa-lines");
            return configured == null || configured.isEmpty() ? defaultFfaSidebarLines() : new ArrayList<>(configured);
        }
        List<String> lines = new ArrayList<>(configs.get("duels/duels.yml").getStringList("duels.ui.scoreboard.lines"));
        if (lines.isEmpty()) lines.addAll(defaultTeamSidebarLines());
        if (singleOpponent(viewer, match) != null) {
            List<String> solo = configs.get("duels/duels.yml").getStringList("duels.ui.scoreboard.solo-lines");
            lines.addAll(solo == null || solo.isEmpty() ? defaultSoloSidebarLines() : solo);
        }
        return lines;
    }

    private List<String> visibleDuelSidebarLines(List<String> lines) {
        return lines;
    }

    private String centerDuelSidebarLine(Player viewer, DuelMatch match, String input) {
        if (input == null || input.isBlank()) return input == null ? "" : input;
        int width = Math.max(16, configs.get("duels/duels.yml").getInt("duels.ui.scoreboard.center-width", 30));
        String resolved = replaceDuelSidebarPlaceholders(viewer, match, replaceDuelImageTags(input));
        String plain = resolved
                .replaceAll("(?i)&x(&[0-9a-f]){6}", "")
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("(?i)§x(§[0-9a-f]){6}", "")
                .replaceAll("(?i)§[0-9a-fk-or]", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("%img_[A-Za-z0-9_]+%", "");
        int spaces = Math.max(0, (width - plain.length()) / 2);
        return " ".repeat(spaces) + input;
    }

    private List<String> defaultTeamSidebarLines() {
        return List.of(
                "",
                "",
                "<dark_gray>----------------</dark_gray>",
                "<gradient:#f4cd2a:#eda323:#d28d0d><bold>DUEL LIVE</bold></gradient>",
                "<gray>Round <white>%round%</white><dark_gray>/</dark_gray><yellow>%rounds_to_win%</yellow> <dark_gray>|</dark_gray> <yellow>%duration%</yellow></gray>",
                "<gray>Kit <gradient:#f4cd2a:#eda323:#d28d0d>%kit%</gradient></gray>",
                "<gray>Arena <white>%arena%</white></gray>",
                "",
                "<gradient:#ef4444:#fca5a5><bold>RED</bold></gradient> <white>%red_score%</white><dark_gray>/</dark_gray><yellow>%rounds_to_win%</yellow> <dark_gray>|</dark_gray> <#fecaca>%red_alive% alive</#fecaca>",
                "<#fecaca>%red_team%</#fecaca>",
                "<gradient:#2563eb:#93c5fd><bold>BLUE</bold></gradient> <white>%blue_score%</white><dark_gray>/</dark_gray><yellow>%rounds_to_win%</yellow> <dark_gray>|</dark_gray> <#dbeafe>%blue_alive% alive</#dbeafe>",
                "<#dbeafe>%blue_team%</#dbeafe>"
        );
    }

    private List<String> defaultSoloSidebarLines() {
        return List.of(
                "",
                "<gradient:#f4cd2a:#eda323:#d28d0d>Opponent</gradient>",
                "<white>%opponent%</white> <dark_gray>|</dark_gray> <gradient:#f4cd2a:#eda323:#d28d0d>%opponent_ping%ms</gradient>"
        );
    }

    private List<String> defaultFfaSidebarLines() {
        return List.of(
                "",
                "",
                "<dark_gray>----------------</dark_gray>",
                "<gradient:#f4cd2a:#eda323:#d28d0d><bold>FFA DUEL</bold></gradient>",
                "<gray>Round <white>%round%</white><dark_gray>/</dark_gray><yellow>%rounds_to_win%</yellow> <dark_gray>|</dark_gray> <yellow>%duration%</yellow></gray>",
                "<gray>Kit <gradient:#f4cd2a:#eda323:#d28d0d>%kit%</gradient></gray>",
                "<gray>Arena <white>%arena%</white></gray>",
                "",
                "<gradient:#f4cd2a:#eda323:#d28d0d>Leader</gradient>",
                "%ffa_leader%",
                "<gray>Remaining <white>%remaining%</white><dark_gray>/</dark_gray><yellow>%players%</yellow></gray>",
                "",
                "<gray>Your Ping <aqua>%player_ping%ms</aqua></gray>"
        );
    }

    private Player singleOpponent(Player viewer, DuelMatch match) {
        if (viewer == null || match == null || match.mode() == DuelMode.FFA) return null;
        Set<UUID> opponents = opposingTeam(match, viewer.getUniqueId());
        if (opponents.size() != 1) return null;
        return Bukkit.getPlayer(opponents.iterator().next());
    }

    private net.kyori.adventure.text.Component renderDuelSidebarText(Player viewer, DuelMatch match, String input) {
        String parsed = replaceDuelSidebarPlaceholders(viewer, match, input == null ? "" : input);
        parsed = replaceDuelImageTags(parsed);
        if (viewer != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            parsed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(viewer, parsed);
        }
        if (looksMiniMessage(parsed)) return Text.mm(parsed);
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(legacyColors(parsed));
    }

    private String replaceDuelSidebarPlaceholders(Player viewer, DuelMatch match, String input) {
        Player opponent = singleOpponent(viewer, match);
        DuelKit kit = kits.get(match.kitId().toLowerCase(Locale.ROOT));
        DuelMap map = activeMapsByMatch.getOrDefault(match.id(), maps.get(match.mapId()));
        int round = Math.max(1, Math.min(match.roundsToWin(), match.totalRoundsPlayed() + 1));
        return input
                .replace("%round%", String.valueOf(round))
                .replace("%round_current%", String.valueOf(round))
                .replace("%rounds_to_win%", String.valueOf(match.roundsToWin()))
                .replace("%red_score%", String.valueOf(match.teamOneWins()))
                .replace("%blue_score%", String.valueOf(match.teamTwoWins()))
                .replace("%red_team%", names(match.teamOne()))
                .replace("%blue_team%", names(match.teamTwo()))
                .replace("%red_alive%", String.valueOf(aliveCount(match, match.teamOne())))
                .replace("%blue_alive%", String.valueOf(aliveCount(match, match.teamTwo())))
                .replace("%opponent%", opponent == null ? "None" : opponent.getName())
                .replace("%opponent_ping%", opponent == null ? "-" : String.valueOf(opponent.getPing()))
                .replace("%kit%", kit == null ? match.kitId() : kit.displayName())
                .replace("%arena%", map == null ? match.mapId() : map.displayName())
                .replace("%mode%", modeLabel(match.mode()))
                .replace("%duration%", formatDuration(System.currentTimeMillis() - match.startedAt()))
                .replace("%player_ping%", viewer == null ? "-" : String.valueOf(viewer.getPing()))
                .replace("%players%", String.valueOf(matchMembers(match).size()))
                .replace("%remaining%", String.valueOf(activeFfaPlayers(match).size()))
                .replace("%ffa_leader%", ffaLeaderSummary(match));
    }

    private int aliveCount(DuelMatch match, Collection<UUID> team) {
        int alive = 0;
        for (UUID uuid : team) {
            if (!match.teamOneEliminated().contains(uuid) && !match.teamTwoEliminated().contains(uuid)) alive++;
        }
        return alive;
    }

    private String replaceDuelImageTags(String input) {
        String out = input.replace("<img:solo_text>", configs.get("social/friends.yml").getString("friends.tab.visuals.images.placeholders.solo_text", "%img_solo_text%"));
        org.bukkit.configuration.ConfigurationSection section = configs.get("social/friends.yml").getConfigurationSection("friends.tab.visuals.images.placeholders");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                out = out.replace("<img:" + id + ">", section.getString(id, "%img_" + id + "%"));
            }
        }
        return out;
    }

    private boolean looksMiniMessage(String input) {
        if (input == null) return false;
        return input.contains("<gradient:")
                || input.contains("<#")
                || input.contains("<gray>")
                || input.contains("<white>")
                || input.contains("<yellow>")
                || input.contains("<aqua>")
                || input.contains("<red>")
                || input.contains("<blue>")
                || input.contains("<green>")
                || input.contains("<gradient:#f4cd2a:#eda323:#d28d0d>")
                || input.contains("<dark_gray>")
                || input.contains("<bold>")
                || input.contains("</");
    }

    private String legacyColors(String input) {
        StringBuilder out = new StringBuilder();
        char color = org.bukkit.ChatColor.COLOR_CHAR;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                if (input.charAt(i + 1) == '#' && i + 7 < input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        out.append(color).append('x');
                        for (char h : hex.toCharArray()) out.append(color).append(h);
                        i += 7;
                        continue;
                    }
                }
                out.append(color).append(input.charAt(++i));
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String duelSidebarEntry(int index) {
        String colors = "0123456789abcdef";
        char color = org.bukkit.ChatColor.COLOR_CHAR;
        return "" + color + colors.charAt(index % colors.length()) + color + colors.charAt((index / colors.length()) % colors.length());
    }

    private void clearDuelSidebar(Scoreboard scoreboard) {
        if (scoreboard == null) return;
        Objective objective = scoreboard.getObjective(DUEL_SIDEBAR_OBJECTIVE);
        if (objective != null) objective.unregister();
        clearDuelSidebarLines(scoreboard);
    }

    private void clearDuelSidebarLines(Scoreboard scoreboard) {
        for (Team team : new HashSet<>(scoreboard.getTeams())) {
            if (!team.getName().startsWith("duel_sb_")) continue;
            for (String entry : new HashSet<>(team.getEntries())) scoreboard.resetScores(entry);
            team.unregister();
        }
    }

    private void applyBlankNumberFormat(Objective objective) {
        if (!configs.get("duels/duels.yml").getBoolean("duels.ui.scoreboard.hide-line-numbers", true)) return;
        try {
            Class<?> numberFormatClass = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = numberFormatClass.getMethod("blank").invoke(null);
            objective.getClass().getMethod("numberFormat", numberFormatClass).invoke(objective, blank);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void applyHealthIndicator(DuelMatch match) {
        if (!healthIndicatorEnabled(match)) {
            clearHealthIndicator(match);
            return;
        }
        if (Bukkit.getScoreboardManager() == null) return;
        Map<UUID, Scoreboard> scoreboards = duelScoreboardsByMatch.get(match.id());
        if (scoreboards == null || scoreboards.isEmpty()) {
            applyScoreboardTeams(match);
            scoreboards = duelScoreboardsByMatch.get(match.id());
        }
        if (scoreboards == null || scoreboards.isEmpty()) return;
        for (Scoreboard scoreboard : scoreboards.values()) ensureHealthObjective(scoreboard);
        updateHealthIndicatorScores();
        startHealthIndicatorTask();
    }

    private void startHealthIndicatorTask() {
        if (healthIndicatorTask != null && !healthIndicatorTask.isCancelled()) return;
        long interval = Math.max(1L, configs.get("duels/duels.yml").getLong("duels.health-indicator.update-ticks", 5L));
        healthIndicatorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHealthIndicatorScores, 0L, interval);
    }

    private void updateHealthIndicatorScores() {
        if (Bukkit.getScoreboardManager() == null) return;
        boolean any = false;
        Set<UUID> seenMatches = new HashSet<>();
        for (DuelMatch match : new HashSet<>(matchesByPlayer.values())) {
            if (match == null || !seenMatches.add(match.id())) continue;
            Map<UUID, Scoreboard> scoreboards = duelScoreboardsByMatch.get(match.id());
            if (scoreboards == null || scoreboards.isEmpty()) continue;
            if (!healthIndicatorEnabled(match)) {
                for (Scoreboard scoreboard : scoreboards.values()) {
                    Objective old = scoreboard.getObjective(HEALTH_OBJECTIVE);
                    if (old != null) old.unregister();
                }
                continue;
            }
            for (Scoreboard scoreboard : scoreboards.values()) {
                Objective objective = ensureHealthObjective(scoreboard);
                for (UUID uuid : matchMembers(match)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || matchesByPlayer.get(uuid) != match) continue;
                    objective.getScore(player.getName()).setScore(healthScore(player));
                }
            }
            any = true;
        }
        if (!any) {
            healthIndicatorEntries.clear();
            if (healthIndicatorTask != null) {
                healthIndicatorTask.cancel();
                healthIndicatorTask = null;
            }
        }
    }

    private void clearHealthIndicator(DuelMatch match) {
        if (match == null) return;
        Map<UUID, Scoreboard> scoreboards = duelScoreboardsByMatch.get(match.id());
        if (scoreboards != null && !scoreboards.isEmpty()) {
            for (Scoreboard scoreboard : scoreboards.values()) {
                for (UUID uuid : matchMembers(match)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) scoreboard.resetScores(player.getName());
                }
                Objective objective = scoreboard.getObjective(HEALTH_OBJECTIVE);
                if (objective != null) objective.unregister();
            }
        } else if (Bukkit.getScoreboardManager() != null) {
            for (UUID uuid : matchMembers(match)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) clearHealthIndicatorEntry(player);
            }
        }
        updateHealthIndicatorScores();
    }

    private boolean healthIndicatorEnabled(DuelMatch match) {
        if (match == null) return false;
        if (!configs.get("duels/duels.yml").getBoolean("duels.health-indicator.enabled", true)) return false;
        if (!configs.get("duels/duels.yml").getBoolean("duels.health-indicator.per-kit-toggle", false)) return true;
        DuelKit kit = kits.get(match.kitId().toLowerCase(Locale.ROOT));
        return kit == null || kit.healthIndicator();
    }

    private Objective ensureHealthObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(HEALTH_OBJECTIVE);
        String heart = Character.toString((char) 0x2764);
        if (objective == null) objective = scoreboard.registerNewObjective(HEALTH_OBJECTIVE, Criteria.DUMMY, Text.mm("<red>" + heart + "</red>"));
        objective.displayName(Text.mm("<red>" + heart + "</red>"));
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        return objective;
    }

    private int healthScore(Player player) {
        return Math.max(0, (int) Math.ceil(player.getHealth()));
    }

    private void clearHealthIndicatorEntry(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) return;
        player.getScoreboard().resetScores(player.getName());
        healthIndicatorEntries.remove(player.getName());
    }

    private void clearAllHealthIndicators() {
        if (healthIndicatorTask != null) {
            healthIndicatorTask.cancel();
            healthIndicatorTask = null;
        }
        healthIndicatorEntries.clear();
        clearVanillaHealthObjective();
    }

    private void clearVanillaHealthObjective() {
        if (Bukkit.getScoreboardManager() == null) return;
        for (Map<UUID, Scoreboard> scoreboards : new HashSet<>(duelScoreboardsByMatch.values())) {
            for (Scoreboard scoreboard : new HashSet<>(scoreboards.values())) {
                Objective duelObjective = scoreboard.getObjective(HEALTH_OBJECTIVE);
                if (duelObjective != null) duelObjective.unregister();
            }
        }
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(HEALTH_OBJECTIVE);
        if (objective != null) objective.unregister();
        healthIndicatorEntries.clear();
    }

    private void removeScoreboardTeams(UUID matchId) {
        removeScoreboardTeams(matchId, true);
    }

    private void removeScoreboardTeams(UUID matchId, boolean restorePlayers) {
        MatchTeams teams = scoreboardTeamsByMatch.remove(matchId);
        Map<UUID, Scoreboard> scoreboards = duelScoreboardsByMatch.remove(matchId);
        if (Bukkit.getScoreboardManager() == null) return;
        Collection<Scoreboard> boards = scoreboards == null || scoreboards.isEmpty()
                ? List.of(Bukkit.getScoreboardManager().getMainScoreboard())
                : new HashSet<>(scoreboards.values());
        for (Scoreboard scoreboard : boards) {
            Objective health = scoreboard.getObjective(HEALTH_OBJECTIVE);
            if (health != null) health.unregister();
            clearDuelSidebar(scoreboard);
            if (teams != null) {
                if (teams.red() != null && !teams.red().isBlank()) {
                    Team red = scoreboard.getTeam(teams.red());
                    if (red != null) red.unregister();
                }
                if (teams.blue() != null && !teams.blue().isBlank()) {
                    Team blue = scoreboard.getTeam(teams.blue());
                    if (blue != null) blue.unregister();
                }
            }
        }
        if (restorePlayers && teams != null) restoreDuelVisualState(teams.members());
    }

    private void runTabColorCommands(Player player, String team) {
        if (Bukkit.getPluginManager().getPlugin("TAB") == null) return;
        List<String> commands = configs.get("duels/duels.yml").getStringList("duels.tab.team-" + team + "-commands");
        for (String command : commands) {
            if (command == null || command.isBlank()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()).replace("%team%", team));
        }
    }

    private String blockKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void balanceRatings(Set<UUID> teamOne, Set<UUID> teamTwo, Set<UUID> winners) {
        boolean teamOneWon = !teamOne.isEmpty() && winners.contains(teamOne.iterator().next());
        for (UUID uuid : teamOne) adjustRating(uuid, teamOneWon);
        for (UUID uuid : teamTwo) adjustRating(uuid, !teamOneWon);
    }

    private void adjustRating(UUID uuid, boolean win) {
        var data = repository.load(uuid);
        data.recordDuel(win);
        int performance = Math.max(0, data.duelKills() - data.duelDeaths());
        int delta = win ? 10 + Math.min(10, performance * 2) : -(10 + Math.min(8, data.duelDeaths()));
        data.duelRating(data.duelRating() + delta);
        repository.save(data);
    }

    private void rememberRecentOpponents(Collection<UUID> firstTeam, Collection<UUID> secondTeam) {
        for (UUID first : firstTeam) {
            for (UUID second : secondTeam) {
                recentOpponents.put(first, second);
                recentOpponents.put(second, first);
            }
        }
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
        Deque<UUID> queue = switch (unit.mode()) {
            case SOLO -> soloQueues.get(unit.kitId());
            case PARTY -> partyQueues.get(unit.kitId());
            case FFA -> ffaQueues.get(unit.kitId());
        };
        if (queue != null) queue.remove(unit.unitId());
        queueUnits.remove(unit.unitId());
        queueSearchRangeNotices.remove(unit.unitId());
        for (UUID member : unit.members()) queueByPlayer.remove(member);
        if (notify) unit.members().forEach(this::removeQueueItemByUuid);
    }

    private void purgePlayersFromQueues(Collection<UUID> members) {
        for (UUID member : members) {
            QueueUnit unit = queueByPlayer.get(member);
            if (unit != null) removeQueueUnit(unit, false);
        }
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

    private String duelMessage(String key, String fallback) {
        return configs.get("duels/duels.yml").getString("duels.messages." + key, fallback);
    }

    private List<String> duelMessageList(String key, List<String> fallback) {
        List<String> configured = configs.get("duels/duels.yml").getStringList("duels.messages." + key);
        return configured == null || configured.isEmpty() ? fallback : configured;
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
        return createTagged(material, name, id, List.of("<gray>Right click to use.</gray>"));
    }

    private ItemStack createTagged(Material material, String name, String id, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(lore.stream().map(line -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line)).toList());
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

    private String resolveKitOrDefault(String input) {
        String resolved = resolveKit(input);
        return resolved == null ? "default" : resolved;
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

    private String modeLabel(DuelMode mode) {
        if (mode == DuelMode.PARTY) return "2v2";
        if (mode == DuelMode.FFA) return "FFA";
        return "1v1";
    }

    private int kitRounds(String kitId) {
        DuelKit kit = kits.get(kitId == null ? "" : kitId.toLowerCase(Locale.ROOT));
        return kit == null ? 1 : Math.max(1, kit.rounds());
    }

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
            maps.put(id.toLowerCase(Locale.ROOT), new DuelMap(id.toLowerCase(Locale.ROOT), map.getString("display-name", id), map.getBoolean("enabled", true), worldName, loc(map, "lobby", worldName), loc(map, "spawn-a", worldName), loc(map, "spawn-b", worldName), loc(map, "ffa-spawn", worldName), loc(map, "spectator", worldName), gate(map.getConfigurationSection("gates.red"), worldName), gate(map.getConfigurationSection("gates.blue"), worldName), loc(map, "gate-exits.red", worldName), loc(map, "gate-exits.blue", worldName), gate(gateZoneSection(map, "red"), worldName), gate(gateZoneSection(map, "blue"), worldName)));
        }
    }

    private org.bukkit.configuration.ConfigurationSection gateZoneSection(org.bukkit.configuration.ConfigurationSection map, String team) {
        if (map == null) return null;
        org.bukkit.configuration.ConfigurationSection section = map.getConfigurationSection("gate-zones." + team);
        return section == null ? map.getConfigurationSection("gate-close-zones." + team) : section;
    }

    private DuelGateRegion gate(org.bukkit.configuration.ConfigurationSection section, String worldName) {
        if (section == null) return null;
        String gateWorld = section.getString("world", worldName);
        List<Integer> min = section.getIntegerList("min");
        List<Integer> max = section.getIntegerList("max");
        if (min.size() < 3 || max.size() < 3) {
            min = section.getIntegerList("pos1");
            max = section.getIntegerList("pos2");
        }
        if (min.size() >= 3 && max.size() >= 3) {
            return new DuelGateRegion(gateWorld, min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2));
        }
        org.bukkit.configuration.ConfigurationSection minSection = section.getConfigurationSection("min");
        org.bukkit.configuration.ConfigurationSection maxSection = section.getConfigurationSection("max");
        if (minSection != null && maxSection != null) {
            return new DuelGateRegion(gateWorld, minSection.getInt("x"), minSection.getInt("y"), minSection.getInt("z"), maxSection.getInt("x"), maxSection.getInt("y"), maxSection.getInt("z"));
        }
        return null;
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

    private record QueueUnit(UUID unitId, DuelMode mode, String kitId, boolean ranked, Set<UUID> members, long joinedAt) {}
    private record DuelChallenge(UUID challenger, UUID target, Set<UUID> challengers, Set<UUID> targets, String kitId, String mapId, DuelMode mode, long createdAt) {}
    private static final class SpectatorSnapshot {
        private final Location location;
        private final GameMode gameMode;
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final UUID matchId;
        private final boolean allowFlight;
        private final boolean flying;
        private final boolean invisible;
        private final boolean collidable;
        private boolean visible = false;

        private SpectatorSnapshot(Location location, GameMode gameMode, ItemStack[] contents, ItemStack[] armor, ItemStack offhand, UUID matchId, boolean allowFlight, boolean flying, boolean invisible, boolean collidable) {
            this.location = location;
            this.gameMode = gameMode;
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
            this.matchId = matchId;
            this.allowFlight = allowFlight;
            this.flying = flying;
            this.invisible = invisible;
            this.collidable = collidable;
        }

        static SpectatorSnapshot capture(Player player, UUID matchId) {
            return new SpectatorSnapshot(player.getLocation(), player.getGameMode(), player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand(), matchId, player.getAllowFlight(), player.isFlying(), player.isInvisible(), player.isCollidable());
        }

        UUID matchId() { return matchId; }
        boolean visible() { return visible; }
        void visible(boolean visible) { this.visible = visible; }

        void restore(Player player) {
            player.setGameMode(gameMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : gameMode);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.setInvisible(invisible);
            player.setCollidable(collidable);
            player.getInventory().setContents(contents);
            player.getInventory().setArmorContents(armor);
            player.getInventory().setItemInOffHand(offhand);
            player.teleport(location);
            player.updateInventory();
        }
    }
    private record PendingKitRoundEdit(String kitId, Location location, org.bukkit.block.data.BlockData previous) {}
    private record DuelLoadout(ItemStack[] contents, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, ItemStack offhand) {}
    private record LoadoutButtonDisplacement(int slot, ItemStack item) {}
    private record MatchTeams(String red, String blue, Set<UUID> members) {}
    private record PlayerNameState(net.kyori.adventure.text.Component displayName, net.kyori.adventure.text.Component playerListName) {}
    private enum GateEditorTarget { RED_GATE, BLUE_GATE, RED_CLOSE_ZONE, BLUE_CLOSE_ZONE }

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

