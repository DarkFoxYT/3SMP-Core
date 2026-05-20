package net.dark.threecore.dungeons;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.survival.SurvivalService;
import net.dark.threecore.text.Text;
import net.dark.threecore.dungeons.api.DungeonAPI;
import net.dark.threecore.dungeons.boulder.BoulderTrapDefinition;
import net.dark.threecore.dungeons.boulder.BoulderTrapService;
import net.dark.threecore.dungeons.engine.DungeonRotation;
import net.dark.threecore.dungeons.engine.DungeonGraphGenerator;
import net.dark.threecore.dungeons.engine.DungeonLayout;
import net.dark.threecore.dungeons.engine.DungeonRoomDefinition;
import net.dark.threecore.dungeons.engine.DungeonRoomRegistry;
import net.dark.threecore.dungeons.engine.DungeonTrapDefinition;
import net.dark.threecore.dungeons.engine.DungeonValidationResult;
import net.dark.threecore.dungeons.engine.DungeonValidator;
import net.dark.threecore.dungeons.engine.MobTrigger;
import net.dark.threecore.dungeons.engine.PlacedConnector;
import net.dark.threecore.dungeons.engine.PlacedDungeonRoom;
import net.dark.threecore.dungeons.engine.RoomTransform;
import net.dark.threecore.dungeons.engine.RoomType;
import net.dark.threecore.dungeons.integration.ItemsAdderHook;
import net.dark.threecore.dungeons.integration.MythicMobsHook;
import net.dark.threecore.dungeons.runtime.DungeonDoorManager;
import net.dark.threecore.dungeons.runtime.DungeonInventoryService;
import net.dark.threecore.dungeons.runtime.DungeonReadyManager;
import net.dark.threecore.dungeons.event.DungeonGenerateEvent;
import net.dark.threecore.dungeons.event.DungeonStartEvent;
import net.dark.threecore.dungeons.event.DungeonEndEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class DungeonService implements Listener {
    private static final String ITEM_KEY = "3smpcore_dungeon_item";
    private static final String ITEM_ID = "dungeon_menu";
    private static final String DEV_TOOL_ID = "dungeon_dev_save";
    private static final String DEV_WAND_ID = "dungeon_dev_wand";
    private static final String DEV_MARKER_ID = "dungeon_marker";
    private static final String BOULDER_TOOL_ID = "dungeon_boulder_tool";
    private static final String COSMETICS_ITEM_ID = "cosmetics_menu";
    private static final String DUEL_QUEUE_ITEM_ID = "queue_sword";
    private static final String PARTY_HUB_ITEM_ID = "party_lectern";
    private static final String PARTY_CREATE_ITEM_ID = "party_goat_horn";
    private static final String PARTY_DISBAND_ITEM_ID = "party_disband";
    private static final int DEV_TOOL_SLOT = 8;
    private static final int MAX_SIZE = 64;
    private static final String TEMPLATE_USED_PATH = "template-command-used";
    private static final Set<UUID> ACTIVE_DUNGEON_PLAYERS = new HashSet<>();
    private static final String DEBUG_PATH = "debug.enabled";
    private static final String DUNGEON_BOSS_KEY = "dungeon_boss";
    private static final String DUNGEON_MOB_KEY = "dungeon_mob";
    private static final String DUNGEON_TRAP_KEY = "dungeon_trap";
    private static final String ROOM_MARKER_KEY = "3smp_dungeon_room_marker";
    private static final String ROOM_MARKER_ROOM_ID_KEY = "room_id";
    private static final String ROOM_CONNECTOR_ID_KEY = "connector_id";
    private static final String ROOM_CONNECTOR_ROLE_KEY = "connector_role";
    private static final String ROOM_CONNECTOR_TYPE_KEY = "connector_type";
    private static final String ROOM_CONNECTOR_VERTICAL_KEY = "connector_vertical";
    private static final String ROOM_CONNECTOR_TARGET_Y_KEY = "connector_target_y";
    private static final String ROOM_CONNECTOR_SNAP_KEY = "connector_snap";
    private static final String ROOM_CONNECTOR_ANCHOR_X_KEY = "connector_anchor_x";
    private static final String ROOM_CONNECTOR_ANCHOR_Y_KEY = "connector_anchor_y";
    private static final String ROOM_CONNECTOR_ANCHOR_Z_KEY = "connector_anchor_z";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MenuService menuService;
    private final PlayerDataRepository repository;
    private final PartyService partyService;
    private final SurvivalService survivalService;
    private final Map<String, RoomReservation> reservations = new HashMap<>();
    private final Map<UUID, DungeonRunOptions> menuOptions = new HashMap<>();
    private final Map<UUID, ActiveDungeonRun> activeRuns = new HashMap<>();
    private final Map<UUID, Set<UUID>> activeGroups = new HashMap<>();
    private final Map<UUID, List<PlacedRoom>> activeLayouts = new HashMap<>();
    private final Map<UUID, World> instanceWorldsByPlayer = new HashMap<>();
    private final Map<UUID, Set<Integer>> spawnedRuntimeRooms = new HashMap<>();
    private final Map<UUID, Set<Integer>> spawnedRuntimeBossRooms = new HashMap<>();
    private final Map<UUID, List<UUID>> spawnedRuntimeEntities = new HashMap<>();
    private final Map<UUID, Location> editorPos1 = new HashMap<>();
    private final Map<UUID, Location> editorPos2 = new HashMap<>();
    private final Map<UUID, BoulderEditSession> boulderEdits = new HashMap<>();
    private final Map<UUID, List<UUID>> boulderMarkerPreviews = new HashMap<>();
    private final Map<UUID, List<UUID>> editorTrapPreviews = new HashMap<>();
    private final Set<String> physicsSuppressedWorlds = new HashSet<>();
    private final Map<String, Long> allowedDungeonMobSpawnWindows = new java.util.concurrent.ConcurrentHashMap<>();
    private final DungeonRoomRegistry roomRegistry;
    private final DungeonGraphGenerator graphGenerator;
    private final DungeonValidator dungeonValidator = new DungeonValidator();
    private final DungeonDoorManager doorManager;
    private final DungeonInventoryService inventoryService;
    private final DungeonReadyManager readyManager;
    private final MythicMobsHook mythicMobsHook;
    private final ItemsAdderHook itemsAdderHook;
    private final BoulderTrapService boulderTrapService;
    private BukkitTask healthIndicatorTask;
    private final Map<UUID, net.dark.threecore.dungeons.runtime.DungeonSession> runtimeSessions = new HashMap<>();
    private final Map<UUID, UUID> playerRuntimeSessions = new HashMap<>();
    private final Map<String, Runnable> roomTriggers = new HashMap<>();
    private final Map<String, Object> lootProviders = new HashMap<>();
    private final Map<String, Object> mobProviders = new HashMap<>();
    private net.dark.threecore.social.SocialTabService socialTabService;

    public DungeonService(JavaPlugin plugin, ConfigFiles configs, MenuService menuService, PlayerDataRepository repository, PartyService partyService, SurvivalService survivalService) {
        this.plugin = plugin;
        this.configs = configs;
        this.menuService = menuService;
        this.repository = repository;
        this.partyService = partyService;
        this.survivalService = survivalService;
        this.roomRegistry = new DungeonRoomRegistry(plugin, configs);
        this.graphGenerator = new DungeonGraphGenerator(roomRegistry);
        this.doorManager = new DungeonDoorManager(plugin, configs);
        this.inventoryService = new DungeonInventoryService(plugin, configs);
        this.readyManager = new DungeonReadyManager(plugin);
        this.mythicMobsHook = new MythicMobsHook(plugin);
        this.itemsAdderHook = new ItemsAdderHook(plugin);
        this.boulderTrapService = new BoulderTrapService(plugin, configs);
        Bukkit.getPluginManager().registerEvents(boulderTrapService, plugin);
        DungeonAPI.install(this);
        cleanupStaleDungeonInstances();
        loadReservations();
    }

    public void reload() { reservations.clear(); activeGroups.clear(); roomRegistry.reload(); loadReservations(); }

    public void setSocialTabService(net.dark.threecore.social.SocialTabService socialTabService) {
        this.socialTabService = socialTabService;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) { if (sender instanceof Player player) openDungeonEntry(player); else Text.send(sender, "<red>Players only.</red>"); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "enter" -> { if (sender instanceof Player player) enter(player, args.length >= 2 ? args[1] : "jungle"); }
            case "spawn" -> {
                if (sender instanceof Player player) {
                    if (args.length >= 2 && args[1].equalsIgnoreCase("set")) setDungeonSpawn(player);
                    else teleportDungeonSpawn(player);
                }
            }
            case "template" -> { if (sender instanceof Player player) createTemplateWorld(player); else Text.send(sender, "<red>Players only.</red>"); }
            case "setspawn" -> { if (sender instanceof Player player) setDungeonSpawn(player); }
            case "dev", "editor" -> {
                if (!(sender instanceof Player player)) break;
                if (args.length >= 2 && args[1].equalsIgnoreCase("connector")) {
                    handleEditorConnectorCommand(player, args);
                    return;
                }
                if (args.length >= 2 && (args[1].equalsIgnoreCase("marker") || args[1].equalsIgnoreCase("markers"))) {
                    handleEditorMarkerCommand(player, args);
                    return;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("leave")) {
                    leaveDungeonEditor(player);
                    return;
                }
                openDungeonEditor(player);
            }
            case "leave" -> { if (sender instanceof Player player) leave(player); }
            case "ready" -> { if (sender instanceof Player player) toggleReady(player); }
            case "save" -> { if (sender instanceof Player player) saveTemplate(player, args.length >= 2 ? args[1] : "room_" + System.currentTimeMillis(), args.length >= 3 ? args[2] : "jungle"); }
            case "templates" -> listTemplates(sender);
            case "spawnset" -> { if (sender instanceof Player player) setDungeonSpawn(player); }
            case "restore" -> restoreCommand(sender, args);
            case "forceend" -> forceEndCommand(sender, args);
            case "debuginv" -> debugInventoryCommand(sender, args);
            case "debug" -> dungeonDebug(sender, args);
            case "boulder" -> dungeonBoulder(sender, args);
            case "give" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { Text.send(sender, "<red>Usage: /dungeon give <player></red>"); return; } Player target = Bukkit.getPlayerExact(args[1]); if (target != null) giveItem(target); }
            case "reload" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reload(); Text.send(sender, "<green>Dungeons reloaded.</green>"); }
            case "clear" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reservations.clear(); saveReservations(); Text.send(sender, "<yellow>Dungeon room reservations cleared.</yellow>"); }
            default -> Text.send(sender, "<gray>/dungeon menu|ready|debug|restore|forceend|debuginv|spawn|template|enter [level]|save <id> [level]|dev|templates|leave|give <player>|reload|clear</gray>");
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("menu", "ready", "debug", "boulder", "restore", "forceend", "debuginv", "spawn", "spawnset", "template", "setspawn", "enter", "save", "dev", "editor", "templates", "leave", "give", "reload", "clear");
        if (args[0].equalsIgnoreCase("editor")) {
            if (args.length == 2) return List.of("connector", "marker", "leave");
            if (args.length == 3 && args[1].equalsIgnoreCase("connector")) return List.of("entrance", "exit", "anchor", "show", "clear");
            if (args.length == 3 && (args[1].equalsIgnoreCase("marker") || args[1].equalsIgnoreCase("markers"))) return List.of("facing", "show", "hide", "clear", "clearnear", "save");
        }
        if (args[0].equalsIgnoreCase("debug")) return List.of("generate", "validate", "layout", "connectors", "rotation", "paste", "transform", "pivot", "bounds", "failed-placement", "room", "session", "doors", "graph");
        if (args[0].equalsIgnoreCase("boulder")) return args.length <= 2 ? List.of("panel", "tool", "markers", "clearmarkers", "preview", "test", "clear", "list", "info", "debug", "killall") : levelIds();
        return levelIds();
    }

    private void toggleReady(Player player) {
        UUID groupId = player.getUniqueId();
        if (!readyManager.toggle(groupId, player)) {
            readyManager.create(groupId, Set.of(player.getUniqueId()), (id, players) -> {
                Player target = Bukkit.getPlayer(player.getUniqueId());
                if (target != null) enter(target, options(target).level());
            });
            readyManager.toggle(groupId, player);
        }
        boolean ready = readyManager.readyCount(groupId) > 0;
        Bukkit.getPluginManager().callEvent(new net.dark.threecore.dungeons.event.DungeonPlayerReadyEvent(player, ready));
        Text.send(player, ready ? "<green>You are ready.</green>" : "<yellow>You are no longer ready.</yellow>");
    }

    private void restoreCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("3smpcore.dungeon.admin") && !sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
        if (args.length < 2) { Text.send(sender, "<red>Usage: /3smpcore dungeon restore <player></red>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { Text.send(sender, "<red>Player not found.</red>"); return; }
        Text.send(sender, inventoryService.restore(target) ? "<green>Dungeon inventory restored.</green>" : "<yellow>No pending dungeon inventory restore.</yellow>");
    }

    private void forceEndCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("3smpcore.dungeon.admin") && !sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
        if (args.length < 2) { Text.send(sender, "<red>Usage: /3smpcore dungeon forceend <session></red>"); return; }
        try {
            Text.send(sender, endDungeonApi(UUID.fromString(args[1])) ? "<green>Dungeon session ended.</green>" : "<red>Session not found.</red>");
        } catch (IllegalArgumentException ex) {
            Text.send(sender, "<red>Invalid session UUID.</red>");
        }
    }

    private void debugInventoryCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("3smpcore.dungeon.admin") && !sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
        if (args.length < 2) { Text.send(sender, "<red>Usage: /3smpcore dungeon debuginv <player></red>"); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        Text.send(sender, "<gray>" + inventoryService.debug(target.getUniqueId()) + "</gray>");
    }

    private void dungeonBoulder(CommandSender sender, String[] args) {
        if (!sender.hasPermission("3smpcore.dungeon.editor") && !sender.hasPermission("3smpcore.dungeon.admin") && !sender.hasPermission("3smpcore.dungeons.admin")) {
            Text.send(sender, "<red>No permission.</red>");
            return;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "debug";
        switch (mode) {
            case "debug" -> Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder traps:</gradient> <white>" + boulderTrapService.activeCount() + "</white> <gray>active runtime instances</gray>");
            case "killall" -> {
                boulderTrapService.killAll();
                Text.send(sender, "<yellow>Removed all active boulder traps.</yellow>");
            }
            case "tool" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 4) { Text.send(sender, "<yellow>/3smpcore dungeon boulder tool <roomId> <trapId></yellow>"); return; }
                giveBoulderTool(player, args[2], args[3]);
            }
            case "panel", "devpanel" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 4) { Text.send(sender, "<yellow>/3smpcore dungeon boulder panel <roomId> <trapId></yellow>"); return; }
                openBoulderPanel(player, args[2], args[3]);
            }
            case "markers", "showmarkers" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 4) { Text.send(sender, "<yellow>/3smpcore dungeon boulder markers <roomId> <trapId></yellow>"); return; }
                showSavedBoulderMarkers(player, args[2], args[3]);
            }
            case "clearmarkers", "hidemarkers" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                clearBoulderMarkers(player);
                Text.send(player, "<yellow>Cleared boulder dev markers.</yellow>");
            }
            case "preview" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 4) { Text.send(sender, "<yellow>/3smpcore dungeon boulder preview <roomId> <trapId></yellow>"); return; }
                previewBoulder(player, args[2], args[3]);
            }
            case "test" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 4) { Text.send(sender, "<yellow>/3smpcore dungeon boulder test <roomId> <trapId></yellow>"); return; }
                testBoulder(player, args[2], args[3]);
            }
            case "clear" -> {
                if (args.length < 4) { Text.send(sender, "<yellow>/3smpcore dungeon boulder clear <roomId> <trapId></yellow>"); return; }
                clearBoulder(sender, args[2], args[3]);
            }
            case "list" -> {
                if (args.length < 3) { Text.send(sender, "<yellow>/3smpcore dungeon boulder list <roomId></yellow>"); return; }
                listBoulders(sender, args[2]);
            }
            case "info" -> {
                if (args.length < 4) { Text.send(sender, "<yellow>/3smpcore dungeon boulder info <roomId> <trapId></yellow>"); return; }
                infoBoulder(sender, args[2], args[3]);
            }
            default -> Text.send(sender, "<yellow>/3smpcore dungeon boulder panel|tool|markers|clearmarkers|preview|test|clear|list|info|debug|killall</yellow>");
        }
    }

    private void openBoulderPanel(Player player, String roomId, String trapId) {
        RoomBox box = selectedBounds(player);
        if (box == null) { Text.send(player, "<red>Select this room with the dungeon wand first, then open the boulder panel.</red>"); return; }
        DungeonRoomDefinition room = dynamicRoomDefinition(roomId, box);
        BoulderEditSession session = new BoulderEditSession(room.id(), sanitizeConnectorId(trapId), box);
        BoulderTrapDefinition saved = boulderTrap(room.id(), trapId);
        if (saved != null) loadBoulderTrap(session, saved);
        boulderEdits.put(player.getUniqueId(), session);
        Inventory inv = Bukkit.createInventory(new DungeonHolder("boulder-panel:" + session.roomId() + ":" + session.trapId()), 27, "Boulder Dev Panel");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(4, button(Material.DEEPSLATE, "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder:</gradient> <white>" + session.trapId() + "</white>", List.of(
                "<gray>Room:</gray> <white>" + session.roomId() + "</white>",
                "<gray>Bounds:</gray> <white>" + (box.maxX() - box.minX() + 1) + "x" + (box.maxY() - box.minY() + 1) + "x" + (box.maxZ() - box.minZ() + 1) + "</white>",
                statusLine(session)
        )));
        inv.setItem(10, button(Material.MACE, "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder Tool</gradient>", List.of("<gray>Click spawn/path/trigger points.</gray>", "<gray>Q saves, sneak+Q clears temp data.</gray>")));
        inv.setItem(12, button(Material.ARMOR_STAND, "<aqua>Show Armor Stand Path</aqua>", List.of("<gray>Spawns clean visible markers for spawn, path, and trigger.</gray>")));
        inv.setItem(14, button(Material.EMERALD_BLOCK, "<green>Save Trap</green>", List.of("<gray>Saves current tool data into rooms.yml.</gray>")));
        inv.setItem(16, button(Material.BARRIER, "<red>Clear Markers</red>", List.of("<gray>Removes temporary boulder dev markers.</gray>")));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
        showBoulderSessionMarkers(player, session);
    }

    private String statusLine(BoulderEditSession session) {
        return "<gray>Spawn:</gray> <white>" + (session.spawn() == null ? "missing" : vectorInts(session.spawn())) + "</white> <gray>Path:</gray> <white>" + session.path().size() + "</white> <gray>Trigger:</gray> <white>" + (session.triggerMin() != null && session.triggerMax() != null ? "set" : "missing") + "</white>";
    }

    private void handleBoulderPanelClick(Player player, String context, int raw) {
        String[] parts = context.split(":", 3);
        if (parts.length < 3) return;
        BoulderEditSession session = boulderEdits.get(player.getUniqueId());
        if (session == null || !session.roomId().equalsIgnoreCase(parts[1]) || !session.trapId().equalsIgnoreCase(parts[2])) {
            RoomBox box = selectedBounds(player);
            if (box == null) { Text.send(player, "<red>Select room bounds again, then reopen this panel.</red>"); return; }
            session = new BoulderEditSession(parts[1], parts[2], box);
            BoulderTrapDefinition saved = boulderTrap(parts[1], parts[2]);
            if (saved != null) loadBoulderTrap(session, saved);
            boulderEdits.put(player.getUniqueId(), session);
        }
        switch (raw) {
            case 10 -> {
                player.getInventory().addItem(boulderTool(session));
                Text.send(player, "<green>Boulder tool created for room " + session.roomId() + ", trap " + session.trapId() + ".</green>");
            }
            case 12 -> showBoulderSessionMarkers(player, session);
            case 14 -> saveBoulderTool(player, false);
            case 16 -> {
                clearBoulderMarkers(player);
                Text.send(player, "<yellow>Cleared boulder dev markers.</yellow>");
            }
            default -> { }
        }
    }

    private void openSelectionBoulderPanel(Player player) {
        RoomBox box = selectedBounds(player);
        if (box == null) {
            Text.send(player, "<red>Select room pos1/pos2 with the wand first.</red>");
            return;
        }
        String roomId = selectionRoomId(box);
        openBoulderPanel(player, roomId, "main_boulder");
    }

    private String selectionRoomId(RoomBox box) {
        return "room_" + box.minX() + "_" + box.minY() + "_" + box.minZ() + "_" + box.maxX() + "_" + box.maxY() + "_" + box.maxZ();
    }

    private void giveBoulderTool(Player player, String roomId, String trapId) {
        RoomBox box = selectedBounds(player);
        if (box == null) { Text.send(player, "<red>Select the room bounds with the dungeon wand first.</red>"); return; }
        DungeonRoomDefinition room = dynamicRoomDefinition(roomId, box);
        BoulderEditSession session = new BoulderEditSession(room.id(), sanitizeConnectorId(trapId), box);
        boulderEdits.put(player.getUniqueId(), session);
        player.getInventory().addItem(boulderTool(session));
        Text.send(player, "<green>Boulder tool created for room " + room.id() + ", trap " + session.trapId() + ".</green>");
        Text.send(player, "<gray>Right click spawn, left click path points, shift-right two trigger corners, Q to save.</gray>");
    }

    private ItemStack boulderTool(BoulderEditSession session) {
        ItemStack stack = new ItemStack(Material.MACE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d><bold>Boulder Tool</bold></gradient> <dark_gray>></dark_gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + session.trapId() + "</gradient>"));
        meta.lore(List.of(
                Text.mm("&7Left Click: add path point"),
                Text.mm("&7Right Click: set spawn point"),
                Text.mm("&7Shift + Left Click: remove last point"),
                Text.mm("&7Shift + Right Click: set trigger corner"),
                Text.mm("&7Drop Item: save trap"),
                Text.mm("&7Sneak + Drop: clear temporary data")
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, ITEM_KEY), PersistentDataType.STRING, BOULDER_TOOL_ID);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "boulder_room"), PersistentDataType.STRING, session.roomId());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "boulder_trap"), PersistentDataType.STRING, session.trapId());
        stack.setItemMeta(meta);
        return stack;
    }

    private void loadBoulderTrap(BoulderEditSession session, BoulderTrapDefinition trap) {
        session.spawn(trap.spawn().clone());
        session.yaw(trap.yaw());
        session.path().clear();
        trap.path().forEach(point -> session.path().add(point.clone()));
        if (trap.triggerMin() != null) session.triggerMin(trap.triggerMin().clone());
        if (trap.triggerMax() != null) session.triggerMax(trap.triggerMax().clone());
    }

    private void handleBoulderToolClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        BoulderEditSession session = boulderEdits.get(player.getUniqueId());
        if (session == null) {
            String room = event.getItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "boulder_room"), PersistentDataType.STRING);
            String trap = event.getItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "boulder_trap"), PersistentDataType.STRING);
            RoomBox box = selectedBounds(player);
            if (box == null || room == null || trap == null) { Text.send(player, "<red>Select room bounds again, then rerun the boulder tool command.</red>"); return; }
            session = new BoulderEditSession(room, trap, box);
            boulderEdits.put(player.getUniqueId(), session);
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) { Text.send(player, "<yellow>Click blocks with the boulder tool.</yellow>"); return; }
        Vector local = localBoulderPoint(player, session, clicked.getLocation());
        if (local == null) return;
        if (player.isSneaking() && event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (!session.path().isEmpty()) session.path().remove(session.path().size() - 1);
            Text.send(player, "<yellow>Removed last boulder path point.</yellow>");
        } else if (player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!session.triggerFirstSet()) {
                session.triggerMin(local);
                session.triggerFirstSet(true);
                Text.send(player, "<green>Trigger pos1 set.</green>");
            } else {
                session.triggerMax(local);
                session.triggerFirstSet(false);
                Text.send(player, "<green>Trigger pos2 set.</green>");
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            session.spawn(local);
            session.yaw(snapYaw(player.getLocation().getYaw()));
            Text.send(player, "<green>Boulder spawn set.</green>");
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            session.path().add(local);
            Text.send(player, "<green>Added path point #" + session.path().size() + ".</green>");
        }
        previewBoulderSession(player, session);
        showBoulderSessionMarkers(player, session);
    }

    private Vector localBoulderPoint(Player player, BoulderEditSession session, Location location) {
        RoomBox box = session.bounds();
        if (location.getBlockX() < box.minX() || location.getBlockX() > box.maxX() || location.getBlockY() < box.minY() || location.getBlockY() > box.maxY() || location.getBlockZ() < box.minZ() || location.getBlockZ() > box.maxZ()) {
            Text.send(player, "<red>That point is outside the selected room bounds.</red>");
            return null;
        }
        return new Vector(location.getBlockX() - box.minX(), location.getBlockY() - box.minY(), location.getBlockZ() - box.minZ());
    }

    private void saveBoulderTool(Player player, boolean clearOnly) {
        BoulderEditSession session = boulderEdits.get(player.getUniqueId());
        if (session == null) { Text.send(player, "<yellow>No active boulder edit session.</yellow>"); return; }
        if (clearOnly) {
            boulderEdits.remove(player.getUniqueId());
            Text.send(player, "<yellow>Cleared temporary boulder editor data.</yellow>");
            return;
        }
        if (session.spawn() == null) { Text.send(player, "<red>Cannot save: missing spawn.</red>"); return; }
        if (session.path().size() < 2) { Text.send(player, "<red>Cannot save: path needs at least 2 points.</red>"); return; }
        if (session.triggerMin() == null || session.triggerMax() == null) { Text.send(player, "<red>Cannot save: trigger region missing.</red>"); return; }
        YamlConfiguration yaml = configs.get("dungeons/rooms.yml");
        ensureRoomSelectionMetadata(yaml, session);
        String path = "rooms." + session.roomId() + ".boulder-traps." + session.trapId();
        yaml.set(path + ".enabled", true);
        yaml.set(path + ".spawn.pos", vectorInts(session.spawn()));
        yaml.set(path + ".spawn.yaw", (double) session.yaw());
        yaml.set(path + ".spawn.pitch", 0.0D);
        yaml.set(path + ".trigger.min", vectorInts(minVector(session.triggerMin(), session.triggerMax())));
        yaml.set(path + ".trigger.max", vectorInts(maxVector(session.triggerMin(), session.triggerMax())));
        yaml.set(path + ".path", session.path().stream().map(this::vectorInts).toList());
        yaml.set(path + ".speed", 0.42D);
        yaml.set(path + ".acceleration", 0.02D);
        yaml.set(path + ".max-speed", 0.85D);
        yaml.set(path + ".kill-radius", 1.6D);
        yaml.set(path + ".vertical-radius", 1.8D);
        yaml.set(path + ".destroy-at-end", true);
        yaml.set(path + ".mythicmob-id", configs.get("dungeons/dungeons.yml").getString("boulder.mythicmob-id", configs.get("dungeons/dungeons.yml").getString("dungeons.boulder-traps.mythicmobs.default-mob-id", "DungeonBoulder")));
        yaml.set(path + ".modelengine-id", configs.get("dungeons/dungeons.yml").getString("boulder.modelengine-id", configs.get("dungeons/dungeons.yml").getString("dungeons.boulder-traps.modelengine.default-model-id", "boulder")));
        try {
            yaml.save(new File(plugin.getDataFolder(), "dungeons/rooms.yml"));
            configs.reload();
            roomRegistry.reload();
            boulderEdits.remove(player.getUniqueId());
            clearBoulderMarkers(player);
            Text.send(player, "<green>Boulder trap saved.</green>");
        } catch (Exception ex) {
            Text.send(player, "<red>Failed to save boulder trap:</red> <white>" + ex.getMessage() + "</white>");
        }
    }

    private void previewBoulder(Player player, String roomId, String trapId) {
        BoulderTrapDefinition trap = boulderTrap(roomId, trapId);
        if (trap == null) { Text.send(player, "<red>Boulder trap not found.</red>"); return; }
        PlacedDungeonRoom room = previewPlacedRoom(player, roomId);
        if (room == null) return;
        boulderTrapService.preview(player, room, trap);
        showSavedBoulderMarkers(player, roomId, trapId);
        Text.send(player, "<green>Boulder preview shown.</green>");
    }

    private void testBoulder(Player player, String roomId, String trapId) {
        BoulderTrapDefinition trap = boulderTrap(roomId, trapId);
        if (trap == null) { Text.send(player, "<red>Boulder trap not found.</red>"); return; }
        PlacedDungeonRoom room = previewPlacedRoom(player, roomId);
        if (room == null) return;
        boulderTrapService.test(player, room, trap);
        Text.send(player, "<green>Boulder test started.</green>");
    }

    private void clearBoulder(CommandSender sender, String roomId, String trapId) {
        YamlConfiguration yaml = configs.get("dungeons/rooms.yml");
        yaml.set("rooms." + roomId.toLowerCase(Locale.ROOT) + ".boulder-traps." + sanitizeConnectorId(trapId), null);
        try {
            yaml.save(new File(plugin.getDataFolder(), "dungeons/rooms.yml"));
            configs.reload();
            roomRegistry.reload();
            Text.send(sender, "<yellow>Boulder trap cleared.</yellow>");
        } catch (Exception ex) {
            Text.send(sender, "<red>Failed to clear boulder trap.</red>");
        }
    }

    private void listBoulders(CommandSender sender, String roomId) {
        DungeonRoomDefinition room = roomRegistry.room(roomId);
        List<String> ids;
        if (room != null) {
            ids = room.boulderTraps().stream().map(BoulderTrapDefinition::id).toList();
        } else {
            ConfigurationSection section = configs.get("dungeons/rooms.yml").getConfigurationSection("rooms." + sanitizeConnectorId(roomId) + ".boulder-traps");
            ids = section == null ? List.of() : section.getKeys(false).stream().sorted().toList();
        }
        Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder traps:</gradient> <white>" + (ids.isEmpty() ? "none" : String.join(", ", ids)) + "</white>");
    }

    private void infoBoulder(CommandSender sender, String roomId, String trapId) {
        BoulderTrapDefinition trap = boulderTrap(roomId, trapId);
        if (trap == null) { Text.send(sender, "<red>Boulder trap not found.</red>"); return; }
        Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder:</gradient> <white>" + trap.id() + "</white> <gray>spawn=" + vectorInts(trap.spawn()) + " path=" + trap.path().size() + " mythic=" + trap.mythicMobId() + " model=" + trap.modelEngineId() + "</gray>");
    }

    private BoulderTrapDefinition boulderTrap(String roomId, String trapId) {
        DungeonRoomDefinition room = roomRegistry.room(roomId);
        String safe = sanitizeConnectorId(trapId);
        if (room != null) return room.boulderTraps().stream().filter(trap -> trap.id().equalsIgnoreCase(safe)).findFirst().orElse(null);
        YamlConfiguration yaml = configs.get("dungeons/rooms.yml");
        ConfigurationSection section = yaml.getConfigurationSection("rooms." + sanitizeConnectorId(roomId) + ".boulder-traps." + safe);
        if (section == null || !section.getBoolean("enabled", true)) return null;
        ConfigurationSection spawn = section.getConfigurationSection("spawn");
        ConfigurationSection trigger = section.getConfigurationSection("trigger");
        List<Vector> points = new ArrayList<>();
        for (Object raw : section.getList("path", List.of())) {
            if (raw instanceof List<?> list && list.size() >= 3) points.add(new Vector(intValue(list.get(0)), intValue(list.get(1)), intValue(list.get(2))));
        }
        return new BoulderTrapDefinition(
                safe,
                vectorFromList(spawn == null ? List.of() : spawn.getIntegerList("pos")),
                spawn == null ? 0.0F : (float) spawn.getDouble("yaw", 0.0D),
                spawn == null ? 0.0F : (float) spawn.getDouble("pitch", 0.0D),
                vectorFromList(trigger == null ? List.of() : trigger.getIntegerList("min")),
                vectorFromList(trigger == null ? List.of() : trigger.getIntegerList("max")),
                points,
                section.getDouble("speed", 0.42D),
                section.getDouble("acceleration", 0.02D),
                section.getDouble("max-speed", 0.85D),
                section.getDouble("kill-radius", 1.6D),
                section.getDouble("vertical-radius", 1.8D),
                section.getBoolean("destroy-at-end", true),
                section.getString("mythicmob-id", ""),
                section.getString("modelengine-id", "")
        );
    }

    private PlacedDungeonRoom previewPlacedRoom(Player player, String roomId) {
        RoomBox box = selectedBounds(player);
        if (box == null) { Text.send(player, "<red>Select the room bounds with the dungeon wand first.</red>"); return null; }
        DungeonRoomDefinition room = dynamicRoomDefinition(roomId, box);
        return PlacedDungeonRoom.place(room, player.getWorld().getName(), new Vector(box.minX(), box.minY(), box.minZ()), DungeonRotation.NONE, -1);
    }

    private void previewBoulderSession(Player player, BoulderEditSession session) {
        PlacedDungeonRoom room = previewPlacedRoom(player, session.roomId());
        if (room == null) return;
        BoulderTrapDefinition trap = new BoulderTrapDefinition(session.trapId(), session.spawn() == null ? new Vector() : session.spawn(), session.yaw(), 0.0F, session.triggerMin(), session.triggerMax(), session.path(), 0.42D, 0.02D, 0.85D, 1.6D, 1.8D, true, "", "");
        boulderTrapService.preview(player, room, trap);
    }

    private void showSavedBoulderMarkers(Player player, String roomId, String trapId) {
        BoulderTrapDefinition trap = boulderTrap(roomId, trapId);
        if (trap == null) { Text.send(player, "<red>Boulder trap not found.</red>"); return; }
        PlacedDungeonRoom room = previewPlacedRoom(player, roomId);
        if (room == null) return;
        clearBoulderMarkers(player);
        spawnBoulderMarker(player, world(room, trap.spawn()), "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder " + trap.id() + " Spawn</gradient>", Material.DEEPSLATE);
        int index = 1;
        Location previous = null;
        for (Vector point : trap.path()) {
            Location current = world(room, point);
            spawnBoulderMarker(player, current, "<aqua>Boulder " + trap.id() + " P" + index++ + "</aqua>", Material.LAPIS_BLOCK);
            if (previous != null) drawLine(player, previous, current, Color.fromRGB(219, 234, 254));
            previous = current;
        }
        spawnTriggerMarkers(player, trap.id(), room, trap.triggerMin(), trap.triggerMax());
        Text.send(player, "<green>Boulder armor stand path shown for room " + roomId + ".</green>");
    }

    private void showBoulderSessionMarkers(Player player, BoulderEditSession session) {
        clearBoulderMarkers(player);
        if (session.spawn() != null) spawnBoulderMarker(player, sessionWorld(session, session.spawn()), "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder " + session.trapId() + " Spawn</gradient>", Material.DEEPSLATE);
        int index = 1;
        Location previous = null;
        for (Vector point : session.path()) {
            Location current = sessionWorld(session, point);
            spawnBoulderMarker(player, current, "<aqua>Boulder " + session.trapId() + " P" + index++ + "</aqua>", Material.LAPIS_BLOCK);
            if (previous != null) drawLine(player, previous, current, Color.fromRGB(219, 234, 254));
            previous = current;
        }
        spawnTriggerSessionMarkers(player, session);
        Text.actionBar(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Boulder panel</gradient> <gray>" + statusLine(session).replace("<gray>", "").replace("</gray>", "").replace("<white>", "").replace("</white>", "") + "</gray>");
    }

    private void spawnTriggerSessionMarkers(Player player, BoulderEditSession session) {
        if (session.triggerMin() == null) return;
        spawnBoulderMarker(player, sessionWorld(session, session.triggerMin()), "<red>Boulder " + session.trapId() + " Trigger 1</red>", Material.REDSTONE_BLOCK);
        if (session.triggerMax() != null) spawnBoulderMarker(player, sessionWorld(session, session.triggerMax()), "<red>Boulder " + session.trapId() + " Trigger 2</red>", Material.REDSTONE_BLOCK);
    }

    private void spawnTriggerMarkers(Player player, String trapId, PlacedDungeonRoom room, Vector min, Vector max) {
        if (min == null) return;
        spawnBoulderMarker(player, world(room, min), "<red>Boulder " + trapId + " Trigger 1</red>", Material.REDSTONE_BLOCK);
        if (max != null) spawnBoulderMarker(player, world(room, max), "<red>Boulder " + trapId + " Trigger 2</red>", Material.REDSTONE_BLOCK);
    }

    private Location sessionWorld(BoulderEditSession session, Vector local) {
        RoomBox box = session.bounds();
        return new Location(Bukkit.getWorld(configs.get("dungeons/dungeons.yml").getString("editor-world", "dungeons_editor")), box.minX() + local.getBlockX() + 0.5D, box.minY() + local.getBlockY(), box.minZ() + local.getBlockZ() + 0.5D);
    }

    private Location world(PlacedDungeonRoom room, Vector local) {
        Vector transformed = room.transform().localToWorld(local);
        World world = Bukkit.getWorld(room.world());
        return new Location(world, transformed.getX() + 0.5D, transformed.getY(), transformed.getZ() + 0.5D);
    }

    private void spawnBoulderMarker(Player player, Location location, String name, Material helmet) {
        if (location == null || location.getWorld() == null) return;
        Location spawn = location.clone();
        spawn.setYaw(0.0F);
        spawn.setPitch(0.0F);
        ArmorStand stand = spawn.getWorld().spawn(spawn, ArmorStand.class, armorStand -> {
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setMarker(false);
            armorStand.setSmall(true);
            armorStand.setVisible(true);
            armorStand.setGlowing(true);
            armorStand.setCustomNameVisible(true);
            armorStand.customName(Text.mm(name));
            armorStand.getEquipment().setHelmet(new ItemStack(helmet));
        });
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "dungeon_editor_marker"), PersistentDataType.STRING, "boulder_preview");
        boulderMarkerPreviews.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>()).add(stand.getUniqueId());
    }

    private void drawLine(Player player, Location from, Location to, Color color) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(1, (int) Math.ceil(delta.length() * 3.0D));
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9F);
        for (int i = 0; i <= steps; i++) {
            Location point = from.clone().add(delta.clone().multiply(i / (double) steps)).add(0.0D, 0.35D, 0.0D);
            player.spawnParticle(Particle.DUST, point, 1, 0.0D, 0.0D, 0.0D, dust);
        }
    }

    private void clearBoulderMarkers(Player player) {
        List<UUID> ids = boulderMarkerPreviews.remove(player.getUniqueId());
        if (ids != null) {
            for (UUID id : ids) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        NamespacedKey key = new NamespacedKey(plugin, "dungeon_editor_marker");
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof ArmorStand)) continue;
            String value = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if ("boulder_preview".equals(value) && entity.getLocation().distanceSquared(player.getLocation()) < 256.0D * 256.0D) entity.remove();
        }
    }

    private List<Integer> vectorInts(Vector vector) {
        return List.of(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    private DungeonRoomDefinition dynamicRoomDefinition(String roomId, RoomBox box) {
        String safe = sanitizeConnectorId(roomId);
        DungeonRoomDefinition loaded = roomRegistry.room(safe);
        if (loaded != null) return loaded;
        YamlConfiguration yaml = configs.get("dungeons/rooms.yml");
        String base = "rooms." + safe;
        int sizeX = yaml.getInt(base + ".size.x", box.maxX() - box.minX() + 1);
        int sizeY = yaml.getInt(base + ".size.y", box.maxY() - box.minY() + 1);
        int sizeZ = yaml.getInt(base + ".size.z", box.maxZ() - box.minZ() + 1);
        return new DungeonRoomDefinition(
                safe,
                yaml.getString(base + ".schematic", safe + ".schem"),
                RoomType.NORMAL,
                yaml.getInt(base + ".weight", 1),
                new Vector(sizeX, sizeY, sizeZ),
                List.of(),
                List.of(),
                java.util.Set.of(),
                java.util.Set.of(),
                yaml.getInt(base + ".difficulty-weight", 1),
                List.of(),
                List.of(),
                List.of(),
                org.bukkit.block.BlockFace.SOUTH,
                null,
                DungeonRoomDefinition.RotationOrigin.roomMin()
        );
    }

    private void ensureRoomSelectionMetadata(YamlConfiguration yaml, BoulderEditSession session) {
        RoomBox box = session.bounds();
        String base = "rooms." + session.roomId();
        if (!yaml.contains(base + ".schematic")) yaml.set(base + ".schematic", session.roomId() + ".schem");
        if (!yaml.contains(base + ".type")) yaml.set(base + ".type", "NORMAL");
        if (!yaml.contains(base + ".weight")) yaml.set(base + ".weight", 1);
        yaml.set(base + ".size.x", box.maxX() - box.minX() + 1);
        yaml.set(base + ".size.y", box.maxY() - box.minY() + 1);
        yaml.set(base + ".size.z", box.maxZ() - box.minZ() + 1);
        if (!yaml.contains(base + ".base-facing")) yaml.set(base + ".base-facing", "SOUTH");
        if (!yaml.contains(base + ".rotation-origin.mode")) yaml.set(base + ".rotation-origin.mode", "ROOM_MIN");
    }

    private Vector vectorFromList(List<Integer> list) {
        return new Vector(list.size() > 0 ? list.get(0) : 0, list.size() > 1 ? list.get(1) : 0, list.size() > 2 ? list.get(2) : 0);
    }

    private Vector listVector(List<?> list) {
        return new Vector(numberValue(list, 0), numberValue(list, 1), numberValue(list, 2));
    }

    private double numberValue(List<?> list, int index) {
        if (list == null || list.size() <= index) return 0.0D;
        Object value = list.get(index);
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0.0D;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Vector minVector(Vector a, Vector b) {
        return new Vector(Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()));
    }

    private Vector maxVector(Vector a, Vector b) {
        return new Vector(Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    private void dungeonDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("3smpcore.dungeon.admin") && !sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "session";
        switch (mode) {
            case "generate", "graph", "layout", "bounds", "failed-placement" -> {
                DungeonLayout layout = graphGenerator.generate("debug", sender instanceof Player player ? player.getWorld().getName() : configs.get("dungeons/dungeons.yml").getString("world", "world"), configs.get("dungeons/dungeons.yml").getInt("levels.jungle.y", 80), true);
                Bukkit.getPluginManager().callEvent(new DungeonGenerateEvent(layout));
                Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Generated debug layout:</gradient> <white>" + layout.rooms().size() + " rooms, " + layout.connections().size() + " connections in " + layout.planningMillis() + "ms</white>");
                for (String line : layout.debug()) Text.send(sender, "<gray>" + line + "</gray>");
                if (sender instanceof Player player && (mode.equals("layout") || mode.equals("bounds") || mode.equals("connectors"))) visualizeLayout(player, layout);
            }
            case "validate" -> {
                DungeonLayout layout = graphGenerator.generate("debug", sender instanceof Player player ? player.getWorld().getName() : configs.get("dungeons/dungeons.yml").getString("world", "world"), configs.get("dungeons/dungeons.yml").getInt("levels.jungle.y", 80), true);
                DungeonValidationResult result = dungeonValidator.validate(layout, roomRegistry.settings().bossRoomRequired());
                Text.send(sender, result.valid() ? "<green>Generated layout validates.</green>" : "<red>Validation failed:</red> <white>" + String.join("; ", result.failures()) + "</white>");
            }
            case "room" -> {
                if (args.length < 3) { Text.send(sender, "<yellow>/3smpcore dungeon debug room <roomId></yellow>"); return; }
                DungeonRoomDefinition room = roomRegistry.room(args[2]);
                if (room == null) { Text.send(sender, "<red>Unknown room.</red>"); return; }
                Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Room:</gradient> <white>" + room.id() + "</white> <gray>type=" + room.type() + " size=" + room.size().getBlockX() + "x" + room.size().getBlockY() + "x" + room.size().getBlockZ() + "</gray>");
                Text.send(sender, "<gray>Spawns:</gray> <white>" + room.spawns().size() + "</white> <gray>Traps:</gray> <white>" + room.traps().size() + "</white>");
                room.traps().forEach(trap -> Text.send(sender, "<dark_gray>-</dark_gray> <white>" + trap.id() + "</white> <gray>type=" + trap.type() + " mob=" + resolvedTrapMobId(trap.type(), trap.mythicMobId()) + " pos=" + trap.localPosition() + " facing=" + trap.facing() + "</gray>"));
            }
            case "connectors" -> {
                if (args.length < 3) { Text.send(sender, "<yellow>/3smpcore dungeon debug connectors <roomId></yellow>"); return; }
                DungeonRoomDefinition room = roomRegistry.room(args[2]);
                if (room == null) { Text.send(sender, "<red>Unknown room.</red>"); return; }
                room.connectors().forEach(connector -> Text.send(sender, "<gray>" + connector.role() + " " + connector.id() + " " + connector.type() + " facing=" + connector.facing() + " pos=" + connector.localPosition() + "</gray>"));
            }
            case "rotation" -> {
                if (args.length < 3) { Text.send(sender, "<yellow>/3smpcore dungeon debug rotation <roomId></yellow>"); return; }
                DungeonRoomDefinition room = roomRegistry.room(args[2]);
                if (room == null) { Text.send(sender, "<red>Unknown room.</red>"); return; }
                Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Rotation debug:</gradient> <white>" + room.id() + "</white> <gray>base-facing=" + room.baseFacing() + " size=" + room.size().getBlockX() + "x" + room.size().getBlockY() + "x" + room.size().getBlockZ() + "</gray>");
                if (room.facingMarker() != null) Text.send(sender, "<gray>Facing marker pos=" + room.facingMarker().localPosition() + " facing=" + room.facingMarker().facing() + "</gray>");
                for (net.dark.threecore.dungeons.engine.DungeonRotation requested : net.dark.threecore.dungeons.engine.DungeonRotation.values()) {
                    net.dark.threecore.dungeons.engine.DungeonRotation effective = room.effectiveRotation(requested);
                    net.dark.threecore.dungeons.engine.RoomTransform transform = new net.dark.threecore.dungeons.engine.RoomTransform(new org.bukkit.util.Vector(), effective, room.size().getBlockX(), room.size().getBlockY(), room.size().getBlockZ());
                    Text.send(sender, "<gray>requested=" + requested + " effective=" + effective + " bounds=" + transform.rotatedSize().getBlockX() + "x" + transform.rotatedSize().getBlockY() + "x" + transform.rotatedSize().getBlockZ() + "</gray>");
                    for (var connector : room.connectors()) {
                        var rotated = transform.rotatedConnector(connector);
                        Text.send(sender, "<dark_gray>-</dark_gray> <white>" + connector.id() + "</white> <gray>" + connector.facing() + " " + connector.localPosition() + " -> " + rotated.facing() + " " + rotated.localPosition() + "</gray>");
                    }
                }
            }
            case "pivot", "transform" -> {
                if (args.length < 3) { Text.send(sender, "<yellow>/3smpcore dungeon debug " + mode + " <roomId></yellow>"); return; }
                DungeonRoomDefinition room = roomRegistry.room(args[2]);
                if (room == null) { Text.send(sender, "<red>Unknown room.</red>"); return; }
                Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Pivot:</gradient> <white>" + room.rotationOrigin().mode() + "</white> <gray>pos=" + room.rotationOrigin().resolve(room.size(), room.facingMarker()) + " base-facing=" + room.baseFacing() + "</gray>");
                for (net.dark.threecore.dungeons.engine.DungeonRotation requested : net.dark.threecore.dungeons.engine.DungeonRotation.values()) {
                    var transform = new net.dark.threecore.dungeons.engine.RoomTransform(new org.bukkit.util.Vector(), room.effectiveRotation(requested), room.size().getBlockX(), room.size().getBlockY(), room.size().getBlockZ(), room.rotationOrigin().resolve(room.size(), room.facingMarker()));
                    Text.send(sender, "<gray>" + requested + " -> effective=" + room.effectiveRotation(requested) + " rotatedSize=" + transform.rotatedSize().getBlockX() + "x" + transform.rotatedSize().getBlockY() + "x" + transform.rotatedSize().getBlockZ() + "</gray>");
                }
            }
            case "paste" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 4) { Text.send(player, "<yellow>/3smpcore dungeon debug paste <roomId> <rotation></yellow>"); return; }
                String id = args[2].toLowerCase(Locale.ROOT);
                StructureRotation rotation = parseStructureRotation(args[3]);
                RoomReservation room = new RoomReservation("debug:paste:" + id, player.getWorld().getName(), "debug", player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ(), rotation);
                pasteTemplate(id, room);
                RoomBox planned = box(room, id);
                drawBox(player, new org.bukkit.util.BoundingBox(planned.minX(), planned.minY(), planned.minZ(), planned.maxX() + 1.0D, planned.maxY() + 1.0D, planned.maxZ() + 1.0D), Color.fromRGB(250, 204, 21));
                Text.send(player, "<green>Pasted debug room.</green> <gray>room=" + id + " rotation=" + rotation + " planned=" + planned + "</gray>");
            }
            case "doors" -> Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Dungeon doors:</gradient> <white>" + doorManager.doors().size() + "</white>");
            case "session" -> Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Runtime sessions:</gradient> <white>" + runtimeSessions.size() + "</white> <gray>players=" + playerRuntimeSessions.size() + "</gray>");
            default -> Text.send(sender, "<yellow>/3smpcore dungeon debug generate|validate|layout|connectors|rotation|bounds|failed-placement|room|session|doors|graph</yellow>");
        }
    }

    private void visualizeLayout(Player player, DungeonLayout layout) {
        if (layout == null || layout.rooms().isEmpty()) return;
        for (var room : layout.rooms()) {
            drawBox(player, room.box(), Color.fromRGB(96, 165, 250));
            for (var connector : room.connectors()) {
                Color color = connector.connector().role() == net.dark.threecore.dungeons.engine.ConnectorRole.ENTRANCE
                    ? Color.fromRGB(96, 165, 250)
                    : connector.connector().type().vertical() ? Color.WHITE : Color.fromRGB(250, 204, 21);
                Location location = connector.location();
                if (location != null) {
                    player.spawnParticle(Particle.DUST, location.clone().add(0.5, 0.7, 0.5), 12, 0.18, 0.18, 0.18, new Particle.DustOptions(color, 1.4F));
                    Location arrow = location.clone().add(connector.facing().getModX() * 0.75, connector.facing().getModY() * 0.75, connector.facing().getModZ() * 0.75);
                    player.spawnParticle(Particle.DUST, arrow.clone().add(0.5, 0.7, 0.5), 8, 0.08, 0.08, 0.08, new Particle.DustOptions(color, 1.1F));
                }
            }
        }
        Text.send(player, "<gray>Debug particles drawn: blue entrances/bounds, gold exits, white vertical connectors.</gray>");
    }

    private void drawBox(Player player, org.bukkit.util.BoundingBox box, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9F);
        World world = player.getWorld();
        double minX = box.getMinX(), minY = box.getMinY(), minZ = box.getMinZ();
        double maxX = box.getMaxX(), maxY = box.getMaxY(), maxZ = box.getMaxZ();
        for (double x = minX; x <= maxX; x += 4.0D) {
            particle(player, world, x, minY, minZ, dust); particle(player, world, x, maxY, minZ, dust);
            particle(player, world, x, minY, maxZ, dust); particle(player, world, x, maxY, maxZ, dust);
        }
        for (double y = minY; y <= maxY; y += 4.0D) {
            particle(player, world, minX, y, minZ, dust); particle(player, world, maxX, y, minZ, dust);
            particle(player, world, minX, y, maxZ, dust); particle(player, world, maxX, y, maxZ, dust);
        }
        for (double z = minZ; z <= maxZ; z += 4.0D) {
            particle(player, world, minX, minY, z, dust); particle(player, world, maxX, minY, z, dust);
            particle(player, world, minX, maxY, z, dust); particle(player, world, maxX, maxY, z, dust);
        }
    }

    private void particle(Player player, World world, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, new Location(world, x, y, z), 1, 0, 0, 0, dust);
    }

    private void openDungeonEntry(Player player) {
        if (isDungeonSpawnWorld(player.getWorld())) openMenu(player);
        else teleportDungeonSpawn(player);
    }

    private void teleportDungeonSpawn(Player player) {
        Location spawn = readConfiguredLocation("spawn");
        if (spawn == null) { Text.send(player, "<red>Dungeon spawn is not configured.</red>"); return; }
        saveCurrentInventoryForCurrentWorld(player);
        clearPlayerState(player);
        clearDungeonVision(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(spawn);
        loadDungeonProfileWithStarterKit(player);
        clearDungeonHubItems(player);
        clearSpawnTools(player);
        applyDungeonSpawnBuffs(player);
        Text.send(player, "<gradient:#4c1d95:#a78bfa>Sent to dungeon spawn.</gradient>");
    }

    private void setDungeonSpawn(Player player) {
        if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        var yaml = configs.get("dungeons/dungeons.yml");
        writeLocation(yaml, "spawn", player.getLocation());
        try { yaml.save(new File(plugin.getDataFolder(), "dungeons/dungeons.yml")); Text.send(player, "<green>Dungeon spawn saved.</green>"); } catch (Exception ex) { Text.send(player, "<red>Failed to save dungeon spawn.</red>"); }
        debug(player, "<gray>Saved dungeon spawn at</gray> <white>" + player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + "</white>");
    }

    private void createTemplateWorld(Player player) {
        if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        var yaml = configs.get("dungeons/dungeons.yml");
        if (yaml.getBoolean(TEMPLATE_USED_PATH, false)) {
            Text.send(player, "<red>The dungeon template initializer can only be used once globally.</red>");
            return;
        }
        World spawn = dungeonSpawnWorld();
        if (spawn == null) {
            Text.send(player, "<red>Dungeon spawn world could not be created.</red>");
            return;
        }
        yaml.set(TEMPLATE_USED_PATH, true);
        try {
            yaml.save(new File(plugin.getDataFolder(), "dungeons/dungeons.yml"));
        } catch (Exception ex) {
            Text.send(player, "<red>Failed to persist template usage flag.</red>");
            return;
        }
        if (survivalService != null) survivalService.saveCurrentProfile(player);
        clearPlayerState(player);
        clearDungeonVision(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(spawn, 0.5D, Math.max(80, spawn.getHighestBlockYAt(0, 0) + 2.0D), 0.5D));
        loadDungeonProfileWithStarterKit(player);
        clearDungeonHubItems(player);
        debug(player, "<gray>Dungeon template initializer used once and locked globally.</gray>");
        Text.send(player, "<green>Dungeon template world prepared.</green> <gray>The one-time initializer is now locked.</gray>");
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder("bank"), 54, dungeonsMenuTitle());
        inv.setItem(22, button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Dungeons Bank</gradient>", List.of(
                "<yellow>Coming soon.</yellow>",
                "<gray>The dungeon bank is staged as a full double chest UI.</gray>"
        )));
        player.openInventory(inv);
    }

    private String dungeonsMenuTitle() {
        return guiTitle(":offset_-15:&f:dungeons_menu:", "dungeons_menu", "\\uA437");
    }

    private String guiTitle(String raw, String token, String symbol) {
        String output = applyItemsAdderFontImages(raw);
        output = output.replace(":" + token + ":", decodeUnicodeEscapes(symbol));
        return decodeUnicodeEscapes(output);
    }

    private String applyItemsAdderFontImages(String input) {
        if (input == null || input.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return input == null ? "" : input;
        try {
            Class<?> wrapper = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            java.lang.reflect.Method replace = wrapper.getMethod("replaceFontImages", String.class);
            Object result = replace.invoke(null, input);
            return result instanceof String text ? text : input;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return input;
        }
    }

    private String decodeUnicodeEscapes(String input) {
        if (input == null || input.isBlank() || !input.contains("\\u")) return input == null ? "" : input;
        StringBuilder out = new StringBuilder(input.length());
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

    private void openLevelMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder("levels"), 27, "Dungeon Level");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        int[] slots = {10, 11, 12, 13, 14}; int idx = 0;
        for (String level : levelIds()) {
            if (idx >= slots.length) break;
            boolean comingSoon = configs.get("dungeons/dungeons.yml").getBoolean("levels." + level + ".coming-soon", false);
            inv.setItem(slots[idx++], button(comingSoon ? Material.BARRIER : Material.MAP, configs.get("dungeons/dungeons.yml").getString("levels." + level + ".display-name", level), List.of(comingSoon ? "<red>Coming soon.</red>" : "<gray>Click to select.</gray>")));
        }
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
    }

    private void openDifficultyMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder("difficulty"), 27, "Dungeon Difficulty");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(10, button(Material.WHITE_WOOL, "<green>Easy</green>", List.of("<gray>Money per kill:</gray> <white>" + moneyPerKill("easy") + "</white>")));
        inv.setItem(12, button(Material.YELLOW_WOOL, "<yellow>Normal</yellow>", List.of("<gray>Money per kill:</gray> <white>" + moneyPerKill("normal") + "</white>")));
        inv.setItem(14, button(Material.RED_WOOL, "<red>Hard</red>", List.of("<gray>Money per kill:</gray> <white>" + moneyPerKill("hard") + "</white>")));
        inv.setItem(16, button(canUseNightmare(player, options(player).level()) ? Material.BLACK_WOOL : Material.BARRIER, "<gradient:#7f1d1d:#020617>Nightmare</gradient>", List.of("<gray>Money per kill:</gray> <white>" + moneyPerKill("nightmare") + "</white>", "<gray>Requires:</gray> <white>Easy, Normal and Hard clears</white>", canUseNightmare(player, options(player).level()) ? "<green>Unlocked.</green>" : "<red>Locked for this level.</red>")));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
    }

    private void openPartyMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder("party"), 27, "Dungeon Party");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(11, button(Material.LIGHT_BLUE_BANNER, "<aqua>Solo</aqua>", List.of("<gray>Run this dungeon alone.</gray>")));
        inv.setItem(15, button(Material.ORANGE_BANNER, "<gradient:#f4cd2a:#eda323:#d28d0d>Party</gradient>", List.of(partyService != null && partyService.isLeader(player.getUniqueId()) ? "<gray>Run with your party.</gray>" : partyService != null && partyService.isInParty(player.getUniqueId()) ? "<yellow>Only the party leader can choose this.</yellow>" : "<red>You are not in a party.</red>")));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
    }

    public void enablePartyMode(Player player) {
        if (partyService != null && partyService.isInParty(player.getUniqueId()) && !partyService.isLeader(player.getUniqueId())) {
            Text.send(player, "<yellow>Only the party leader can start a party dungeon.</yellow>");
            return;
        }
        options(player).party()[0] = true;
    }
    public void openDungeonEditor(Player player) {
        World world = dungeonEditorWorld();
        if (world == null) {
            Text.send(player, "<red>Dungeon editor world could not be loaded.</red>");
            return;
        }
        if (survivalService != null) survivalService.saveCurrentProfile(player);
        clearPlayerState(player);
        clearDungeonVision(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 0.5D, world.getHighestBlockYAt(0, 0) + 2.0D, 0.5D));
        clearPlayerState(player);
        clearDungeonHubItems(player);
        giveDevTool(player);
        openDevToolbox(player);
        debug(player, "<gray>Editor session opened in <white>" + world.getName() + "</white>.</gray>");
        Text.send(player, "<gradient:#4c1d95:#a78bfa>Dungeon editor opened.</gradient>");
    }

    private void leaveDungeonEditor(Player player) {
        if (!isDungeonEditorWorld(player.getWorld())) {
            Text.send(player, "<gray>You are not in the dungeon editor.</gray>");
            return;
        }
        clearPlayerState(player);
        stripSpawnTools(player);
        player.setGameMode(GameMode.SURVIVAL);
        teleportDungeonSpawn(player);
        Text.send(player, "<green>Left the dungeon editor.</green> <gray>Your editor tools were cleared.</gray>");
        debug(player, "<gray>Dungeon editor session closed and player returned to dungeon spawn.</gray>");
    }

    public void leave(Player player) {
        net.dark.threecore.dungeons.runtime.DungeonSession session = getDungeonSession(player);
        if (session == null && !isDungeonPlayer(player) && !isDungeonWorld(player.getWorld())) {
            Text.send(player, "<gray>You are not in a dungeon.</gray>");
            return;
        }
        if (session != null) {
            leaveRuntimeDungeon(player, session, true);
            Text.send(player, "<green>Left the dungeon.</green>");
            return;
        }
        saveDungeonProfile(player);
        World instance = instanceWorldsByPlayer.get(player.getUniqueId());
        clearDungeonRunState(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        teleportDungeonSpawn(player);
        cleanupDungeonInstanceIfEmpty(instance);
    }

    public UUID startDungeonApi(Collection<Player> players, String dungeonId) {
        if (players == null || players.isEmpty()) return null;
        Player leader = players.iterator().next();
        prepareDungeonGeneration();
        String id = dungeonId == null || dungeonId.isBlank() ? "jungle" : dungeonId.toLowerCase(Locale.ROOT);
        String difficulty = options(leader).difficulty()[0];
        World world = createDungeonInstanceWorld(leader.getUniqueId(), id);
        if (world == null) return null;
        DungeonLayout layout = graphGenerator.generate(id, world.getName(), configs.get("dungeons/dungeons.yml").getInt("levels." + id + ".y", 80), debugEnabled());
        if (layout.rooms().isEmpty()) {
            debug(leader, "<red>Room-aware generation failed. See debug generate for reasons.</red>");
            for (String line : layout.debug().stream().limit(25).toList()) debug(leader, "<gray>" + line + "</gray>");
            cleanupDungeonInstanceIfEmpty(world);
            return null;
        }
        DungeonValidationResult validation = dungeonValidator.validate(layout, roomRegistry.settings().bossRoomRequired());
        if (!validation.valid()) {
            debug(leader, "<yellow>Generated dungeon has validation notes, forcing usable layout:</yellow> <white>" + String.join("; ", validation.failures()) + "</white>");
            for (String line : layout.debug().stream().limit(25).toList()) debug(leader, "<gray>" + line + "</gray>");
        }
        if (layout.startRoom() == null) {
            debug(leader, "<red>Generated dungeon rejected because it has no start room.</red>");
            cleanupDungeonInstanceIfEmpty(world);
            return null;
        }
        pasteGeneratedLayout(layout);
        Bukkit.getPluginManager().callEvent(new DungeonGenerateEvent(layout));
        UUID sessionId = UUID.randomUUID();
        Set<UUID> members = new HashSet<>();
        for (Player player : players) {
            inventoryService.save(sessionId, player);
            members.add(player.getUniqueId());
            playerRuntimeSessions.put(player.getUniqueId(), sessionId);
            ACTIVE_DUNGEON_PLAYERS.add(player.getUniqueId());
            instanceWorldsByPlayer.put(player.getUniqueId(), world);
        }
        net.dark.threecore.dungeons.runtime.DungeonSession session = new net.dark.threecore.dungeons.runtime.DungeonSession(sessionId, id, difficulty, members, layout);
        runtimeSessions.put(sessionId, session);
        Bukkit.getPluginManager().callEvent(new DungeonStartEvent(session));
        spawnDungeonStartContent(session);
        Location spawn = generatedRoomSpawn(layout.startRoom());
        if (spawn == null) spawn = layout.startRoom().originLocation();
        int offsetIndex = 0;
        for (Player player : players) {
            clearPlayerState(player);
            clearDungeonVision(player);
            player.setGameMode(GameMode.SURVIVAL);
            Location target = spawn == null ? null : spawn.clone().add((offsetIndex % 3) * 0.75D, 0.0D, (offsetIndex / 3) * 0.75D);
            if (target != null) player.teleport(target);
            loadDungeonProfileWithStarterKit(player);
            clearDungeonHubItems(player);
            offsetIndex++;
        }
        startHealthIndicatorTask();
        Bukkit.getScheduler().runTaskLater(plugin, doorManager::openAll, configs.get("dungeons/dungeons.yml").getInt("dungeon-door.open-delay-seconds", 5) * 20L);
        return sessionId;
    }

    private void prepareDungeonGeneration() {
        if (!configs.get("dungeons/dungeons.yml").getBoolean("generation.force-refresh-before-run", true)) return;
        configs.reload();
        roomRegistry.reload();
    }

    private void pasteGeneratedLayout(DungeonLayout layout) {
        if (layout == null) return;
        YamlConfiguration templates = configs.get("dungeons/templates.yml");
        for (var room : layout.rooms()) {
            if (!templates.isConfigurationSection("templates." + room.definition().id())) {
                debug(null, "<yellow>No paste template found for room-engine room</yellow> <white>" + room.definition().id() + "</white><yellow>; layout metadata remains valid.</yellow>");
                continue;
            }
            pasteTemplate(room.definition().id(), new RoomReservation(
                "room-engine:" + room.graphIndex() + ":" + room.definition().id(),
                room.world(),
                layout.dungeonId(),
                room.origin().getBlockX(),
                room.origin().getBlockY(),
                room.origin().getBlockZ(),
                toStructureRotation(room.rotation())
            ));
            restoreBossDoorBlockStates(room);
        }
        capUnconnectedDungeonOpenings(layout);
    }

    private void restoreBossDoorBlockStates(PlacedDungeonRoom room) {
        if (room == null) return;
        YamlConfiguration rooms = configs.get("dungeons/rooms.yml");
        String base = "rooms." + room.definition().id() + ".boss-door";
        if (!rooms.getBoolean(base + ".enabled", false)) return;
        World world = Bukkit.getWorld(room.world());
        if (world == null) return;
        List<String> states = rooms.getStringList(base + ".blockstates");
        if (states.isEmpty()) return;
        StructureRotation rotation = toStructureRotation(room.rotation());
        int restored = 0;
        for (String raw : states) {
            String[] parts = raw.split("\\|", 3);
            if (parts.length < 2) continue;
            String[] xyz = parts[0].split(",");
            if (xyz.length < 3) continue;
            try {
                int lx = Integer.parseInt(xyz[0]);
                int ly = Integer.parseInt(xyz[1]);
                int lz = Integer.parseInt(xyz[2]);
                Vector worldVector = room.transform().localToWorld(new Vector(lx, ly, lz));
                Block block = world.getBlockAt(worldVector.getBlockX(), worldVector.getBlockY(), worldVector.getBlockZ());
                Material material = parseMaterial(parts[1]);
                block.setType(material, false);
                if (parts.length >= 3 && !parts[2].isBlank()) {
                    BlockData data = Bukkit.createBlockData(parts[2]);
                    rotateBlockData(data, rotation);
                    block.setBlockData(data, false);
                }
                restored++;
            } catch (Exception ignored) {
            }
        }
        if (restored > 0) debug(null, "<gray>Restored boss door blockstates for</gray> <white>" + room.definition().id() + "</white> <gray>blocks:</gray> <white>" + restored + "</white>");
    }

    private void capUnconnectedDungeonOpenings(DungeonLayout layout) {
        if (layout == null || layout.rooms().isEmpty()) return;
        Set<String> connected = new HashSet<>();
        for (var connection : layout.connections()) {
            connected.add(connectorKey(connection.fromRoom(), connection.fromConnector()));
            connected.add(connectorKey(connection.toRoom(), connection.toConnector()));
        }
        Material capMaterial = parseMaterial(configs.get("dungeons/dungeons.yml").getString(
            "generation.room-engine.unconnected-opening-cap-material",
            configs.get("dungeons/dungeons.yml").getString("dungeon-door.material", "POLISHED_BLACKSTONE")
        ));
        int capped = 0;
        int inactiveEntrances = 0;
        int fallbackOpenings = 0;
        for (PlacedDungeonRoom room : layout.rooms()) {
            World world = Bukkit.getWorld(room.world());
            if (world == null) continue;
            for (PlacedConnector connector : room.connectors()) {
                if (connected.contains(connectorKey(room.graphIndex(), connector.connector().id()))) continue;
                capConnectorOpening(world, connector, capMaterial);
                capped++;
                if (connector.connector().role() == net.dark.threecore.dungeons.engine.ConnectorRole.ENTRANCE) inactiveEntrances++;
                else fallbackOpenings++;
            }
        }
        if (capped > 0) debug(null, "<gray>Capped</gray> <white>" + inactiveEntrances + "</white> <gray>inactive entrance(s) and</gray> <white>" + fallbackOpenings + "</white> <gray>fallback opening(s) after paste.</gray>");
    }

    private String connectorKey(int roomIndex, String connectorId) {
        return roomIndex + ":" + (connectorId == null ? "" : connectorId.toLowerCase(Locale.ROOT));
    }

    private void capConnectorOpening(World world, PlacedConnector connector, Material material) {
        if (world == null || connector == null || material == null || material.isAir()) return;
        int x = connector.worldPosition().getBlockX();
        int y = connector.worldPosition().getBlockY();
        int z = connector.worldPosition().getBlockZ();
        int width = Math.max(1, connector.connector().width());
        int height = Math.max(1, connector.connector().height());
        BlockFace facing = connector.facing() == null ? BlockFace.NORTH : connector.facing();
        int depth = Math.max(1, configs.get("dungeons/dungeons.yml").getInt("generation.room-engine.unconnected-opening-cap-depth", 2));
        for (int layer = 0; layer < depth; layer++) {
            fillConnectorCapLayer(world, x, y, z, width, height, facing, material, layer);
        }
    }

    private void fillConnectorCapLayer(World world, int x, int y, int z, int width, int height, BlockFace facing, Material material, int layer) {
        int inwardX = -facing.getModX() * layer;
        int inwardY = -facing.getModY() * layer;
        int inwardZ = -facing.getModZ() * layer;
        if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
            for (int dx = 0; dx < width; dx++) {
                for (int dy = 0; dy < height; dy++) {
                    world.getBlockAt(x + dx + inwardX, y + dy + inwardY, z + inwardZ).setType(material, false);
                }
            }
            return;
        }
        if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
            for (int dz = 0; dz < width; dz++) {
                for (int dy = 0; dy < height; dy++) {
                    world.getBlockAt(x + inwardX, y + dy + inwardY, z + dz + inwardZ).setType(material, false);
                }
            }
            return;
        }
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                world.getBlockAt(x + dx + inwardX, y + inwardY, z + dz + inwardZ).setType(material, false);
            }
        }
    }

    private StructureRotation toStructureRotation(net.dark.threecore.dungeons.engine.DungeonRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> StructureRotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> StructureRotation.CLOCKWISE_180;
            case COUNTERCLOCKWISE_90 -> StructureRotation.COUNTERCLOCKWISE_90;
            case NONE -> StructureRotation.NONE;
        };
    }

    public boolean endDungeonApi(UUID sessionId) {
        net.dark.threecore.dungeons.runtime.DungeonSession session = runtimeSessions.remove(sessionId);
        if (session == null) return false;
        Bukkit.getPluginManager().callEvent(new DungeonEndEvent(session));
        cleanupRuntimeEntities(sessionId);
        spawnedRuntimeRooms.remove(sessionId);
        spawnedRuntimeBossRooms.remove(sessionId);
        Set<World> worlds = new HashSet<>();
        for (UUID uuid : session.players()) {
            World world = instanceWorldsByPlayer.get(uuid);
            if (world != null) worlds.add(world);
            playerRuntimeSessions.remove(uuid);
            clearDungeonRunState(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) returnRuntimeDungeonPlayer(player, true);
        }
        doorManager.cleanup();
        for (World world : worlds) cleanupDungeonInstanceIfEmpty(world);
        return true;
    }

    private void leaveRuntimeDungeon(Player player, net.dark.threecore.dungeons.runtime.DungeonSession session, boolean restoreAndTeleport) {
        if (player == null || session == null) return;
        UUID uuid = player.getUniqueId();
        UUID sessionId = session.id();
        World world = instanceWorldsByPlayer.get(uuid);
        playerRuntimeSessions.remove(uuid);
        session.removePlayer(uuid);
        clearDungeonRunState(uuid);
        if (restoreAndTeleport) returnRuntimeDungeonPlayer(player, true);
        if (session.isEmpty()) {
            runtimeSessions.remove(sessionId);
            Bukkit.getPluginManager().callEvent(new DungeonEndEvent(session));
            cleanupRuntimeEntities(sessionId);
            spawnedRuntimeRooms.remove(sessionId);
            spawnedRuntimeBossRooms.remove(sessionId);
            doorManager.cleanup();
        }
        cleanupDungeonInstanceIfEmpty(world);
    }

    private void returnRuntimeDungeonPlayer(Player player, boolean restoreInventory) {
        if (player == null) return;
        clearDungeonVision(player);
        player.setGameMode(GameMode.SURVIVAL);
        Location spawn = readConfiguredLocation("spawn");
        if (spawn != null) {
            player.teleport(spawn);
        }
        if (restoreInventory) inventoryService.restore(player);
        player.setGameMode(GameMode.SURVIVAL);
        if (spawn != null) applyDungeonSpawnBuffs(player);
        player.updateInventory();
    }

    public net.dark.threecore.dungeons.runtime.DungeonSession getDungeonSession(Player player) {
        if (player == null) return null;
        UUID sessionId = playerRuntimeSessions.get(player.getUniqueId());
        return sessionId == null ? null : runtimeSessions.get(sessionId);
    }

    public boolean isInDungeonApi(Player player) {
        return getDungeonSession(player) != null || isInActiveDungeon(player.getUniqueId());
    }

    public String dungeonPlaceholder(Player player, String id) {
        UUID groupId = player == null ? null : player.getUniqueId();
        net.dark.threecore.dungeons.runtime.DungeonSession session = player == null ? null : getDungeonSession(player);
        return switch (id) {
            case "ready_count" -> groupId == null ? "0" : String.valueOf(readyManager.readyCount(groupId));
            case "total_players" -> groupId == null ? "0" : String.valueOf(Math.max(1, readyManager.total(groupId)));
            case "countdown" -> groupId == null ? "0" : String.valueOf(readyManager.countdown(groupId));
            case "room" -> session == null || session.currentRoom() == null ? "" : session.currentRoom().definition().id();
            case "floor" -> session == null || session.currentRoom() == null ? "0" : String.valueOf(session.currentRoom().origin().getBlockY());
            default -> "";
        };
    }

    public void registerRoomDefinition(DungeonRoomDefinition definition) {
        roomRegistry.register(definition);
    }

    public void registerRoomTrigger(String id, Runnable trigger) {
        if (id != null && trigger != null) roomTriggers.put(id, trigger);
    }

    public void registerLootProvider(String id, Object provider) {
        if (id != null && provider != null) lootProviders.put(id, provider);
    }

    public void registerMobProvider(String id, Object provider) {
        if (id != null && provider != null) mobProviders.put(id, provider);
    }

    public java.util.List<String> activeMemberNames(UUID uuid) {
        Set<UUID> members = activeGroups.get(uuid);
        if (members == null || members.isEmpty()) return java.util.List.of();
        return members.stream().map(this::nameOf).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public boolean isInActiveDungeon(UUID uuid) { return activeRuns.containsKey(uuid); }
    public static boolean isDungeonPlayer(Player player) { return player != null && ACTIVE_DUNGEON_PLAYERS.contains(player.getUniqueId()); }

    public void enter(Player player, String level) {
        String id = level.toLowerCase(Locale.ROOT);
        if (!configs.get("dungeons/dungeons.yml").isConfigurationSection("levels." + id)) id = "jungle";
        if (options(player).party()[0] && partyService != null && partyService.isInParty(player.getUniqueId())) {
            enterParty(player, id);
            return;
        }
        if (configs.get("dungeons/dungeons.yml").getBoolean("levels." + id + ".coming-soon", false)) { Text.send(player, "<red>That dungeon level is coming soon.</red>"); return; }
        if (!canStartDifficulty(player, id, options(player).difficulty()[0])) return;
        UUID sessionId = startDungeonApi(List.of(player), id);
        if (sessionId == null) {
            Text.send(player, "<red>Dungeon generation failed.</red> <gray>It needs a full path with a boss room and exit. Check /d debug generate for details.</gray>");
        }
    }

    private void giveDevTool(Player player) {
        if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        player.getInventory().setItem(DEV_TOOL_SLOT, tagged(Material.STRUCTURE_BLOCK, "<gradient:#4c1d95:#a78bfa>Dungeon Room Saver</gradient>", DEV_TOOL_ID));
        player.getInventory().setItem(DEV_TOOL_SLOT - 1, tagged(Material.WOODEN_AXE, "<gradient:#34d399:#22c55e>Dungeon Selection Wand</gradient>", DEV_WAND_ID));
        Text.send(player, "<green>Dungeon editor tools given.</green> <gray>Wand: left pos1, right pos2. Saver: right-click save, shift-right-click toolbox.</gray>");
    }

    private void openDevToolbox(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder("dev-toolbox"), 54, "Dungeon Dev Toolbox");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(4, connectionPreviewItem(player));
        inv.setItem(10, tagged(Material.WOODEN_AXE, "<gradient:#34d399:#22c55e>Selection Wand</gradient>", DEV_WAND_ID));
        inv.setItem(12, devMarker(Material.EMERALD, "Player Spawn"));
        inv.setItem(13, devMarker(Material.COMPASS, "Room Facing"));
        inv.setItem(14, devMarker(Material.LAPIS_LAZULI, "Enemy Spawn"));
        inv.setItem(16, devMarker(Material.REDSTONE, "Dungeon Exit"));
        inv.setItem(19, devMarker(Material.GOLD_INGOT, "Entrance"));
        inv.setItem(20, devMarker(Material.COPPER_INGOT, "Connector"));
        inv.setItem(21, devMarker(Material.COAL, "Boss Room"));
        inv.setItem(22, devMarker(Material.CRYING_OBSIDIAN, "Boss Spawner"));
        inv.setItem(23, devMarker(Material.IRON_BARS, "Boss Door"));
        inv.setItem(28, devMarker(Material.DEEPSLATE, "Boulder Trap"));
        inv.setItem(29, devMarker(Material.POINTED_DRIPSTONE, "Spike Trap"));
        inv.setItem(30, devMarker(Material.OAK_PLANKS, "Bridge Trap"));
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
        debug(player, "<gray>Dev toolbox opened with live connection preview.</gray>");
    }

    private void handleDevToolboxClick(Player player, int raw) {
        Map<Integer, Material> markers = Map.ofEntries(
                Map.entry(12, Material.EMERALD),
                Map.entry(13, Material.COMPASS),
                Map.entry(14, Material.LAPIS_LAZULI),
                Map.entry(16, Material.REDSTONE),
                Map.entry(19, Material.GOLD_INGOT),
                Map.entry(20, Material.COPPER_INGOT),
                Map.entry(21, Material.COAL),
                Map.entry(22, Material.CRYING_OBSIDIAN),
                Map.entry(23, Material.IRON_BARS),
                Map.entry(28, Material.DEEPSLATE),
                Map.entry(29, Material.POINTED_DRIPSTONE),
                Map.entry(30, Material.OAK_PLANKS)
        );
        Material material = markers.get(raw);
        if (raw == 49) {
            playBackSound(player);
            openMenu(player);
            return;
        }
        if (raw == 10) {
            player.getInventory().setItem(DEV_TOOL_SLOT - 1, tagged(Material.WOODEN_AXE, "<gradient:#34d399:#22c55e>Dungeon Selection Wand</gradient>", DEV_WAND_ID));
            Text.actionBar(player, "<green>Selection wand equipped.</green>");
            Bukkit.getScheduler().runTask(plugin, () -> openDevToolbox(player));
            return;
        }
        if (material == null) return;
        player.getInventory().addItem(devMarker(material, material.name()));
        Text.actionBar(player, "<gray>Added dungeon marker:</gray> <white>" + material.name() + "</white>");
        debug(player, "<gray>Added marker:</gray> <white>" + material.name() + "</white> <gray>and refreshed the toolbox.</gray>");
        Bukkit.getScheduler().runTask(plugin, () -> openDevToolbox(player));
    }

    private void placeDevMarker(Player player, Material material) {
        Location loc = player.getTargetBlockExact(6) == null ? player.getLocation().add(player.getLocation().getDirection().multiply(2)) : player.getTargetBlockExact(6).getLocation().add(0, 1, 0);
        String marker = markerId(material);
        if (marker == null) return;
        if ((marker.equals("exit") || marker.equals("boss_door")) && player.isSneaking() && selectedBounds(player) != null) {
            RoomBox box = selectedBounds(player);
            placeArmorMarker(player, new Location(player.getWorld(), box.minX(), box.minY(), box.minZ()), marker);
            placeArmorMarker(player, new Location(player.getWorld(), box.maxX(), box.maxY(), box.maxZ()), marker);
            Text.actionBar(player, marker.equals("boss_door") ? "<gray>Placed boss door region from wand selection.</gray>" : "<gray>Placed dungeon exit region from wand selection.</gray>");
            return;
        }
        placeArmorMarker(player, loc, marker);
    }

    private void placeArmorMarker(Player player, Location loc, String marker) {
        Location spawn = loc.clone();
        spawn.add(0.5D, 0.0D, 0.5D);
        spawn.setYaw(snapYaw(player.getLocation().getYaw()));
        spawn.setPitch(0.0F);
        ArmorStand stand = player.getWorld().spawn(spawn, ArmorStand.class, s -> {
            s.setGravity(false);
            s.setInvulnerable(false);
            s.setMarker(false);
            s.setSmall(true);
            s.setVisible(true);
            s.setGlowing(true);
            s.setBasePlate(false);
            s.setCustomNameVisible(true);
            s.customName(Component.text(marker));
        });
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "dungeon_editor_marker"), PersistentDataType.STRING, marker);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_MARKER_KEY), PersistentDataType.STRING, marker);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_MARKER_ROOM_ID_KEY), PersistentDataType.STRING, "room_id:unsaved");
        spawnEditorTrapPreview(stand, marker);
        Text.actionBar(player, "<gray>Placed armor stand marker:</gray> <white>" + marker + "</white>");
    }

    private void spawnEditorTrapPreview(ArmorStand markerStand, String markerId) {
        String type = trapType(markerId);
        if (type == null || markerStand == null || markerStand.getWorld() == null) return;
        removeEditorTrapPreview(markerStand.getUniqueId());
        Location location = markerStand.getLocation().clone();
        String mobId = resolvedTrapMobId(type, trapMobId(type));
        List<UUID> spawned = new ArrayList<>();
        openDungeonMobSpawnWindow(location.getWorld());
        Entity entity = mythicMobsHook.spawnMob(mobId, location);
        if (entity == null && !mobId.contains(":")) entity = mythicMobsHook.spawnMob(mobId + ":1", location);
        if (entity == null) {
            entity = location.getWorld().spawn(location, ArmorStand.class, stand -> {
                stand.setGravity(false);
                stand.setInvulnerable(false);
                stand.setMarker(false);
                stand.setVisible(true);
                stand.setGlowing(true);
                stand.setCustomNameVisible(true);
                stand.customName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>" + type + " trap preview</gradient>"));
            });
        }
        entity.teleport(location);
        entity.setPersistent(false);
        mythicMobsHook.setNoAI(entity);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "dungeon_editor_trap_preview"), PersistentDataType.STRING, markerStand.getUniqueId().toString());
        spawned.add(entity.getUniqueId());
        editorTrapPreviews.put(markerStand.getUniqueId(), spawned);
        debug(null, "<gray>Editor trap preview spawned:</gray> <white>" + type + "</white> <gray>mob=</gray><white>" + mobId + "</white> <gray>at</gray> <white>" + formatLocation(location) + "</white>");
    }

    private void removeEditorTrapPreview(UUID markerId) {
        if (markerId == null) return;
        List<UUID> ids = editorTrapPreviews.remove(markerId);
        if (ids == null || ids.isEmpty()) return;
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    private void removeEditorTrapPreviewForMarker(Entity marker) {
        if (marker == null) return;
        removeEditorTrapPreview(marker.getUniqueId());
        String markerId = marker.getUniqueId().toString();
        NamespacedKey key = new NamespacedKey(plugin, "dungeon_editor_trap_preview");
        for (Entity entity : marker.getWorld().getNearbyEntities(marker.getLocation(), 8.0D, 8.0D, 8.0D)) {
            String owner = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (markerId.equals(owner)) entity.remove();
        }
    }

    private void handleEditorMarkerCommand(Player player, String[] args) {
        if (!player.hasPermission("3smpcore.dungeon.editor") && !player.hasPermission("3smpcore.dungeons.admin")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (!isDungeonEditorWorld(player.getWorld())) {
            Text.send(player, "<red>Use dungeon marker commands inside the dungeon editor world.</red>");
            return;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "show";
        switch (action) {
            case "facing" -> placeFacingMarker(player);
            case "show" -> showFacingMarkers(player);
            case "hide" -> setEditorMarkersVisible(player, false);
            case "clear" -> clearFacingMarkers(player, true);
            case "clearnear" -> clearNearbyEditorMarkers(player, args.length >= 4 ? parseRadius(args[3]) : 12.0D);
            case "save" -> savePendingFacingMarker(player);
            default -> Text.send(player, "<yellow>/3smpcore dungeon editor marker facing|show|hide|clear|clearnear <radius>|save</yellow>");
        }
    }

    private void handleEditorConnectorCommand(Player player, String[] args) {
        if (!player.hasPermission("3smpcore.dungeon.editor") && !player.hasPermission("3smpcore.dungeons.admin")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (!isDungeonEditorWorld(player.getWorld())) {
            Text.send(player, "<red>Use dungeon connector commands inside the dungeon editor world.</red>");
            return;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "show";
        switch (action) {
            case "entrance" -> placeConnectorMarker(player, args.length >= 4 ? args[3] : "entrance_" + System.currentTimeMillis(), "entrance", "ENTRANCE", "NORMAL", "NONE", 0, "CONNECTOR_FACE");
            case "exit" -> placeConnectorMarker(player, args.length >= 4 ? args[3] : "exit_" + System.currentTimeMillis(), "connector", "EXIT", "NORMAL", "NONE", 0, "CONNECTOR_FACE");
            case "up", "down", "vertical" -> Text.send(player, "<yellow>Vertical room editor markers are disabled. Use normal entrance/exit connectors for smoother generation.</yellow>");
            case "anchor" -> setConnectorAnchor(player, args.length >= 4 ? args[3] : null);
            case "show" -> showConnectorMarkers(player);
            case "clear" -> clearConnectorMarker(player, args.length >= 4 ? args[3] : null);
            default -> Text.send(player, "<yellow>/3smpcore dungeon editor connector entrance|exit <id> | anchor <id> | show | clear <id></yellow>");
        }
    }

    private void placeConnectorMarker(Player player, String connectorId, String markerId, String role, String type, String verticalDirection, int targetYOffset, String snapMode) {
        String safeId = sanitizeConnectorId(connectorId);
        Location loc = player.getLocation().clone();
        loc.setYaw(snapYaw(loc.getYaw()));
        loc.setPitch(0.0F);
        ArmorStand stand = player.getWorld().spawn(loc, ArmorStand.class, armorStand -> {
            armorStand.setGravity(false);
            armorStand.setInvulnerable(false);
            armorStand.setMarker(false);
            armorStand.setSmall(true);
            armorStand.setVisible(true);
            armorStand.setArms(false);
            armorStand.setBasePlate(false);
            armorStand.setGlowing(true);
            armorStand.customName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>" + safeId + "</gradient> <gray>(" + role.toLowerCase(Locale.ROOT) + " " + type.toLowerCase(Locale.ROOT) + ")</gray>"));
            armorStand.setCustomNameVisible(false);
        });
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "dungeon_editor_marker"), PersistentDataType.STRING, markerId);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_MARKER_KEY), PersistentDataType.STRING, markerId);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_MARKER_ROOM_ID_KEY), PersistentDataType.STRING, "room_id:unsaved");
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ID_KEY), PersistentDataType.STRING, safeId);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ROLE_KEY), PersistentDataType.STRING, role);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_TYPE_KEY), PersistentDataType.STRING, type);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_VERTICAL_KEY), PersistentDataType.STRING, verticalDirection);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_TARGET_Y_KEY), PersistentDataType.INTEGER, targetYOffset);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_SNAP_KEY), PersistentDataType.STRING, snapMode);
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_X_KEY), PersistentDataType.INTEGER, loc.getBlockX());
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_Y_KEY), PersistentDataType.INTEGER, loc.getBlockY());
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_Z_KEY), PersistentDataType.INTEGER, loc.getBlockZ());
        drawConnectorMarker(player, stand);
        Text.send(player, "<green>Connector marker placed.</green> <gray>Id:</gray> <white>" + safeId + "</white> <gray>Role:</gray> <white>" + role + "</white> <gray>Type:</gray> <white>" + type + "</white>");
    }

    private void setConnectorAnchor(Player player, String connectorId) {
        if (connectorId == null || connectorId.isBlank()) {
            Text.send(player, "<yellow>Usage: /3smpcore dungeon editor connector anchor <id></yellow>");
            return;
        }
        String safeId = sanitizeConnectorId(connectorId);
        int updated = 0;
        for (Entity entity : connectorMarkers(player.getWorld())) {
            if (!safeId.equalsIgnoreCase(entity.getPersistentDataContainer().get(new NamespacedKey(plugin, ROOM_CONNECTOR_ID_KEY), PersistentDataType.STRING))) continue;
            Location loc = player.getLocation();
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_X_KEY), PersistentDataType.INTEGER, loc.getBlockX());
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_Y_KEY), PersistentDataType.INTEGER, loc.getBlockY());
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_Z_KEY), PersistentDataType.INTEGER, loc.getBlockZ());
            updated++;
        }
        Text.send(player, updated == 0 ? "<yellow>No connector marker found with id " + safeId + ".</yellow>" : "<green>Connector anchor saved at your current block for " + safeId + ".</green>");
    }

    private void showConnectorMarkers(Player player) {
        List<Entity> markers = connectorMarkers(player.getWorld());
        if (markers.isEmpty()) {
            Text.send(player, "<yellow>No dungeon connector markers found.</yellow>");
            return;
        }
        for (Entity entity : markers) {
            if (entity instanceof ArmorStand stand) {
                stand.setVisible(true);
                stand.setGlowing(true);
                stand.setCustomNameVisible(false);
            }
            drawConnectorMarker(player, entity);
            Text.send(player, connectorDebugLine(entity));
        }
    }

    private void clearConnectorMarker(Player player, String connectorId) {
        String safeId = connectorId == null ? null : sanitizeConnectorId(connectorId);
        int removed = 0;
        for (Entity entity : connectorMarkers(player.getWorld())) {
            String id = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, ROOM_CONNECTOR_ID_KEY), PersistentDataType.STRING);
            if (safeId != null && !safeId.equalsIgnoreCase(id)) continue;
            entity.remove();
            removed++;
        }
        Text.send(player, "<yellow>Removed " + removed + " dungeon connector marker(s).</yellow>");
    }

    private List<Entity> connectorMarkers(World world) {
        if (world == null) return List.of();
        NamespacedKey key = new NamespacedKey(plugin, ROOM_CONNECTOR_ID_KEY);
        return allEditorMarkers(world).stream()
            .filter(entity -> entity.getPersistentDataContainer().has(key, PersistentDataType.STRING))
            .toList();
    }

    private void drawConnectorMarker(Player player, Entity entity) {
        Location loc = entity.getLocation();
        String marker = armorStandMarker(entity);
        Color color = switch (marker == null ? "" : marker.toLowerCase(Locale.ROOT)) {
            case "entrance" -> Color.fromRGB(96, 165, 250);
            case "up", "down" -> Color.fromRGB(248, 250, 252);
            default -> Color.fromRGB(250, 204, 21);
        };
        player.spawnParticle(Particle.DUST, loc.clone().add(0.0D, 1.1D, 0.0D), 18, 0.25D, 0.25D, 0.25D, new Particle.DustOptions(color, 1.1F));
        drawFacingArrow(player, loc, markerFacing(marker == null ? "connector" : marker, loc, loc.getBlockX(), loc.getBlockX(), loc.getBlockZ(), loc.getBlockZ()));
    }

    private String connectorDebugLine(Entity entity) {
        var data = entity.getPersistentDataContainer();
        String id = data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_ID_KEY), PersistentDataType.STRING);
        String role = data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_ROLE_KEY), PersistentDataType.STRING);
        String type = data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_TYPE_KEY), PersistentDataType.STRING);
        String vertical = data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_VERTICAL_KEY), PersistentDataType.STRING);
        String snap = data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_SNAP_KEY), PersistentDataType.STRING);
        return "<gray>Connector</gray> <white>" + id + "</white> <dark_gray>@</dark_gray> <white>" + entity.getLocation().getBlockX() + "," + entity.getLocation().getBlockY() + "," + entity.getLocation().getBlockZ() + "</white> <gray>role</gray> <white>" + role + "</white> <gray>type</gray> <white>" + type + "</white> <gray>vertical</gray> <white>" + vertical + "</white> <gray>snap</gray> <white>" + snap + "</white>";
    }

    private String sanitizeConnectorId(String raw) {
        String value = raw == null || raw.isBlank() ? "connector_" + System.currentTimeMillis() : raw.toLowerCase(Locale.ROOT);
        return value.replaceAll("[^a-z0-9_\\-]", "_");
    }

    private void placeFacingMarker(Player player) {
        clearFacingMarkers(player, false);
        Location loc = player.getLocation().clone();
        String facing = facingFromYaw(loc.getYaw());
        loc.setYaw(yawFromFacing(facing, loc.getYaw()));
        loc.setPitch(0.0F);
        ArmorStand stand = player.getWorld().spawn(loc, ArmorStand.class, armorStand -> {
            armorStand.setGravity(false);
            armorStand.setInvulnerable(false);
            armorStand.setMarker(false);
            armorStand.setSmall(true);
            armorStand.setVisible(true);
            armorStand.setArms(true);
            armorStand.setBasePlate(false);
            armorStand.setRotation(loc.getYaw(), 0.0F);
            armorStand.customName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>Room Facing:</gradient> <white>" + facing + "</white>"));
            armorStand.setCustomNameVisible(true);
        });
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, "dungeon_editor_marker"), PersistentDataType.STRING, "room_facing");
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_MARKER_KEY), PersistentDataType.STRING, "room_facing");
        stand.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_MARKER_ROOM_ID_KEY), PersistentDataType.STRING, "room_id:unsaved");
        drawFacingArrow(player, loc, facing);
        Text.send(player, "<green>Room facing marker placed.</green> <gray>Base-facing:</gray> <white>" + facing + "</white>");
    }

    private void showFacingMarkers(Player player) {
        setEditorMarkersVisible(player, true);
        List<Entity> markers = facingMarkers(player.getWorld());
        if (markers.isEmpty()) {
            Text.send(player, "<yellow>No room-facing marker found. Missing rooms default to SOUTH.</yellow>");
            return;
        }
        for (Entity entity : markers) {
            Location loc = entity.getLocation();
            String facing = facingFromYaw(loc.getYaw());
            drawFacingArrow(player, loc, facing);
            Text.send(player, "<gray>Facing marker:</gray> <white>" + facing + "</white> <dark_gray>@</dark_gray> <white>" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "</white>");
        }
    }

    private void setEditorMarkersVisible(Player player, boolean visible) {
        for (Entity entity : allEditorMarkers(player.getWorld())) {
            if (entity instanceof ArmorStand stand) {
                stand.setVisible(visible);
                stand.setCustomNameVisible(false);
                if (visible) stand.setGlowing(true);
                else stand.setGlowing(false);
            }
        }
        Text.send(player, visible ? "<green>Showing dungeon editor markers.</green>" : "<yellow>Hid dungeon editor markers.</yellow>");
    }

    private void clearNearbyEditorMarkers(Player player, double radius) {
        int removed = 0;
        for (Entity entity : allEditorMarkers(player.getWorld())) {
            if (entity.getLocation().distanceSquared(player.getLocation()) > radius * radius) continue;
            entity.remove();
            removed++;
        }
        Text.send(player, "<yellow>Removed " + removed + " nearby dungeon editor marker(s).</yellow>");
    }

    private double parseRadius(String raw) {
        try {
            return Math.max(1.0D, Math.min(128.0D, Double.parseDouble(raw)));
        } catch (NumberFormatException ex) {
            return 12.0D;
        }
    }

    private void clearFacingMarkers(Player player, boolean feedback) {
        int removed = 0;
        for (Entity marker : facingMarkers(player.getWorld())) {
            marker.remove();
            removed++;
        }
        if (feedback) Text.send(player, "<yellow>Cleared " + removed + " room-facing marker(s).</yellow>");
    }

    private void savePendingFacingMarker(Player player) {
        List<Entity> markers = facingMarkers(player.getWorld());
        if (markers.isEmpty()) {
            Text.send(player, "<yellow>No facing marker to save. Place one with /3smpcore dungeon editor marker facing.</yellow>");
            return;
        }
        Entity marker = markers.get(0);
        Location loc = marker.getLocation();
        String facing = facingFromYaw(loc.getYaw());
        var yaml = configs.get("dungeons/rooms.yml");
        String path = "editor-facing-markers." + player.getUniqueId();
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".pos", List.of(loc.getX(), loc.getY(), loc.getZ()));
        yaml.set(path + ".facing", facing);
        try {
            yaml.save(new File(plugin.getDataFolder(), "dungeons/rooms.yml"));
            Text.send(player, "<green>Saved pending room-facing marker.</green> <gray>It will also be written into the next saved room template.</gray>");
        } catch (Exception ex) {
            Text.send(player, "<red>Failed to save facing marker metadata.</red>");
        }
    }

    private List<Entity> facingMarkers(World world) {
        if (world == null) return List.of();
        NamespacedKey newKey = new NamespacedKey(plugin, ROOM_MARKER_KEY);
        NamespacedKey oldKey = new NamespacedKey(plugin, "dungeon_editor_marker");
        return world.getEntities().stream()
            .filter(entity -> entity instanceof ArmorStand)
            .filter(entity -> "room_facing".equalsIgnoreCase(entity.getPersistentDataContainer().get(newKey, PersistentDataType.STRING))
                || "room_facing".equalsIgnoreCase(entity.getPersistentDataContainer().get(oldKey, PersistentDataType.STRING)))
            .toList();
    }

    private List<Entity> allEditorMarkers(World world) {
        if (world == null) return List.of();
        NamespacedKey roomKey = new NamespacedKey(plugin, ROOM_MARKER_KEY);
        NamespacedKey editorKey = new NamespacedKey(plugin, "dungeon_editor_marker");
        return world.getEntities().stream()
            .filter(entity -> entity instanceof ArmorStand)
            .filter(entity -> entity.getPersistentDataContainer().has(roomKey, PersistentDataType.STRING) || entity.getPersistentDataContainer().has(editorKey, PersistentDataType.STRING))
            .toList();
    }

    private void drawFacingArrow(Player player, Location loc, String facing) {
        org.bukkit.util.Vector direction = switch (facing.toUpperCase(Locale.ROOT)) {
            case "NORTH" -> new org.bukkit.util.Vector(0, 0, -1);
            case "EAST" -> new org.bukkit.util.Vector(1, 0, 0);
            case "WEST" -> new org.bukkit.util.Vector(-1, 0, 0);
            default -> new org.bukkit.util.Vector(0, 0, 1);
        };
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(250, 204, 21), 1.2F);
        for (int i = 0; i <= 8; i++) {
            Location point = loc.clone().add(0, 1.0D, 0).add(direction.clone().multiply(i * 0.35D));
            player.spawnParticle(Particle.DUST, point, 2, 0.02D, 0.02D, 0.02D, gold);
        }
    }

    private void saveTemplate(Player player, String id, String level) {
        if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        RoomBox selection = selectedBounds(player);
        if (selection == null) { Text.send(player, "<red>Select pos1/pos2 with the dungeon wand first.</red>"); return; }
        int minX = selection.minX(), maxX = selection.maxX();
        int minY = selection.minY(), maxY = selection.maxY();
        int minZ = selection.minZ(), maxZ = selection.maxZ();
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        String path = "templates." + id.toLowerCase(Locale.ROOT);
        yaml.set(path, null); yaml.set(path + ".level", level.toLowerCase(Locale.ROOT)); yaml.set(path + ".role", "normal"); yaml.set(path + ".size.x", maxX-minX+1); yaml.set(path + ".size.y", maxY-minY+1); yaml.set(path + ".size.z", maxZ-minZ+1);
        List<String> blocks = new ArrayList<>(); List<String> markers = new ArrayList<>(); List<String> preciseMarkers = new ArrayList<>(); List<String> entities = new ArrayList<>(); List<EditorConnector> editorConnectors = new ArrayList<>();
        for (int x=minX;x<=maxX;x++) for (int y=minY;y<=maxY;y++) for (int z=minZ;z<=maxZ;z++) {
            Block block = player.getWorld().getBlockAt(x,y,z); Material type = block.getType(); if (type.isAir()) continue;
            if (isShulkerMarkerBlock(type)) continue;
            String rel = (x-minX)+","+(y-minY)+","+(z-minZ);
            Marker marker = marker(type); if (marker != null) { markers.add(rel+":"+marker.id()+":"+facing(block)); continue; }
            blocks.add(rel+"|"+serializeBlockData(block));
        }
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Player) continue;
            Location loc = entity.getLocation();
            if (loc.getBlockX() < minX || loc.getBlockX() > maxX || loc.getBlockY() < minY || loc.getBlockY() > maxY || loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) continue;
            String armorMarker = armorStandMarker(entity);
            if (armorMarker != null) {
                String markerFacing = markerFacing(armorMarker, loc, minX, maxX, minZ, maxZ);
                SavedMarker marker = new SavedMarker(loc.getBlockX() - minX, loc.getBlockY() - minY, loc.getBlockZ() - minZ, armorMarker, markerFacing, loc.getX() - minX, loc.getY() - minY, loc.getZ() - minZ, yawFromFacing(markerFacing, loc.getYaw()), loc.getPitch());
                preciseMarkers.add(serializePreciseMarker(marker));
                markers.add(marker.x() + "," + marker.y() + "," + marker.z() + ":" + marker.id() + ":" + marker.facing());
                EditorConnector connector = editorConnector(entity, marker, minX, minY, minZ);
                if (connector != null) editorConnectors.add(connector);
                continue;
            }
            entities.add(serializeEntity(entity, minX, minY, minZ));
        }
        String baseFacing = detectBaseFacing(preciseMarkers);
        SavedMarker facingMarker = preciseMarkers.stream()
            .map(this::parsePreciseMarker)
            .filter(Objects::nonNull)
            .filter(marker -> marker.id().equalsIgnoreCase("room_facing"))
            .findFirst()
            .orElse(null);
        if (facingMarker == null) Text.send(player, "<yellow>No room-facing marker found; saved base-facing SOUTH. Use /3smpcore dungeon editor marker facing for accurate rotations.</yellow>");
        String safeId = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        File structureFile = templateStructureFile(safeId);
        try {
            StructureManager structureManager = Bukkit.getStructureManager();
            Structure structure = structureManager.createStructure();
            structure.fill(new Location(player.getWorld(), minX, minY, minZ), new Location(player.getWorld(), maxX, maxY, maxZ), false);
            structureManager.saveStructure(structureFile, structure);
            yaml.set(path + ".structure", "templates/" + structureFile.getName());
        } catch (Exception ex) {
            Text.send(player, "<yellow>Structure save failed, using YAML fallback only: " + ex.getMessage() + "</yellow>");
            yaml.set(path + ".structure", null);
        }
        yaml.set(path + ".base-facing", baseFacing);
        if (facingMarker != null) {
            yaml.set(path + ".facing-marker.pos", List.of(facingMarker.x(), facingMarker.y(), facingMarker.z()));
            yaml.set(path + ".facing-marker.facing", facingMarker.facing());
            yaml.set(path + ".rotation-origin.mode", "MARKER");
            yaml.set(path + ".rotation-origin.pos", List.of(facingMarker.x(), facingMarker.y(), facingMarker.z()));
        } else {
            yaml.set(path + ".facing-marker.pos", null);
            yaml.set(path + ".facing-marker.facing", null);
            yaml.set(path + ".rotation-origin.mode", "ROOM_MIN");
            yaml.set(path + ".rotation-origin.pos", List.of(0, 0, 0));
        }
        yaml.set(path + ".connectors", null);
        for (EditorConnector connector : editorConnectors) {
            String connectorPath = path + ".connectors." + connector.id();
            yaml.set(connectorPath + ".role", connector.role());
            yaml.set(connectorPath + ".type", connector.type());
            yaml.set(connectorPath + ".pos", List.of(connector.marker().x(), connector.marker().y(), connector.marker().z()));
            yaml.set(connectorPath + ".facing", connector.marker().facing());
            yaml.set(connectorPath + ".vertical-direction", connector.verticalDirection());
            yaml.set(connectorPath + ".target-y-offset", connector.targetYOffset());
            yaml.set(connectorPath + ".width", 3);
            yaml.set(connectorPath + ".height", 4);
            yaml.set(connectorPath + ".snap-mode", connector.snapMode());
            yaml.set(connectorPath + ".anchor-pos", List.of(connector.anchorX(), connector.anchorY(), connector.anchorZ()));
        }
        yaml.set(path + ".role", detectRoomRole(markers));
        yaml.set(path + ".size-category", sizeCategory(maxX-minX+1, maxY-minY+1, maxZ-minZ+1));
        yaml.set(path + ".blocks", blocks); yaml.set(path + ".markers", markers); yaml.set(path + ".precise-markers", preciseMarkers); yaml.set(path + ".entities", entities);
        try { yaml.save(new File(plugin.getDataFolder(), "dungeons/templates.yml")); } catch (Exception ignored) {}
        syncSavedTemplateToRooms(player, safeId, level, detectRoomRole(markers), baseFacing, facingMarker, editorConnectors, preciseMarkers, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        for (Entity markerEntity : facingMarkers(player.getWorld())) {
            Location loc = markerEntity.getLocation();
            if (loc.getBlockX() >= minX && loc.getBlockX() <= maxX && loc.getBlockY() >= minY && loc.getBlockY() <= maxY && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ) {
                markerEntity.getPersistentDataContainer().set(new NamespacedKey(plugin, ROOM_MARKER_ROOM_ID_KEY), PersistentDataType.STRING, "room_id:" + safeId);
            }
        }
        debug(player, "<gray>Saved template</gray> <white>" + id + "</white> <gray>level</gray> <white>" + level + "</white> <gray>role</gray> <white>" + detectRoomRole(markers) + "</white> <gray>markers</gray> <white>" + markers.size() + "</white> <gray>precise</gray> <white>" + preciseMarkers.size() + "</white>");
        Text.send(player, "<green>Saved dungeon room template " + id + " with " + blocks.size() + " blocks, " + markers.size() + " markers, and " + preciseMarkers.size() + " precise armor stand markers.</green> <gray>Base-facing:</gray> <white>" + baseFacing + "</white>");
    }

    private void syncSavedTemplateToRooms(Player player, String safeId, String level, String role, String baseFacing, SavedMarker facingMarker, List<EditorConnector> editorConnectors, List<String> preciseMarkers, int sizeX, int sizeY, int sizeZ) {
        YamlConfiguration rooms = configs.get("dungeons/rooms.yml");
        String base = "rooms." + safeId;
        backupDungeonConfig("rooms.yml");
        rooms.set(base, null);
        rooms.set(base + ".schematic", "templates/" + safeId + ".nbt");
        rooms.set(base + ".type", templateRoomType(role));
        rooms.set(base + ".weight", 1);
        rooms.set(base + ".base-facing", baseFacing);
        rooms.set(base + ".size.x", sizeX);
        rooms.set(base + ".size.y", sizeY);
        rooms.set(base + ".size.z", sizeZ);
        if (facingMarker != null) {
            rooms.set(base + ".facing-marker.pos", List.of(facingMarker.x(), facingMarker.y(), facingMarker.z()));
            rooms.set(base + ".facing-marker.facing", facingMarker.facing());
            rooms.set(base + ".rotation-origin.mode", "MARKER");
            rooms.set(base + ".rotation-origin.pos", List.of(facingMarker.x(), facingMarker.y(), facingMarker.z()));
        } else {
            rooms.set(base + ".rotation-origin.mode", "ROOM_MIN");
            rooms.set(base + ".rotation-origin.pos", List.of(0, 0, 0));
        }

        if (!editorConnectors.isEmpty()) {
            for (EditorConnector connector : editorConnectors) writeRoomConnector(rooms, base, connector);
        } else {
            writeLegacyMarkerConnectors(rooms, base, preciseMarkers);
        }
        writeRoomSpawnsAndTraps(rooms, base, safeId, level, preciseMarkers);
        writeBossDoorRegion(rooms, base, safeId, preciseMarkers);
        try {
            rooms.save(new File(plugin.getDataFolder(), "dungeons/rooms.yml"));
            configs.reload();
            roomRegistry.reload();
            Text.send(player, "<green>Updated rooms.yml for generated dungeons.</green> <gray>Room:</gray> <white>" + safeId + "</white>");
        } catch (Exception ex) {
            Text.send(player, "<red>Room template saved, but rooms.yml sync failed:</red> <white>" + ex.getMessage() + "</white>");
        }
    }

    private void backupDungeonConfig(String name) {
        try {
            File file = new File(plugin.getDataFolder(), "dungeons/" + name);
            if (!file.exists()) return;
            File dir = new File(plugin.getDataFolder(), "dungeons/backups");
            if (!dir.exists()) dir.mkdirs();
            File backup = new File(dir, name.replace(".yml", "") + "-" + System.currentTimeMillis() + ".yml");
            Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private String templateRoomType(String role) {
        if ("entrance".equalsIgnoreCase(role)) return "START";
        if ("boss".equalsIgnoreCase(role)) return "BOSS";
        if ("exit".equalsIgnoreCase(role)) return "DEAD_END";
        return "NORMAL";
    }

    private void writeRoomConnector(YamlConfiguration rooms, String base, EditorConnector connector) {
        boolean vertical = !"NONE".equalsIgnoreCase(connector.verticalDirection()) || connector.type().toUpperCase(Locale.ROOT).startsWith("STAIRS");
        String group = vertical ? "vertical-connectors" : connector.role().equalsIgnoreCase("ENTRANCE") ? "entrances" : "exits";
        String path = base + "." + group + "." + sanitizeConnectorId(connector.id());
        rooms.set(path + ".pos", List.of(connector.marker().x(), connector.marker().y(), connector.marker().z()));
        rooms.set(path + ".facing", connector.marker().facing());
        rooms.set(path + ".width", 3);
        rooms.set(path + ".height", 4);
        rooms.set(path + ".connector", connector.type());
        rooms.set(path + ".snap-mode", connector.snapMode());
        rooms.set(path + ".anchor-pos", List.of(connector.anchorX(), connector.anchorY(), connector.anchorZ()));
        rooms.set(path + ".required", connector.role().equalsIgnoreCase("ENTRANCE"));
        if (vertical) {
            rooms.set(path + ".vertical-direction", connector.verticalDirection());
            rooms.set(path + ".target-y-offset", connector.targetYOffset());
            rooms.set(path + ".required-match", connector.type().equalsIgnoreCase("STAIRS_UP") ? "STAIRS_DOWN" : connector.type().equalsIgnoreCase("STAIRS_DOWN") ? "STAIRS_UP" : connector.type());
        }
    }

    private void writeLegacyMarkerConnectors(YamlConfiguration rooms, String base, List<String> preciseMarkers) {
        int entrances = 0;
        int exits = 0;
        for (String raw : preciseMarkers) {
            SavedMarker marker = parsePreciseMarker(raw);
            if (marker == null) continue;
            if (marker.id().equalsIgnoreCase("entrance")) {
                String path = base + ".entrances.entrance_" + (++entrances);
                rooms.set(path + ".pos", List.of(marker.x(), marker.y(), marker.z()));
                rooms.set(path + ".facing", marker.facing());
                rooms.set(path + ".width", 3);
                rooms.set(path + ".height", 4);
                rooms.set(path + ".connector", "NORMAL");
                rooms.set(path + ".snap-mode", "CONNECTOR_FACE");
                rooms.set(path + ".anchor-pos", List.of(marker.x(), marker.y(), marker.z()));
                rooms.set(path + ".required", true);
            } else if (marker.id().equalsIgnoreCase("connector")) {
                String path = base + ".exits.exit_" + (++exits);
                rooms.set(path + ".pos", List.of(marker.x(), marker.y(), marker.z()));
                rooms.set(path + ".facing", marker.facing());
                rooms.set(path + ".width", 3);
                rooms.set(path + ".height", 4);
                rooms.set(path + ".connector", "NORMAL");
                rooms.set(path + ".snap-mode", "CONNECTOR_FACE");
                rooms.set(path + ".anchor-pos", List.of(marker.x(), marker.y(), marker.z()));
                rooms.set(path + ".required", false);
            }
        }
    }

    private void writeRoomSpawnsAndTraps(YamlConfiguration rooms, String base, String roomId, String level, List<String> preciseMarkers) {
        int enemyIndex = 0;
        int bossIndex = 0;
        int trapIndex = 0;
        for (String raw : preciseMarkers) {
            SavedMarker marker = parsePreciseMarker(raw);
            if (marker == null) continue;
            if (marker.id().equalsIgnoreCase("enemy_spawn")) {
                String path = base + ".spawns.mobs.enemy_" + (++enemyIndex);
                rooms.set(path + ".id", configs.get("dungeons/dungeons.yml").getString("mobs.default-id", "CryptSkeleton"));
                rooms.set(path + ".pos", markerPosition(marker));
                rooms.set(path + ".amount", 1);
                rooms.set(path + ".level", 1);
                rooms.set(path + ".trigger", "ON_ROOM_ENTER");
                rooms.set(path + ".fallback", "ZOMBIE");
            } else if (marker.id().equalsIgnoreCase("boss_spawner")) {
                String path = base + ".spawns.mobs.boss_" + (++bossIndex);
                rooms.set(path + ".id", bossMobId(level, "normal", configs.get("dungeons/dungeons.yml").getString("boss.mythicmob", "DungeonBoss")));
                rooms.set(path + ".pos", markerPosition(marker));
                rooms.set(path + ".amount", 1);
                rooms.set(path + ".level", configs.get("dungeons/dungeons.yml").getInt("boss.levels." + level, 5));
                rooms.set(path + ".trigger", "ON_BOSS_START");
                rooms.set(path + ".fallback", "WARDEN");
            } else {
                String trapType = trapType(marker.id());
                if (trapType == null) continue;
                String path = base + ".traps." + trapType + "_" + (++trapIndex);
                rooms.set(path + ".enabled", true);
                rooms.set(path + ".type", trapType);
                rooms.set(path + ".pos", markerPosition(marker));
                rooms.set(path + ".facing", marker.facing());
                rooms.set(path + ".mythicmob-id", trapMobId(trapType));
            }
        }
    }

    private void writeBossDoorRegion(YamlConfiguration rooms, String base, String roomId, List<String> preciseMarkers) {
        List<SavedMarker> door = preciseMarkers.stream()
            .map(this::parsePreciseMarker)
            .filter(Objects::nonNull)
            .filter(marker -> marker.id().equalsIgnoreCase("boss_door"))
            .toList();
        rooms.set(base + ".boss-door", null);
        if (door.size() < 2) return;
        SavedMarker a = door.get(0);
        SavedMarker b = door.get(1);
        int minX = Math.min(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int minZ = Math.min(a.z(), b.z());
        int maxX = Math.max(a.x(), b.x());
        int maxY = Math.max(a.y(), b.y());
        int maxZ = Math.max(a.z(), b.z());
        rooms.set(base + ".boss-door.enabled", true);
        rooms.set(base + ".boss-door.pos1", List.of(minX, minY, minZ));
        rooms.set(base + ".boss-door.pos2", List.of(maxX, maxY, maxZ));
        List<String> blockStates = bossDoorBlockStates(roomId, minX, minY, minZ, maxX, maxY, maxZ);
        if (!blockStates.isEmpty()) rooms.set(base + ".boss-door.blockstates", blockStates);
        Text.send(Bukkit.getConsoleSender(), "[3SMPCore] Saved boss door for " + base + " from " + minX + "," + minY + "," + minZ + " to " + maxX + "," + maxY + "," + maxZ);
    }

    private List<String> bossDoorBlockStates(String roomId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<String> out = new ArrayList<>();
        YamlConfiguration templates = configs.get("dungeons/templates.yml");
        for (String raw : templates.getStringList("templates." + roomId + ".blocks")) {
            String[] parts = raw.split("\\|", 3);
            if (parts.length < 2) continue;
            String[] xyz = parts[0].split(",");
            if (xyz.length < 3) continue;
            try {
                int x = Integer.parseInt(xyz[0]);
                int y = Integer.parseInt(xyz[1]);
                int z = Integer.parseInt(xyz[2]);
                if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) out.add(raw);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private List<Double> markerPosition(SavedMarker marker) {
        return List.of(round(marker.offsetX()), round(marker.offsetY()), round(marker.offsetZ()));
    }

    private String trapType(String markerId) {
        if ("trap_boulder".equalsIgnoreCase(markerId)) return "boulder";
        if ("trap_spike".equalsIgnoreCase(markerId)) return "spike";
        if ("trap_bridge".equalsIgnoreCase(markerId)) return "bridge";
        return null;
    }

    private String trapMobId(String trapType) {
        return configs.get("dungeons/dungeons.yml").getString("traps.mythicmobs." + trapType.toLowerCase(Locale.ROOT), switch (trapType.toLowerCase(Locale.ROOT)) {
            case "spike" -> "Spike_Trap";
            case "bridge" -> "Bridge_Trap";
            default -> "DungeonBoulder";
        });
    }

    private String resolvedTrapMobId(String trapType, String configuredId) {
        if (configuredId == null || configuredId.isBlank()) return trapMobId(trapType);
        return switch (configuredId) {
            case "DungeonSpikeTrap" -> "Spike_Trap";
            case "DungeonBridgeTrap" -> "Bridge_Trap";
            default -> configuredId;
        };
    }

    private EditorConnector editorConnector(Entity entity, SavedMarker marker, int minX, int minY, int minZ) {
        var data = entity.getPersistentDataContainer();
        String id = data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_ID_KEY), PersistentDataType.STRING);
        if (id == null || id.isBlank()) return null;
        String role = Optional.ofNullable(data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_ROLE_KEY), PersistentDataType.STRING)).orElse(marker.id().equalsIgnoreCase("entrance") || marker.id().equalsIgnoreCase("down") ? "ENTRANCE" : "EXIT");
        String type = Optional.ofNullable(data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_TYPE_KEY), PersistentDataType.STRING)).orElse(marker.id().equalsIgnoreCase("up") ? "STAIRS_UP" : marker.id().equalsIgnoreCase("down") ? "STAIRS_DOWN" : "NORMAL");
        String vertical = Optional.ofNullable(data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_VERTICAL_KEY), PersistentDataType.STRING)).orElse(marker.id().equalsIgnoreCase("up") ? "UP" : marker.id().equalsIgnoreCase("down") ? "DOWN" : "NONE");
        String snap = Optional.ofNullable(data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_SNAP_KEY), PersistentDataType.STRING)).orElse(vertical.equalsIgnoreCase("NONE") ? "CONNECTOR_FACE" : "ANCHOR");
        Integer yOffset = data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_TARGET_Y_KEY), PersistentDataType.INTEGER);
        int anchorX = Optional.ofNullable(data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_X_KEY), PersistentDataType.INTEGER)).orElse(entity.getLocation().getBlockX()) - minX;
        int anchorY = Optional.ofNullable(data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_Y_KEY), PersistentDataType.INTEGER)).orElse(entity.getLocation().getBlockY()) - minY;
        int anchorZ = Optional.ofNullable(data.get(new NamespacedKey(plugin, ROOM_CONNECTOR_ANCHOR_Z_KEY), PersistentDataType.INTEGER)).orElse(entity.getLocation().getBlockZ()) - minZ;
        return new EditorConnector(id, role, type, marker, vertical, yOffset == null ? 0 : yOffset, snap, anchorX, anchorY, anchorZ);
    }

    private void pasteTemplate(String id, RoomReservation room) {
        World world = Bukkit.getWorld(room.world()); if (world == null) return;
        var yaml = configs.get("dungeons/templates.yml");
        RoomSize pasteSize = rotatedSize(id, room.rotation());
        forceLoadTemplateArea(world, room, pasteSize.x(), pasteSize.z());
        beginQuietPaste(world);
        try {
            clearTemplateArea(world, room, pasteSize.x(), pasteSize.y(), pasteSize.z());
            if (!roomIsConnectable(yaml, id)) return;
            if (canUseFastStructurePaste(id) && pasteStructure(id, room)) {
                stripRuntimeMarkers(world, room, pasteSize.x(), pasteSize.y(), pasteSize.z());
                return;
            }
            for (String entry : yaml.getStringList("templates." + id + ".blocks")) {
                String[] p = entry.split("\\|", 3); if (p.length < 2) continue; String[] xyz = p[0].split(",");
                LocalBlockPos rotated = transformTemplateBlock(id, Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]), effectiveStructureRotation(id, room.rotation()));
                Block target = world.getBlockAt(room.centerX()+rotated.x(), room.y()+rotated.y(), room.centerZ()+rotated.z());
                Material mat = parseMaterial(p[1]);
                if (isShulkerMarkerBlock(mat)) continue;
                target.setType(mat, false);
                if (p.length >= 3 && !p[2].isBlank()) {
                    BlockData data = Bukkit.createBlockData(p[2]);
                    rotateBlockData(data, effectiveStructureRotation(id, room.rotation()));
                    target.setBlockData(data, false);
                }
            }
            for (String raw : yaml.getStringList("templates." + id + ".entities")) spawnSerializedEntity(world, room, raw);
        } finally {
            endQuietPaste(world);
        }
    }

    private void clearTemplateArea(World world, RoomReservation room, int sizeX, int sizeY, int sizeZ) {
        if (!configs.get("dungeons/dungeons.yml").getBoolean("generation.reset-before-paste", true)) return;
        int maxX = Math.max(1, sizeX);
        int maxY = Math.max(1, sizeY);
        int maxZ = Math.max(1, sizeZ);
        for (Entity entity : roomEntities(world, room, maxX, maxY, maxZ)) {
            if (entity instanceof Player) continue;
            Location loc = entity.getLocation();
            if (loc.getBlockX() >= room.centerX() && loc.getBlockX() < room.centerX() + maxX && loc.getBlockY() >= room.y() && loc.getBlockY() < room.y() + maxY && loc.getBlockZ() >= room.centerZ() && loc.getBlockZ() < room.centerZ() + maxZ) entity.remove();
        }
        for (int x = 0; x < maxX; x++) {
            for (int y = 0; y < maxY; y++) {
                for (int z = 0; z < maxZ; z++) {
                    world.getBlockAt(room.centerX() + x, room.y() + y, room.centerZ() + z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void beginQuietPaste(World world) {
        if (world == null) return;
        physicsSuppressedWorlds.add(world.getName());
    }

    private void endQuietPaste(World world) {
        if (world == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> physicsSuppressedWorlds.remove(world.getName()), 2L);
    }

    private List<Entity> roomEntities(World world, RoomReservation room, int sizeX, int sizeY, int sizeZ) {
        if (world == null || room == null) return List.of();
        org.bukkit.util.BoundingBox box = new org.bukkit.util.BoundingBox(
            room.centerX(),
            room.y(),
            room.centerZ(),
            room.centerX() + Math.max(1, sizeX),
            room.y() + Math.max(1, sizeY),
            room.centerZ() + Math.max(1, sizeZ)
        );
        try {
            return new ArrayList<>(world.getNearbyEntities(box));
        } catch (Throwable ignored) {
            return new ArrayList<>(world.getEntities());
        }
    }

    private void applyDungeonWorldRules(World world) {
        if (world == null) return;
        if (configs.get("dungeons/dungeons.yml").getBoolean("world-generation.disable-mob-spawning", true)) {
            setGameRule(world, "doMobSpawning", false);
            setGameRule(world, "doPatrolSpawning", false);
            setGameRule(world, "doTraderSpawning", false);
            setGameRule(world, "doWardenSpawning", false);
        }
        if (configs.get("dungeons/dungeons.yml").getBoolean("world-generation.disable-weather", true)) {
            world.setStorm(false);
            world.setThundering(false);
            setGameRule(world, "doWeatherCycle", false);
        }
        if (configs.get("dungeons/dungeons.yml").getBoolean("world-generation.disable-random-ticks", true)) setGameRule(world, "randomTickSpeed", 0);
        setGameRule(world, "doDaylightCycle", false);
        setGameRule(world, "doInsomnia", false);
        setGameRule(world, "doImmediateRespawn", true);
    }
    private Location markerSpawn(String id, RoomReservation room) {
        var yaml = configs.get("dungeons/templates.yml"); World world = Bukkit.getWorld(room.world()); if (world == null) return null;
        for (String entry : yaml.getStringList("templates." + id + ".precise-markers")) {
            SavedMarker marker = parsePreciseMarker(entry);
            if (marker != null && marker.id().equalsIgnoreCase("player_spawn")) {
                return marker.world(room).add(0.0D, 1.62D, 0.0D);
            }
        }
        return null;
    }

    private Location generatedRoomSpawn(net.dark.threecore.dungeons.engine.PlacedDungeonRoom room) {
        if (room == null) return null;
        World world = Bukkit.getWorld(room.world());
        if (world == null) return null;
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        String path = "templates." + room.definition().id();
        for (String entry : yaml.getStringList(path + ".precise-markers")) {
            SavedMarker marker = parsePreciseMarker(entry);
            if (marker == null || !marker.id().equalsIgnoreCase("player_spawn")) continue;
            org.bukkit.util.Vector rotated = room.transform().localToWorld(new org.bukkit.util.Vector(marker.offsetX(), marker.offsetY(), marker.offsetZ()));
            float yaw = marker.yaw() + yawOffset(room.definition().effectiveRotation(room.rotation()));
            return safeSpawnLocation(new Location(world, rotated.getX(), rotated.getY(), rotated.getZ(), yaw, marker.pitch()));
        }
        for (String entry : yaml.getStringList(path + ".markers")) {
            if (!entry.contains(":player_spawn:")) continue;
            String[] xyz = entry.split(":")[0].split(",");
            if (xyz.length < 3) continue;
            org.bukkit.util.Vector rotated = room.transform().localToWorld(new org.bukkit.util.Vector(Integer.parseInt(xyz[0]) + 0.5D, Integer.parseInt(xyz[1]) + 1.0D, Integer.parseInt(xyz[2]) + 0.5D));
            return safeSpawnLocation(new Location(world, rotated.getX(), rotated.getY(), rotated.getZ(), yawFromFacing("SOUTH", 0.0F), 0.0F));
        }
        return null;
    }

    private Location safeSpawnLocation(Location location) {
        if (location == null || location.getWorld() == null) return location;
        Location target = location.clone();
        Block feet = target.getBlock();
        Block head = target.clone().add(0.0D, 1.0D, 0.0D).getBlock();
        if (!feet.getType().isAir() || !head.getType().isAir()) {
            for (int y = 0; y <= 3; y++) {
                Location candidate = location.clone().add(0.0D, y, 0.0D);
                if (candidate.getBlock().getType().isAir() && candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().getType().isAir()) return candidate;
            }
        }
        return target;
    }

    private float yawOffset(net.dark.threecore.dungeons.engine.DungeonRotation rotation) {
        return switch (rotation == null ? net.dark.threecore.dungeons.engine.DungeonRotation.NONE : rotation) {
            case CLOCKWISE_90 -> 90.0F;
            case CLOCKWISE_180 -> 180.0F;
            case COUNTERCLOCKWISE_90 -> -90.0F;
            default -> 0.0F;
        };
    }

    public void giveItem(Player player) { if (!configs.get("core/config.yml").getBoolean("spawn.hotbar-items.enabled", false) || net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || net.dark.threecore.duels.DuelService.isDuelPlayer(player) || isDungeonWorld(player.getWorld()) || !isSpawnWorld(player.getWorld())) { clearItem(player); return; } int slot = Math.max(0, Math.min(8, configs.get("dungeons/dungeons.yml").getInt("item.slot", 1))); clearItem(player); player.getInventory().setItem(slot, item()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { inventoryService.emergencyRestore(event.getPlayer()); giveItem(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isDungeonWorld(player.getWorld()) || activeRuns.containsKey(player.getUniqueId()) || ACTIVE_DUNGEON_PLAYERS.contains(player.getUniqueId())) {
            Location spawn = readConfiguredLocation("spawn");
            if (spawn != null) event.setRespawnLocation(spawn);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                clearPlayerState(player);
                player.setGameMode(GameMode.SURVIVAL);
                if (spawn != null && player.getWorld() != spawn.getWorld()) player.teleport(spawn);
                loadDungeonProfileWithStarterKit(player);
                clearDungeonHubItems(player);
                giveItem(player);
                applyDungeonSpawnBuffs(player);
                clearDungeonVision(player);
            });
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> giveItem(event.getPlayer()));
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onWorld(PlayerChangedWorldEvent event) { Player player = event.getPlayer(); if (isDungeonWorld(event.getFrom()) && !isDungeonEditorWorld(event.getFrom())) saveDungeonProfile(player); if (isDungeonEditorWorld(player.getWorld())) { clearPlayerState(player); clearDungeonHubItems(player); giveDevTool(player); } else if (isDungeonWorld(player.getWorld())) { Bukkit.getScheduler().runTask(plugin, () -> { if (!player.isOnline() || !isDungeonWorld(player.getWorld()) || isDungeonEditorWorld(player.getWorld())) return; loadDungeonProfileWithStarterKit(player); clearDungeonHubItems(player); giveItem(player); }); } if (isDungeonSpawnWorld(player.getWorld())) applyDungeonSpawnBuffs(player); else clearDungeonSpawnBuffs(player); }
    @EventHandler public void onMove(PlayerMoveEvent event) { if (event.getTo() == null) return; if (event.getFrom().getBlockX()==event.getTo().getBlockX() && event.getFrom().getBlockY()==event.getTo().getBlockY() && event.getFrom().getBlockZ()==event.getTo().getBlockZ()) return; if (isDungeonSpawnWorld(event.getPlayer().getWorld())) applyDungeonSpawnBuffs(event.getPlayer()); handleDungeonRoomProgress(event.getPlayer(), event.getTo()); }
    @EventHandler public void onEditorMarkerInteract(PlayerInteractAtEntityEvent event) { if (!isDungeonEditorWorld(event.getPlayer().getWorld())) return; if (!event.getPlayer().hasPermission("3smpcore.dungeon.editor") && !event.getPlayer().hasPermission("3smpcore.dungeons.admin")) return; if (!allEditorMarkers(event.getPlayer().getWorld()).contains(event.getRightClicked())) return; event.setCancelled(true); if (event.getPlayer().isSneaking()) clearNearbyEditorMarkers(event.getPlayer(), 12.0D); else { event.getRightClicked().remove(); Text.send(event.getPlayer(), "<yellow>Removed dungeon editor marker.</yellow>"); } }
    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getAction()!=Action.RIGHT_CLICK_AIR && event.getAction()!=Action.RIGHT_CLICK_BLOCK && event.getAction()!=Action.LEFT_CLICK_BLOCK) return;
        if (isBoulderTool(event.getItem())) {
            event.setCancelled(true);
            if (!isDungeonEditorWorld(event.getPlayer().getWorld())) return;
            handleBoulderToolClick(event);
            return;
        }
        if (isDevWand(event.getItem())) {
            event.setCancelled(true);
            if (!isDungeonEditorWorld(event.getPlayer().getWorld())) return;
            setEditorSelection(event.getPlayer(), event.getClickedBlock(), event.getAction() == Action.LEFT_CLICK_BLOCK);
            return;
        }
        if (event.getAction()!=Action.RIGHT_CLICK_AIR && event.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        if (isDevTool(event.getItem())) { event.setCancelled(true); if (!isDungeonEditorWorld(event.getPlayer().getWorld())) return; if (event.getPlayer().isSneaking()) openDevToolbox(event.getPlayer()); else saveTemplate(event.getPlayer(), "room_" + System.currentTimeMillis(), options(event.getPlayer()).level()); return; }
        if (isDevMarker(event.getItem())) { event.setCancelled(true); if (!isDungeonEditorWorld(event.getPlayer().getWorld())) return; placeDevMarker(event.getPlayer(), event.getItem().getType()); return; }
        if (isProtectedDungeonWorld(event.getPlayer().getWorld()) && !canBypassDungeonProtection(event.getPlayer()) && event.getClickedBlock() != null && !isAllowedDungeonInteraction(event.getClickedBlock().getType())) {
            event.setCancelled(true);
            return;
        }
        if (isItem(event.getItem())) { event.setCancelled(true); teleportDungeonSpawn(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonBlockBreak(BlockBreakEvent event) { if (protects("prevent-break") && isProtectedDungeonWorld(event.getPlayer().getWorld()) && !canBypassDungeonProtection(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonBlockPlace(BlockPlaceEvent event) { if (protects("prevent-place") && isProtectedDungeonWorld(event.getPlayer().getWorld()) && !canBypassDungeonProtection(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonBucketEmpty(PlayerBucketEmptyEvent event) { if (protects("prevent-buckets") && isProtectedDungeonWorld(event.getPlayer().getWorld()) && !canBypassDungeonProtection(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonBucketFill(PlayerBucketFillEvent event) { if (protects("prevent-buckets") && isProtectedDungeonWorld(event.getPlayer().getWorld()) && !canBypassDungeonProtection(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonBlockBurn(BlockBurnEvent event) { if (protects("prevent-fire") && isProtectedDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonBlockIgnite(BlockIgniteEvent event) { if (protects("prevent-fire") && isProtectedDungeonWorld(event.getBlock().getWorld()) && (event.getPlayer() == null || !canBypassDungeonProtection(event.getPlayer()))) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonEntityExplode(EntityExplodeEvent event) { if (protects("prevent-explosions") && isProtectedDungeonWorld(event.getLocation().getWorld())) event.blockList().clear(); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonBlockExplode(BlockExplodeEvent event) { if (protects("prevent-explosions") && isProtectedDungeonWorld(event.getBlock().getWorld())) event.blockList().clear(); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonEntityChangeBlock(EntityChangeBlockEvent event) { if (isProtectedDungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonHangingBreak(HangingBreakByEntityEvent event) { if (isProtectedDungeonWorld(event.getEntity().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDungeonPhysics(BlockPhysicsEvent event) { World world = event.getBlock().getWorld(); if (physicsSuppressedWorlds.contains(world.getName()) || (isDungeonInstanceWorld(world) && configs.get("dungeons/dungeons.yml").getBoolean("generation.cancel-physics-in-runs", true))) event.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isBoulderTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            saveBoulderTool(player, player.isSneaking());
            return;
        }
        if (isDungeonInventoryRestricted(player) && !configs.get("dungeons/dungeons.yml").getBoolean("inventory.allow-drop", false) && !canBypassDungeonProtection(player)) {
            event.setCancelled(true);
            return;
        }
        if (isItem(event.getItemDrop().getItemStack()) || isDevTool(event.getItemDrop().getItemStack()) || isDevWand(event.getItemDrop().getItemStack()) || isDevMarker(event.getItemDrop().getItemStack())) { event.setCancelled(true); if (isDungeonEditorWorld(event.getPlayer().getWorld())) Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(event.getPlayer())); }
    }
    @EventHandler public void onClick(InventoryClickEvent event) { if (event.getInventory().getHolder() instanceof DungeonHolder holder) { event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player p)) return; DungeonRunOptions options = options(p); int raw = event.getRawSlot(); if (holder.context().startsWith("boulder-panel:")) { handleBoulderPanelClick(p, holder.context(), raw); return; } if (raw == 22 && !holder.context().equals("dev-toolbox")) { playBackSound(p); openMenu(p); return; } switch (holder.context()) { case "main" -> { if (raw == 10) { playBackSound(p); openLevelMenu(p); } else if (raw == 12) { playBackSound(p); openDifficultyMenu(p); } else if (raw == 14) { playBackSound(p); openPartyMenu(p); } else if (raw == 16) enter(p, options.level()); } case "levels" -> { int[] slots={10,11,12,13,14}; int idx=-1; for(int i=0;i<slots.length;i++) if(slots[i]==raw) idx=i; List<String> levels=levelIds(); if(idx>=0&&idx<levels.size()){String level=levels.get(idx); if(!configs.get("dungeons/dungeons.yml").getBoolean("levels."+level+".coming-soon",false)) options.level(level); playBackSound(p); openMenu(p);} } case "difficulty" -> { if(raw==10) options.difficulty()[0]="easy"; else if(raw==12) options.difficulty()[0]="normal"; else if(raw==14) options.difficulty()[0]="hard"; else if(raw==16) { if (canUseNightmare(p, options.level())) options.difficulty()[0]="nightmare"; else Text.send(p, "<red>Beat Easy, Normal and Hard on this level before Nightmare.</red>"); } playBackSound(p); openMenu(p); } case "party" -> { if(raw==11) options.party()[0]=false; else if(raw==15 && partyService!=null && partyService.isLeader(p.getUniqueId())) options.party()[0]=true; else if (raw==15) Text.send(p, "<yellow>Only the party leader can choose party dungeons.</yellow>"); playBackSound(p); openMenu(p); } case "dev-toolbox" -> handleDevToolboxClick(p, raw); default -> { } } return; } if (event.getWhoClicked() instanceof Player p && shouldCancelDungeonInventoryClick(p, event)) { event.setCancelled(true); return; } if (isItem(event.getCurrentItem())) { event.setCancelled(true); clearCursor(event); if (event.getWhoClicked() instanceof Player p && event.getClickedInventory()==p.getInventory()) { p.setItemOnCursor(null); p.updateInventory(); teleportDungeonSpawn(p); } return; } if (event.getWhoClicked() instanceof Player p && isDungeonEditorWorld(p.getWorld()) && (isDevTool(event.getCurrentItem()) || isDevWand(event.getCurrentItem()) || isDevMarker(event.getCurrentItem()) || isDevTool(event.getCursor()) || isDevWand(event.getCursor()) || isDevMarker(event.getCursor()))) { event.setCancelled(true); clearCursor(event); Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(p)); } }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (!(event.getWhoClicked() instanceof Player player)) return; if (isDungeonInventoryRestricted(player)) { if (!configs.get("dungeons/dungeons.yml").getBoolean("inventory.allow-drag", true) && !canBypassDungeonProtection(player)) { event.setCancelled(true); return; } if (configs.get("dungeons/dungeons.yml").getBoolean("inventory.block-external-inventories", true) && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) { event.setCancelled(true); return; } } if (!isDungeonEditorWorld(player.getWorld())) return; if (isDevTool(event.getOldCursor()) || isDevWand(event.getOldCursor()) || isDevMarker(event.getOldCursor())) { event.setCancelled(true); Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(player)); } }
    @EventHandler public void onEditorMarkerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!isDungeonEditorWorld(stand.getWorld())) return;
        if (armorStandMarker(stand) == null) return;
        removeEditorTrapPreviewForMarker(stand);
    }
    @EventHandler public void onEditorMarkerDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!isDungeonEditorWorld(stand.getWorld())) return;
        if (armorStandMarker(stand) == null) return;
        removeEditorTrapPreviewForMarker(stand);
    }
    @EventHandler public void onMobDeath(EntityDeathEvent event) { if (event.getEntity().getKiller() == null) return; Player killer = event.getEntity().getKiller(); boolean bossKill = event.getEntity().getPersistentDataContainer().has(new NamespacedKey(plugin, DUNGEON_BOSS_KEY)); net.dark.threecore.dungeons.runtime.DungeonSession session = getDungeonSession(killer); if (session != null) { if (bossKill && !session.bossDefeated()) { session.bossDefeated(true); openRuntimeBossDoors(session); Text.actionBar(killer, "<green>Boss defeated. Exit unlocked.</green>"); } return; } ActiveDungeonRun run = activeRuns.get(killer.getUniqueId()); if (run == null) return; int kills = run.kills() + 1; boolean firstBossKill = bossKill && !run.bossDefeated(); boolean bossDefeated = run.bossDefeated() || bossKill; activeRuns.put(killer.getUniqueId(), new ActiveDungeonRun(run.level(), run.difficulty(), bossDefeated, kills)); double amount = moneyPerKill(run.difficulty()); repository.setMoneyBalance(killer.getUniqueId(), repository.getMoneyBalance(killer.getUniqueId()) + amount); if (firstBossKill) rewardBossClear(killer, run); Text.actionBar(killer, "<gradient:#4c1d95:#a78bfa>Dungeon kill</gradient> <gray>+ $" + amount + "</gray>" + (bossDefeated ? " <green>Boss defeated. Exit room unlocked.</green>" : "")); }
    @EventHandler public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isDungeonWorld(event.getLocation().getWorld())) return;
        if (isDungeonMobSpawnWindowOpen(event.getLocation().getWorld())) return;
        List<String> allowedReasons = configs.get("dungeons/dungeons.yml").getStringList("world-generation.allowed-spawn-reasons");
        if (allowedReasons.isEmpty()) allowedReasons = List.of("SPAWNER");
        String reason = event.getSpawnReason().name();
        if (allowedReasons.stream().anyMatch(value -> value.equalsIgnoreCase(reason))) {
            return;
        }
        event.setCancelled(true);
    }

    private boolean isDungeonMobSpawnWindowOpen(World world) {
        if (world == null) return false;
        Long until = allowedDungeonMobSpawnWindows.get(world.getName());
        if (until == null) return false;
        if (until >= System.currentTimeMillis()) return true;
        allowedDungeonMobSpawnWindows.remove(world.getName());
        return false;
    }

    private void openDungeonMobSpawnWindow(World world) {
        if (world == null) return;
        allowedDungeonMobSpawnWindows.put(world.getName(), System.currentTimeMillis() + 2_500L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long until = allowedDungeonMobSpawnWindows.get(world.getName());
            if (until != null && until < System.currentTimeMillis()) allowedDungeonMobSpawnWindows.remove(world.getName());
        }, 80L);
    }

    @EventHandler public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isDungeonWorld(player.getWorld()) && !activeRuns.containsKey(player.getUniqueId()) && !ACTIVE_DUNGEON_PLAYERS.contains(player.getUniqueId())) return;
        if (isDungeonEditorWorld(player.getWorld())) return;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        saveDungeonProfile(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            Location spawn = readConfiguredLocation("spawn");
            if (spawn != null && player.getWorld() != spawn.getWorld()) player.teleport(spawn);
            clearPlayerState(player);
            player.setGameMode(GameMode.SURVIVAL);
            loadDungeonProfile(player);
            applyDungeonSpawnBuffs(player);
            clearDungeonVision(player);
            if (socialTabService != null) socialTabService.refreshAll();
            Text.send(player, "<yellow>You were downed.</yellow> <gray>Your dungeon inventory stayed intact.</gray>");
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        readyManager.removePlayer(event.getPlayer());
        net.dark.threecore.dungeons.runtime.DungeonSession session = getDungeonSession(event.getPlayer());
        if (session != null) {
            leaveRuntimeDungeon(event.getPlayer(), session, false);
            return;
        }
        if (isDungeonWorld(event.getPlayer().getWorld()) && !isDungeonEditorWorld(event.getPlayer().getWorld())) saveDungeonProfile(event.getPlayer());
        World instance = instanceWorldsByPlayer.get(event.getPlayer().getUniqueId());
        clearDungeonRunState(event.getPlayer().getUniqueId());
        cleanupDungeonInstanceIfEmpty(instance);
    }

    private void enterParty(Player player, String level) {
        if (partyService == null || !partyService.isInParty(player.getUniqueId())) { Text.send(player, "<red>Create or join a party first.</red>"); return; }
        if (!partyService.isLeader(player.getUniqueId())) { Text.send(player, "<yellow>Only the party leader can start a party dungeon.</yellow>"); return; }
        String difficulty = options(player).difficulty()[0];
        java.util.LinkedHashSet<UUID> members = new java.util.LinkedHashSet<>(partyService.partyMembers(player.getUniqueId()));
        if (members.isEmpty()) members.add(player.getUniqueId());
        java.util.LinkedHashSet<UUID> onlineIds = new java.util.LinkedHashSet<>(partyService.onlinePartyMembers(player.getUniqueId()));
        onlineIds.add(player.getUniqueId());
        java.util.List<Player> onlineMembers = onlineIds.stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).filter(Player::isOnline).toList();
        if (onlineMembers.isEmpty()) { Text.send(player, "<red>No online party members were found.</red>"); return; }
        if (onlineMembers.size() < members.size()) {
            Text.send(player, "<yellow>Starting with online party members only.</yellow> <gray>Offline members stay in the party and will be skipped for this run.</gray>");
        }
        for (Player member : onlineMembers) {
            if (!canStartDifficulty(member, level, difficulty)) return;
            if (getDungeonSession(member) != null || activeRuns.containsKey(member.getUniqueId()) || ACTIVE_DUNGEON_PLAYERS.contains(member.getUniqueId())) {
                Text.send(player, "<red>" + member.getName() + " is already in a dungeon.</red>");
                return;
            }
        }
        UUID sessionId = startDungeonApi(onlineMembers, level);
        if (sessionId == null) {
            Text.send(player, "<red>Party dungeon generation failed.</red> <gray>Use /3smpcore dungeon debug generate for the exact room/connector reason.</gray>");
            return;
        }
        for (Player member : onlineMembers) {
            activeRuns.put(member.getUniqueId(), new ActiveDungeonRun(level, difficulty, false, 0));
            activeLayouts.put(member.getUniqueId(), List.of());
            activeGroups.put(member.getUniqueId(), new java.util.LinkedHashSet<>(onlineIds));
            Text.send(member, "<gradient:#4c1d95:#a78bfa>Party dungeon started.</gradient> <gray>Level:</gray> <white>" + level + "</white> <gray>Difficulty:</gray> <white>" + difficulty + "</white> <gray>Session:</gray> <white>" + sessionId + "</white>");
        }
        if (socialTabService != null) socialTabService.refreshAll();
    }

    private void rewardBossClear(Player player, ActiveDungeonRun run) {
        repository.markDungeonCompletion(player.getUniqueId(), run.level(), run.difficulty());
        long sapphires = configs.get("dungeons/dungeons.yml").getLong("rewards.boss-clear.sapphires", 25L);
        double money = configs.get("dungeons/dungeons.yml").getDouble("rewards.boss-clear.money", 10000.0D);
        repository.setSapphireBalance(player.getUniqueId(), repository.getSapphireBalance(player.getUniqueId()) + sapphires);
        repository.setMoneyBalance(player.getUniqueId(), repository.getMoneyBalance(player.getUniqueId()) + money);
        Text.send(player, "<gradient:#4c1d95:#a78bfa>Boss defeated!</gradient> <gray>Rewards:</gray> <white>" + sapphires + " sapphires</white> <gray>and</gray> <white>$" + String.format(Locale.ROOT, "%,.0f", money) + "</white>");
    }

    private boolean canStartDifficulty(Player player, String level, String difficulty) {
        if (!difficulty.equalsIgnoreCase("nightmare")) return true;
        if (canUseNightmare(player, level)) return true;
        Text.send(player, "<red>Nightmare is locked.</red> <gray>Beat Easy, Normal and Hard on this dungeon level first.</gray>");
        return false;
    }

    private boolean canUseNightmare(Player player, String level) {
        List<String> requiredDifficulties = configs.get("dungeons/dungeons.yml").getStringList("nightmare.required-completions");
        if (requiredDifficulties.isEmpty()) requiredDifficulties = List.of("easy", "normal", "hard");
        for (String required : requiredDifficulties) {
            if (!repository.hasDungeonCompletion(player.getUniqueId(), level, required)) return false;
        }
        return true;
    }

    private List<String> buildRoomPlan(String level, String difficulty) {
        List<String> entrance = templates(level, "entrance");
        List<String> normal = templates(level, "normal");
        List<String> boss = templates(level, "boss");
        List<String> exit = templates(level, "exit");
        if (entrance.isEmpty()) entrance = templates(level, "any");
        if (normal.isEmpty()) normal = templates(level, "any");
        if (entrance.isEmpty()) return List.of();
        int target = targetRoomCount(difficulty);
        String selectedBoss = boss.isEmpty() ? null : boss.get(new Random().nextInt(boss.size()));
        String selectedExit = exit.isEmpty() ? null : exit.get(new Random().nextInt(exit.size()));
        if (selectedBoss != null && selectedBoss.equals(selectedExit)) selectedExit = null;
        int fixedRooms = 1 + (selectedBoss == null ? 0 : 1) + (selectedExit == null ? 0 : 1);
        int normalTarget = Math.max(0, target - fixedRooms);
        List<String> shuffled = new ArrayList<>(normal); Collections.shuffle(shuffled);
        List<String> plan = new ArrayList<>();
        plan.add(entrance.get(new Random().nextInt(entrance.size())));
        for (int i = 0; i < normalTarget && !shuffled.isEmpty(); i++) {
            if (i > 0 && i % shuffled.size() == 0) Collections.shuffle(shuffled);
            String candidate = shuffled.get(i % shuffled.size());
            if (i < shuffled.size()) {
                if (!plan.contains(candidate)) plan.add(candidate);
            } else {
                plan.add(candidate);
            }
        }
        while (plan.size() < 1 + normalTarget && !shuffled.isEmpty()) {
            plan.add(shuffled.get(new Random().nextInt(shuffled.size())));
        }
        if (selectedBoss != null) plan.add(selectedBoss);
        if (selectedExit != null) plan.add(selectedExit);
        return plan;
    }

    private int targetRoomCount(String difficulty) {
        String key = difficulty.toLowerCase(Locale.ROOT);
        if (key.equals("medium")) key = "normal";
        int fallback = configs.get("dungeons/dungeons.yml").getInt("generation.rooms.easy", 30);
        return Math.max(1, configs.get("dungeons/dungeons.yml").getInt("generation.rooms." + key, fallback));
    }

    private List<RoomReservation> pasteRoomPlan(List<String> plan, RoomReservation start) {
        List<RoomReservation> placed = new ArrayList<>();
        RoomReservation previousRoom = null;
        String previousTemplate = null;
        for (int i = 0; i < plan.size(); i++) {
            String id = plan.get(i);
            String roomKey = start.key() + ":" + i + ":" + id;
            RoomReservation room = previousRoom == null ? new RoomReservation(roomKey, start.world(), start.level(), start.centerX(), start.y(), start.centerZ(), StructureRotation.NONE) : smartRoomPlacement(roomKey, previousRoom, previousTemplate, id, placed);
            if (room == null) {
                debug(null, "<yellow>Skipped room</yellow> <white>" + id + "</white> <yellow>because no rotated connector path could place it without overlap. Fix this room's entrance/connector markers or add more connector rooms.</yellow>");
                continue;
            }
            pasteTemplate(id, room);
            placed.add(room);
            previousRoom = room;
            previousTemplate = id;
            debug(null, "<gray>Placed room</gray> <white>" + id + "</white> <gray>at</gray> <white>" + room.centerX() + "," + room.y() + "," + room.centerZ() + "</white> <gray>using collision-safe marker chaining.</gray>");
        }
        return placed;
    }

    private List<String> templates(String level, String role) {
        var sec = configs.get("dungeons/templates.yml").getConfigurationSection("templates");
        if (sec == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String id : sec.getKeys(false)) {
            if (!level.equalsIgnoreCase(sec.getString(id + ".level"))) continue;
            String foundRole = sec.getString(id + ".role", "normal");
            if (role.equals("any") || role.equalsIgnoreCase(foundRole)) out.add(id);
        }
        return out;
    }

    private String detectRoomRole(List<String> markers) {
        boolean spawn = markers.stream().anyMatch(m -> m.contains(":player_spawn:"));
        boolean boss = markers.stream().anyMatch(m -> m.contains(":boss:"));
        boolean exit = markers.stream().anyMatch(m -> m.contains(":exit:"));
        if (spawn) return "entrance";
        if (boss) return "boss";
        if (exit) return "exit";
        return "normal";
    }

    private String sizeCategory(int x, int y, int z) {
        int volume = x * y * z;
        if (volume >= 48 * 48 * 32) return "large";
        if (volume >= 24 * 24 * 16) return "medium";
        return "small";
    }

        private DungeonRunOptions options(Player player) { return menuOptions.computeIfAbsent(player.getUniqueId(), ignored -> new DungeonRunOptions(configs.get("dungeons/dungeons.yml").getString("default-level", "jungle"), new boolean[]{false}, new String[]{"easy"})); }
    private String nextDifficulty(String current) { return switch (current.toLowerCase(Locale.ROOT)) { case "easy" -> "normal"; case "normal" -> "hard"; case "hard" -> "nightmare"; default -> "easy"; }; }
    private double moneyPerKill(String difficulty) { return configs.get("dungeons/dungeons.yml").getDouble("rewards.money-per-kill." + difficulty.toLowerCase(Locale.ROOT), difficulty.equalsIgnoreCase("nightmare") ? 75.0D : difficulty.equalsIgnoreCase("hard") ? 30.0D : difficulty.equalsIgnoreCase("normal") ? 20.0D : 10.0D); }
    private Material difficultyIcon(String difficulty) { return switch (difficulty.toLowerCase(Locale.ROOT)) { case "nightmare" -> Material.BLACK_WOOL; case "hard" -> Material.RED_WOOL; case "normal" -> Material.YELLOW_WOOL; default -> Material.WHITE_WOOL; }; }
    private void applyBaseKit(Player player) { clearPlayerState(player); player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.LEATHER_BOOTS), new ItemStack(Material.LEATHER_LEGGINGS), new ItemStack(Material.LEATHER_CHESTPLATE), new ItemStack(Material.LEATHER_HELMET)}); player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD), new ItemStack(Material.COOKED_BEEF, 8)); }
    private void clearPlayerState(Player player) { player.getInventory().clear(); player.getInventory().setArmorContents(new ItemStack[4]); player.getInventory().setItemInOffHand(new ItemStack(Material.AIR)); player.setItemOnCursor(null); player.updateInventory(); }
    private void startHealthIndicatorTask() {
        if (healthIndicatorTask != null && !healthIndicatorTask.isCancelled()) return;
        healthIndicatorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHealthIndicatorScores, 0L, 10L);
    }

    private void updateHealthIndicatorScores() {
        if (Bukkit.getScoreboardManager() == null) return;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = scoreboard.getObjective("dungeon_health");
        if (objective == null) objective = scoreboard.registerNewObjective("dungeon_health", Criteria.DUMMY, Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>❤</gradient>"));
        objective.displayName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>❤</gradient>"));
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        boolean any = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ACTIVE_DUNGEON_PLAYERS.contains(player.getUniqueId())) {
                scoreboard.resetScores(player.getName());
                continue;
            }
            any = true;
            objective.getScore(player.getName()).setScore(Math.max(0, (int) Math.ceil(player.getHealth())));
        }
        if (!any) {
            objective.unregister();
            if (healthIndicatorTask != null) {
                healthIndicatorTask.cancel();
                healthIndicatorTask = null;
            }
        }
    }

    private void resetHealthScore(UUID uuid) {
        if (Bukkit.getScoreboardManager() == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        scoreboard.resetScores(player.getName());
    }

    private void clearDungeonRunState(UUID uuid) {
        activeRuns.remove(uuid);
        ACTIVE_DUNGEON_PLAYERS.remove(uuid);
        activeGroups.remove(uuid);
        activeLayouts.remove(uuid);
        instanceWorldsByPlayer.remove(uuid);
        resetHealthScore(uuid);
    }
    private void cleanupDungeonInstanceIfEmpty(World world) {
        if (world == null || !isDungeonInstanceWorld(world)) return;
        boolean stillUsed = instanceWorldsByPlayer.values().stream().anyMatch(active -> active != null && active.getUID().equals(world.getUID()));
        if (stillUsed) return;
        String name = world.getName();
        Bukkit.unloadWorld(world, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteWorldFolder(Bukkit.getWorldContainer().toPath().resolve(name)));
    }
    private boolean isDungeonInstanceWorld(World world) {
        return world != null && isDungeonInstanceWorldName(world.getName());
    }
    private boolean isDungeonInstanceWorldName(String worldName) {
        return worldName != null && worldName.startsWith(configs.get("dungeons/dungeons.yml").getString("world-generation.instance-prefix", "dungeon_run_"));
    }
    private void deleteWorldFolder(Path path) {
        if (path == null || !Files.exists(path)) return;
        try {
            Files.walk(path).sorted(Comparator.reverseOrder()).forEach(file -> {
                try { Files.deleteIfExists(file); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
    }
    private void cleanupStaleDungeonInstances() {
        String prefix = configs.get("dungeons/dungeons.yml").getString("world-generation.instance-prefix", "dungeon_run_");
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            if (!world.getName().startsWith(prefix)) continue;
            Bukkit.unloadWorld(world, false);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Path container = Bukkit.getWorldContainer().toPath();
            if (!Files.exists(container)) return;
            try (var paths = Files.list(container)) {
                paths.filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .forEach(this::deleteWorldFolder);
            } catch (IOException ignored) {
            }
        });
    }

    private RoomBox selectedBounds(Player player) {
        Location first = editorPos1.get(player.getUniqueId());
        Location second = editorPos2.get(player.getUniqueId());
        if (first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld())) {
            return new RoomBox(Math.min(first.getBlockX(), second.getBlockX()), Math.min(first.getBlockY(), second.getBlockY()), Math.min(first.getBlockZ(), second.getBlockZ()), Math.max(first.getBlockX(), second.getBlockX()), Math.max(first.getBlockY(), second.getBlockY()), Math.max(first.getBlockZ(), second.getBlockZ()));
        }
        return null;
    }

    private void setEditorSelection(Player player, Block clicked, boolean firstPoint) {
        Location selected = clicked == null ? player.getLocation().getBlock().getLocation() : clicked.getLocation();
        if (firstPoint) editorPos1.put(player.getUniqueId(), selected);
        else editorPos2.put(player.getUniqueId(), selected);
        RoomBox box = selectedBounds(player);
        String size = box == null ? "" : " <gray>Size:</gray> <white>" + (box.maxX() - box.minX() + 1) + "x" + (box.maxY() - box.minY() + 1) + "x" + (box.maxZ() - box.minZ() + 1) + "</white>";
        Text.send(player, "<green>Dungeon pos" + (firstPoint ? "1" : "2") + " set.</green> <gray>" + selected.getBlockX() + "," + selected.getBlockY() + "," + selected.getBlockZ() + "</gray>" + size);
    }

    private List<Block> nearby(Player player, Material material) { List<Block> out = new ArrayList<>(); Location c = player.getLocation(); int r = 80; for (int x=c.getBlockX()-r;x<=c.getBlockX()+r;x++) for(int y=Math.max(player.getWorld().getMinHeight(), c.getBlockY()-r);y<=Math.min(player.getWorld().getMaxHeight()-1,c.getBlockY()+r);y++) for(int z=c.getBlockZ()-r;z<=c.getBlockZ()+r;z++) { Block b=player.getWorld().getBlockAt(x,y,z); if(b.getType()==material) out.add(b); } return out; }
    private Marker marker(Material m) { return switch(m) { case CRYING_OBSIDIAN -> new Marker("boss_spawner"); default -> null; }; }
    private boolean isShulkerMarkerBlock(Material material) { return material != null && material.name().endsWith("SHULKER_BOX"); }
    private String markerId(Material material) {
        return switch (material) {
            case EMERALD -> "player_spawn";
            case GOLD_INGOT -> "entrance";
            case COPPER_INGOT -> "connector";
            case LAPIS_LAZULI -> "enemy_spawn";
            case COMPASS -> "room_facing";
            case REDSTONE -> "exit";
            case COAL -> "boss";
            case CRYING_OBSIDIAN -> "boss_spawner";
            case DEEPSLATE -> "trap_boulder";
            case POINTED_DRIPSTONE -> "trap_spike";
            case OAK_PLANKS -> "trap_bridge";
            case IRON_BARS -> "boss_door";
            default -> null;
        };
    }
    private String facing(Block b) { return b.getBlockData() instanceof Directional d ? d.getFacing().name() : "UP"; }
    private String serializeBlockData(Block block) {
        BlockData data = block.getBlockData();
        return block.getType().name() + "|" + data.getAsString();
    }
    private String firstTemplate(String level) { var sec=configs.get("dungeons/templates.yml").getConfigurationSection("templates"); if(sec==null)return null; for(String id:sec.getKeys(false)) if(level.equalsIgnoreCase(sec.getString(id+".level"))) return id; return null; }
    private void listTemplates(CommandSender s) { var sec=configs.get("dungeons/templates.yml").getConfigurationSection("templates"); Text.send(s, "<gray>Templates: " + (sec==null?"none":String.join(", ",sec.getKeys(false))) + "</gray>"); }
    private RoomReservation allocate(UUID uuid, String level, World world) { String key=uuid+":"+level; if(reservations.containsKey(key))return reservations.get(key); int spacing=configs.get("dungeons/dungeons.yml").getInt("generation.spacing",96); int y=configs.get("dungeons/dungeons.yml").getInt("levels."+level+".y",80); int idx=reservations.size(); int cols=Math.max(1, configs.get("dungeons/dungeons.yml").getInt("generation.columns",8)); RoomReservation r=new RoomReservation(key,world.getName(),level,(idx%cols)*spacing,y,(idx/cols)*spacing, StructureRotation.NONE); reservations.put(key,r); saveReservations(); return r; }
    private RoomReservation runStart(UUID uuid, String level, World world) { int y = configs.get("dungeons/dungeons.yml").getInt("levels." + level + ".y", 80); return new RoomReservation(uuid + ":" + level + ":run:start", world.getName(), level, 0, y, 0, StructureRotation.NONE); }
    private World createDungeonInstanceWorld(UUID owner, String level) {
        String prefix = configs.get("dungeons/dungeons.yml").getString("world-generation.instance-prefix", "dungeon_run_");
        String name = prefix + level.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_") + "_" + owner.toString().substring(0, 8) + "_" + Long.toString(System.currentTimeMillis(), 36);
        World world = loadDungeonWorld(name);
        if (world != null) {
            world.setAutoSave(false);
        }
        return world;
    }
    private World dungeonWorld(){ return loadDungeonWorld(configs.get("dungeons/dungeons.yml").getString("world","dungeons")); }
    private World dungeonEditorWorld(){ return loadDungeonWorld(configs.get("dungeons/dungeons.yml").getString("editor-world","dungeons_editor")); }
    private World dungeonSpawnWorld(){ return loadDungeonWorld(configs.get("dungeons/dungeons.yml").getString("spawn.world","dungeons_spawn")); }
    private World loadDungeonWorld(String name){
        World w = Bukkit.getWorld(name);
        if (w != null) {
            applyDungeonWorldRules(w);
            return w;
        }
        WorldCreator creator = new WorldCreator(name);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        creator.type(WorldType.FLAT);
        creator.generator(new VoidChunkGenerator());
        World created = Bukkit.createWorld(creator);
        if (created == null) return null;
        applyDungeonWorldRules(created);
        registerDungeonWorldWithMultiverse(created);
        return created;
    }
    private void registerDungeonWorldWithMultiverse(World world) {
        if (world == null || Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) return;
        if (!configs.get("dungeons/dungeons.yml").getBoolean("world-generation.multiverse", true)) return;
        if (isDungeonInstanceWorld(world)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + world.getName() + " normal");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set hidden true");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify " + world.getName() + " set pvp true");
        });
    }
    private void unregisterDungeonWorldFromMultiverse(String worldName) {
        if (worldName == null || Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null) return;
        if (isDungeonInstanceWorldName(worldName)) return;
        if (!configs.get("dungeons/dungeons.yml").getBoolean("world-generation.multiverse", true)) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv remove " + worldName);
    }
    private void clearItem(Player player){ for(int i=0;i<player.getInventory().getSize();i++) if(isItem(player.getInventory().getItem(i))) player.getInventory().setItem(i,null); }
    private ItemStack item(){ return tagged(Material.COMPASS, configs.get("dungeons/dungeons.yml").getString("item.name","<gradient:#4c1d95:#a78bfa>Dungeons</gradient>")); }
    private ItemStack tagged(Material mat,String name){ return tagged(mat, name, ITEM_ID); }
    private ItemStack tagged(Material mat,String name,String id){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm(name)); String lore = id.equals(DEV_TOOL_ID) ? "<gray>Right-click save. Shift-right-click toolbox.</gray>" : id.equals(DEV_WAND_ID) ? "<gray>Left-click pos1. Right-click pos2.</gray>" : "<gray>Click to open dungeons.</gray>"; m.lore(List.of(Text.mm(lore))); m.getPersistentDataContainer().set(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING, id); s.setItemMeta(m); return s; }
    private boolean isItem(ItemStack i){ return i!=null&&i.hasItemMeta()&&ITEM_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private boolean isDevTool(ItemStack i){ return i!=null&&i.hasItemMeta()&&DEV_TOOL_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private boolean isDevWand(ItemStack i){ return i!=null&&i.hasItemMeta()&&DEV_WAND_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private boolean isDevMarker(ItemStack i){ return i!=null&&i.hasItemMeta()&&DEV_MARKER_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private boolean isBoulderTool(ItemStack i){ return i!=null&&i.hasItemMeta()&&BOULDER_TOOL_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private ItemStack devMarker(Material mat, String label){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm("<gradient:#4c1d95:#a78bfa>"+label+" Marker</gradient>")); m.lore(List.of(Text.mm("<gray>Right-click to place this marker.</gray>"))); m.getPersistentDataContainer().set(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING, DEV_MARKER_ID); s.setItemMeta(m); return s; }
    private ItemStack pane(){ return button(Material.GRAY_STAINED_GLASS_PANE," ",List.of()); }
    private ItemStack button(Material mat,String name,List<String> lore){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm(name)); m.lore(lore.stream().map(Text::mm).toList()); s.setItemMeta(m); return s; }
    private void clearCursor(InventoryClickEvent event){ event.setCursor(null); if(event.getWhoClicked() instanceof Player p) Bukkit.getScheduler().runTask(plugin, p::updateInventory); }
    private void enforceEditorToolbar(Player player){ if(!isDungeonEditorWorld(player.getWorld())) return; ItemStack tool = null; ItemStack wand = null; for(int i=0;i<player.getInventory().getSize();i++){ ItemStack item = player.getInventory().getItem(i); if(isDevTool(item)){ tool = item.clone(); if(i != DEV_TOOL_SLOT) player.getInventory().setItem(i, null); } if(isDevWand(item)){ wand = item.clone(); if(i != DEV_TOOL_SLOT - 1) player.getInventory().setItem(i, null); } } player.getInventory().setItem(DEV_TOOL_SLOT, tool == null ? tagged(Material.STRUCTURE_BLOCK, "<gradient:#4c1d95:#a78bfa>Dungeon Room Saver</gradient>", DEV_TOOL_ID) : tool); player.getInventory().setItem(DEV_TOOL_SLOT - 1, wand == null ? tagged(Material.WOODEN_AXE, "<gradient:#34d399:#22c55e>Dungeon Selection Wand</gradient>", DEV_WAND_ID) : wand); player.updateInventory(); }
    private boolean isDungeonEditorWorld(World world){ return world != null && world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("editor-world","dungeons_editor")); }
    private <T> void setGameRule(World world, String ruleName, T value){ @SuppressWarnings("unchecked") GameRule<T> rule = (GameRule<T>) Registry.GAME_RULE.get(NamespacedKey.minecraft(toSnakeCase(ruleName))); if(rule != null) world.setGameRule(rule, value); }
    private String toSnakeCase(String value){ return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT); }
    private String nameOf(UUID uuid){ Player online = Bukkit.getPlayer(uuid); if(online != null) return online.getName(); var offline = Bukkit.getOfflinePlayer(uuid); return offline.getName() == null ? uuid.toString() : offline.getName(); }
    private Material parseMaterial(String in){ try{return Material.valueOf(in.toUpperCase(Locale.ROOT));}catch(Exception e){return Material.STONE;} }
    private Location readConfiguredLocation(String path) { var s = configs.get("dungeons/dungeons.yml").getConfigurationSection(path); if (s == null) return null; String fallback = path.equalsIgnoreCase("spawn") ? configs.get("dungeons/dungeons.yml").getString("spawn.world", configs.get("dungeons/dungeons.yml").getString("world", "dungeons_spawn")) : configs.get("dungeons/dungeons.yml").getString("world", "dungeons"); World w = Bukkit.getWorld(s.getString("world", fallback)); return w == null ? null : new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")); }
    private void writeLocation(org.bukkit.configuration.file.YamlConfiguration y, String p, Location l) { y.set(p+".world", l.getWorld().getName()); y.set(p+".x", l.getX()); y.set(p+".y", l.getY()); y.set(p+".z", l.getZ()); y.set(p+".yaw", l.getYaw()); y.set(p+".pitch", l.getPitch()); }
    private List<String> levelIds(){ var sec=configs.get("dungeons/dungeons.yml").getConfigurationSection("levels"); return sec==null?List.of("jungle"):List.copyOf(sec.getKeys(false)); }
    private void loadReservations(){ var sec=configs.get("dungeons/rooms.yml").getConfigurationSection("rooms"); if(sec==null)return; for(String k:sec.getKeys(false)) reservations.put(k,new RoomReservation(k,sec.getString(k+".world"),sec.getString(k+".level"),sec.getInt(k+".x"),sec.getInt(k+".y"),sec.getInt(k+".z"), StructureRotation.NONE)); }
    private void saveReservations(){ var y=configs.get("dungeons/rooms.yml"); y.set("rooms",null); for(RoomReservation r:reservations.values()){String p="rooms."+r.key(); y.set(p+".world",r.world()); y.set(p+".level",r.level()); y.set(p+".x",r.centerX()); y.set(p+".y",r.y()); y.set(p+".z",r.centerZ());} try{y.save(new File(plugin.getDataFolder(),"dungeons/rooms.yml"));}catch(Exception ignored){} }
    public boolean isDungeonWorld(World world){ return world != null && (world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("world","dungeons")) || world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("editor-world","dungeons_editor")) || world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("spawn.world","dungeons_spawn")) || isDungeonInstanceWorld(world)); }
    private boolean isDungeonSpawnWorld(World world){ return world != null && world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("spawn.world","dungeons_spawn")); }
    private boolean isSpawnWorld(World world){ return world != null && world.getName().equalsIgnoreCase(configs.get("core/config.yml").getString("spawn.world","spawn")); }
    private boolean isProtectedDungeonWorld(World world) { return protects(null) && isDungeonWorld(world) && !isDungeonEditorWorld(world); }
    private boolean protects(String key) {
        YamlConfiguration yaml = configs.get("dungeons/dungeons.yml");
        if (!yaml.getBoolean("dungeon-protection.enabled", true)) return false;
        return key == null || yaml.getBoolean("dungeon-protection." + key, true);
    }
    private boolean canBypassDungeonProtection(Player player) {
        if (player == null) return false;
        if (isDungeonInstanceWorld(player.getWorld()) && !configs.get("dungeons/dungeons.yml").getBoolean("dungeon-protection.allow-bypass-in-runs", false)) return false;
        return player.hasPermission("3smpcore.dungeon.protection.bypass");
    }
    private boolean isDungeonInventoryRestricted(Player player) {
        return player != null && isInDungeonApi(player) && isDungeonWorld(player.getWorld()) && !isDungeonEditorWorld(player.getWorld());
    }
    private boolean shouldCancelDungeonInventoryClick(Player player, InventoryClickEvent event) {
        if (!isDungeonInventoryRestricted(player) || canBypassDungeonProtection(player)) return false;
        InventoryAction action = event.getAction();
        if (action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT || event.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
            return !configs.get("dungeons/dungeons.yml").getBoolean("inventory.allow-drop", false);
        }
        if (action == InventoryAction.HOTBAR_SWAP && !configs.get("dungeons/dungeons.yml").getBoolean("inventory.allow-hotbar-swap", true)) return true;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.WINDOW_BORDER_LEFT || event.getClick() == org.bukkit.event.inventory.ClickType.WINDOW_BORDER_RIGHT) return true;
        if (!configs.get("dungeons/dungeons.yml").getBoolean("inventory.allow-move", true)) return true;
        if (!configs.get("dungeons/dungeons.yml").getBoolean("inventory.block-external-inventories", true)) return false;
        boolean topClicked = event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory());
        boolean shiftToExternal = action == InventoryAction.MOVE_TO_OTHER_INVENTORY && event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory()) && event.getView().getTopInventory().getHolder() != player;
        return topClicked || shiftToExternal;
    }
    private boolean isAllowedDungeonInteraction(Material material) {
        if (material == null) return false;
        List<String> allowed = configs.get("dungeons/dungeons.yml").getStringList("dungeon-protection.allowed-interactions");
        if (allowed.isEmpty()) allowed = List.of("CHEST", "BARREL", "LEVER", "STONE_BUTTON");
        String name = material.name();
        return allowed.stream().anyMatch(entry -> entry.equalsIgnoreCase(name));
    }
    private void saveCurrentInventoryForCurrentWorld(Player player) {
        if (isDungeonWorld(player.getWorld()) && !isDungeonEditorWorld(player.getWorld())) {
            saveDungeonProfile(player);
            return;
        }
        if (survivalService != null) survivalService.saveCurrentProfile(player);
    }
    private void saveDungeonProfile(Player player){ repository.saveInventoryProfile(player.getUniqueId(), "dungeon", player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand()); }
    private void loadDungeonProfile(Player player){ var data = repository.loadInventoryProfile(player.getUniqueId(), "dungeon"); clearPlayerState(player); player.getInventory().setContents(data.contents()); player.getInventory().setArmorContents(data.armor()); player.getInventory().setItemInOffHand(data.offhand() == null ? new ItemStack(Material.AIR) : data.offhand()); player.updateInventory(); }
    private void loadDungeonProfileWithStarterKit(Player player) {
        loadDungeonProfile(player);
        if (hasReceivedDungeonStarterKit(player.getUniqueId())) return;
        if (isLiveInventoryEmpty(player)) {
            debug(player, "<gray>First dungeon spawn visit detected; applying one-time starter kit.</gray>");
            applyBaseKit(player);
            saveDungeonProfile(player);
        }
        markDungeonStarterKit(player.getUniqueId());
    }
    private boolean hasReceivedDungeonStarterKit(UUID uuid) {
        return configs.get("dungeons/rooms.yml").getBoolean("starter-kit." + uuid, false);
    }
    private void markDungeonStarterKit(UUID uuid) {
        var yaml = configs.get("dungeons/rooms.yml");
        yaml.set("starter-kit." + uuid, true);
        try { yaml.save(new File(plugin.getDataFolder(), "dungeons/rooms.yml")); } catch (Exception ignored) {}
    }
    private boolean isEmptyDungeonProfile(UUID uuid) { return isEmptyProfile(repository.loadInventoryProfile(uuid, "dungeon")); }
    private boolean isEmptyProfile(PlayerDataRepository.InventoryProfile data) {
        if (data == null) return true;
        boolean contentsEmpty = Arrays.stream(data.contents()).allMatch(this::isAir);
        boolean armorEmpty = Arrays.stream(data.armor()).allMatch(this::isAir);
        boolean offhandEmpty = isAir(data.offhand());
        return contentsEmpty && armorEmpty && offhandEmpty;
    }
    private boolean isLiveInventoryEmpty(Player player) {
        boolean contentsEmpty = Arrays.stream(player.getInventory().getContents()).allMatch(this::isAir);
        boolean armorEmpty = Arrays.stream(player.getInventory().getArmorContents()).allMatch(this::isAir);
        return contentsEmpty && armorEmpty && isAir(player.getInventory().getItemInOffHand());
    }
    private boolean isAir(ItemStack item) { return item == null || item.getType().isAir(); }
    private void stripSpawnTools(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;
            if (isItem(item) || isDevTool(item) || isDevWand(item) || isDevMarker(item)) player.getInventory().setItem(i, null);
        }
        clearCursorToAir(player);
        player.updateInventory();
    }
    private void playBackSound(Player player){ player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f); }
    private void clearCursorToAir(Player player){ player.setItemOnCursor(new ItemStack(Material.AIR)); player.updateInventory(); }
    private void applyDungeonSpawnBuffs(Player player){
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, org.bukkit.potion.PotionEffect.INFINITE_DURATION, 1, true, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SATURATION, org.bukkit.potion.PotionEffect.INFINITE_DURATION, 0, true, false, false));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }
    private void clearDungeonSpawnBuffs(Player player){
        org.bukkit.potion.PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null && speed.getDuration() == org.bukkit.potion.PotionEffect.INFINITE_DURATION && speed.getAmplifier() == 1) player.removePotionEffect(PotionEffectType.SPEED);
        org.bukkit.potion.PotionEffect saturation = player.getPotionEffect(PotionEffectType.SATURATION);
        if (saturation != null && saturation.getDuration() == org.bukkit.potion.PotionEffect.INFINITE_DURATION) player.removePotionEffect(PotionEffectType.SATURATION);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }
    private void clearDungeonVision(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }
    private void clearSpawnTools(Player player) {
        clearItem(player);
        player.setItemOnCursor(new ItemStack(Material.AIR));
        player.updateInventory();
    }
    private void clearDungeonHubItems(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isTaggedItem(item, COSMETICS_ITEM_ID) || isTaggedItem(item, DUEL_QUEUE_ITEM_ID) || isTaggedItem(item, PARTY_HUB_ITEM_ID) || isTaggedItem(item, PARTY_CREATE_ITEM_ID) || isTaggedItem(item, PARTY_DISBAND_ITEM_ID)) {
                player.getInventory().setItem(i, null);
            }
        }
        clearCursorToAir(player);
        player.updateInventory();
    }
    private boolean isTaggedItem(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String value = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "3smpcore_cosmetics_item"), PersistentDataType.STRING);
        if (value != null && id.equals(value)) return true;
        value = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "3smpcore_duel_item"), PersistentDataType.STRING);
        if (value != null && id.equals(value)) return true;
        value = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "3smpcore_party_item"), PersistentDataType.STRING);
        return value != null && id.equals(value);
    }
    private void toggleEditorTool(Player player){ if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; } if (!isDungeonEditorWorld(player.getWorld())) { Text.send(player, "<red>You can only toggle the dungeon editor tool in the dungeon editor world.</red>"); return; } if (isDevTool(player.getInventory().getItem(DEV_TOOL_SLOT))) { player.getInventory().setItem(DEV_TOOL_SLOT, null); clearCursorToAir(player); Text.send(player, "<yellow>Dungeon editor tool hidden.</yellow>"); } else { giveDevTool(player); } player.updateInventory(); }
    private void giveMarker(Player player, Material material){ var marker = devMarker(material, material.name()); var leftover = player.getInventory().addItem(marker); leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item)); enforceEditorToolbar(player); }
    private boolean roomIsConnectable(YamlConfiguration yaml, String id){ List<SavedMarker> markers = yaml.getStringList("templates." + id + ".precise-markers").stream().map(this::parsePreciseMarker).filter(Objects::nonNull).toList(); if (markers.isEmpty()) return false; String role = yaml.getString("templates." + id + ".role", "normal"); boolean entrance = markers.stream().anyMatch(v -> v.id().equalsIgnoreCase("entrance") || v.id().equalsIgnoreCase("player_spawn")); boolean connector = markers.stream().anyMatch(v -> v.id().equalsIgnoreCase("connector") || v.id().equalsIgnoreCase("up") || v.id().equalsIgnoreCase("down")); boolean boss = markers.stream().anyMatch(v -> v.id().equalsIgnoreCase("boss")); boolean exit = markers.stream().anyMatch(v -> v.id().equalsIgnoreCase("exit")); if (role.equalsIgnoreCase("entrance")) return entrance || connector; if (role.equalsIgnoreCase("boss")) return boss || connector; if (role.equalsIgnoreCase("exit")) return exit || connector; return connector || entrance || boss || exit; }
    private boolean roomsConnect(String previousId, String currentId){
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        boolean previousOk = roomIsConnectable(yaml, previousId);
        boolean currentOk = roomIsConnectable(yaml, currentId);
        boolean allowed = previousOk && currentOk;
        if (debugEnabled()) {
            Bukkit.getLogger().info("[3SMPCore] Dungeon link check previous=" + previousId + " current=" + currentId + " previousOk=" + previousOk + " currentOk=" + currentOk + " allowed=" + allowed);
        }
        return allowed;
    }
    private ItemStack connectionPreviewItem(Player player){
        List<Component> lore = new ArrayList<>();
        World world = player.getWorld();
        boolean editor = isDungeonEditorWorld(world);
        lore.add(Text.mm(editor ? "<green>Editor world active.</green>" : "<red>Not in editor world.</red>"));
        lore.add(Text.mm(debugEnabled() ? "<green>Dungeon debug: enabled</green>" : "<gray>Dungeon debug: disabled</gray>"));
        RoomBox selected = selectedBounds(player);
        if (selected != null) lore.add(Text.mm("<gray>Wand size:</gray> <white>" + (selected.maxX() - selected.minX() + 1) + "x" + (selected.maxY() - selected.minY() + 1) + "x" + (selected.maxZ() - selected.minZ() + 1) + "</white>"));
        else lore.add(Text.mm("<yellow>Select pos1 and pos2 with the wand.</yellow>"));
        List<Entity> entrance = nearbyMarkers(player, "entrance");
        List<Entity> connector = nearbyMarkers(player, "connector");
        List<Entity> exit = nearbyMarkers(player, "exit");
        List<Entity> boss = nearbyMarkers(player, "boss");
        List<Entity> playerSpawn = nearbyMarkers(player, "player_spawn");
        int traps = nearbyMarkers(player, "trap_boulder").size() + nearbyMarkers(player, "trap_spike").size() + nearbyMarkers(player, "trap_bridge").size();
        boolean connectable = !entrance.isEmpty() || !connector.isEmpty() || !exit.isEmpty() || !boss.isEmpty() || !playerSpawn.isEmpty();
        lore.add(Text.mm(connectable ? "<green>Connection markers detected.</green>" : "<red>No connection markers detected.</red>"));
        lore.add(Text.mm("<gray>Detected room type:</gray> <white>" + roomTypeName(entrance, connector, exit, boss, playerSpawn) + "</white>"));
        lore.add(Text.mm("<gray>Entrance:</gray> <white>" + entrance.size() + "</white> <gray>Connector:</gray> <white>" + connector.size() + "</white> <gray>Exit:</gray> <white>" + exit.size() + "</white> <gray>Boss:</gray> <white>" + boss.size() + "</white> <gray>Player spawn:</gray> <white>" + playerSpawn.size() + "</white>"));
        lore.add(Text.mm("<gray>Traps:</gray> <white>" + traps + "</white> <dark_gray>(boulder/spike/bridge)</dark_gray>"));
        lore.add(Text.mm("<gray>Link mode:</gray> <white>Jigsaw face-to-face</white>"));
        ItemStack stack = new ItemStack(editor ? Material.LIGHT_BLUE_CONCRETE : Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm("<gradient:#4c1d95:#a78bfa>Connection Preview</gradient>"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
    private void debug(Player player, String message) {
        if (!debugEnabled()) return;
        if (player != null) Text.send(player, "<dark_gray>[<gradient:#4c1d95:#a78bfa>Dungeon Debug</gradient>]</dark_gray> " + message);
        Bukkit.getLogger().info("[3SMPCore] DungeonDebug " + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Text.mm(message)));
    }
    private List<Entity> nearbyMarkers(Player player, String markerId) {
        Location center = player.getLocation();
        return player.getWorld().getNearbyEntities(center, 96.0D, 96.0D, 96.0D).stream().filter(entity -> markerId.equalsIgnoreCase(armorStandMarker(entity))).toList();
    }
    private String roomTypeName(List<?> entrance, List<?> connector, List<?> exit, List<?> boss, List<?> playerSpawn) {
        if (!playerSpawn.isEmpty()) return "entrance";
        if (!boss.isEmpty()) return "boss";
        if (!exit.isEmpty()) return "exit";
        if (!entrance.isEmpty()) return "entrance";
        if (!connector.isEmpty()) return "connector";
        return "normal";
    }
    private boolean debugEnabled() { return configs.get("dungeons/dungeons.yml").getBoolean(DEBUG_PATH, true); }
    private void handleDungeonRoomProgress(Player player, Location to) {
        net.dark.threecore.dungeons.runtime.DungeonSession session = getDungeonSession(player);
        if (session != null && session.layout() != null) {
            for (var room : session.layout().rooms()) {
                if (!room.box().contains(to.toVector())) continue;
                if (session.currentRoom() == null || session.currentRoom().graphIndex() != room.graphIndex()) {
                    session.currentRoom(room);
                    Bukkit.getPluginManager().callEvent(new net.dark.threecore.dungeons.event.DungeonRoomEnterEvent(session, player, room));
                    spawnGeneratedRoomContent(session, player, room);
                }
                break;
            }
        }
        ActiveDungeonRun run = activeRuns.get(player.getUniqueId());
        if (run == null) return;
        List<PlacedRoom> rooms = activeLayouts.get(player.getUniqueId());
        if (rooms == null) return;
        for (PlacedRoom room : rooms) {
            if (!room.contains(to)) continue;
            if (room.role().equalsIgnoreCase("boss") && room.entered().add(player.getUniqueId())) spawnBossForRoom(player, room);
            if (room.isInsideMarkerRegion(to, "exit")) {
                if (!run.bossDefeated()) {
                    Text.actionBar(player, "<red>Beat the boss first.</red>");
                    return;
                }
                leave(player);
                Text.send(player, "<green>Dungeon clear.</green>");
                return;
            }
        }
    }

    private boolean canUseFastStructurePaste(String id) {
        String mode = rotationOriginMode(id);
        return mode.equals("ROOM_MIN");
    }

    private boolean pasteStructure(String id, RoomReservation room) {
        YamlConfiguration dungeonConfig = configs.get("dungeons/dungeons.yml");
        YamlConfiguration templates = configs.get("dungeons/templates.yml");
        if (!dungeonConfig.getBoolean("generation.room-engine.paste.apply-physics", false)
            && !templates.getStringList("templates." + id + ".blocks").isEmpty()) {
            return false;
        }
        String relative = configs.get("dungeons/templates.yml").getString("templates." + id + ".structure", "");
        if (relative == null || relative.isBlank()) return false;
        File file = new File(plugin.getDataFolder(), "dungeons/" + relative.replace('\\', '/').replaceFirst("^dungeons/", ""));
        if (!file.exists()) return false;
        try {
            Structure structure = Bukkit.getStructureManager().loadStructure(file);
            World world = Bukkit.getWorld(room.world());
            if (world == null) return false;
            RoomSize pasteSize = rotatedSize(id, room.rotation());
            forceLoadTemplateArea(world, room, pasteSize.x(), pasteSize.z());
            structure.place(new Location(world, room.centerX(), room.y(), room.centerZ()), true, effectiveStructureRotation(id, room.rotation()), Mirror.NONE, 0, 1.0F, new Random());
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[3SMPCore] Failed to paste dungeon structure " + file.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private void forceLoadTemplateArea(World world, RoomReservation room, int sizeX, int sizeZ) {
        if (world == null || room == null) return;
        if (!configs.get("dungeons/dungeons.yml").getBoolean("generation.force-load-chunks-before-paste", true)) return;
        int minChunkX = Math.floorDiv(room.centerX() - 2, 16);
        int maxChunkX = Math.floorDiv(room.centerX() + Math.max(1, sizeX) + 2, 16);
        int minChunkZ = Math.floorDiv(room.centerZ() - 2, 16);
        int maxChunkZ = Math.floorDiv(room.centerZ() + Math.max(1, sizeZ) + 2, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ).load(true);
            }
        }
    }

    private void stripRuntimeMarkers(World world, RoomReservation room, int sizeX, int sizeY, int sizeZ) {
        int maxX = Math.max(1, sizeX);
        int maxY = Math.max(1, sizeY);
        int maxZ = Math.max(1, sizeZ);
        for (Entity entity : roomEntities(world, room, maxX, maxY, maxZ)) {
            if (entity instanceof Player) continue;
            Location loc = entity.getLocation();
            if (loc.getBlockX() >= room.centerX() && loc.getBlockX() < room.centerX() + maxX && loc.getBlockY() >= room.y() && loc.getBlockY() < room.y() + maxY && loc.getBlockZ() >= room.centerZ() && loc.getBlockZ() < room.centerZ() + maxZ && armorStandMarker(entity) != null) {
                entity.remove();
            }
        }
        for (int x = 0; x < maxX; x++) {
            for (int y = 0; y < maxY; y++) {
                for (int z = 0; z < maxZ; z++) {
                    Block block = world.getBlockAt(room.centerX() + x, room.y() + y, room.centerZ() + z);
                    if (isShulkerMarkerBlock(block.getType()) || marker(block.getType()) != null) block.setType(Material.AIR, false);
                }
            }
        }
    }

    private File templateStructureFile(String safeId) {
        File dir = new File(plugin.getDataFolder(), "dungeons/templates");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, safeId + ".nbt");
    }

    private void spawnDungeonStartContent(net.dark.threecore.dungeons.runtime.DungeonSession session) {
        if (session == null || session.layout() == null) return;
        int trapCount = session.layout().rooms().stream().mapToInt(room -> room.definition().traps().size()).sum();
        debug(null, "<gray>Generated dungeon content:</gray> <white>" + session.layout().rooms().size() + "</white> <gray>rooms,</gray> <white>" + trapCount + "</white> <gray>trap marker spawn(s).</gray>");
        for (PlacedDungeonRoom room : session.layout().rooms()) {
            if (!room.definition().traps().isEmpty()) {
                debug(null, "<gray>Room</gray> <white>" + room.definition().id() + "</white> <gray>has</gray> <white>" + room.definition().traps().size() + "</white> <gray>trap(s).</gray>");
            }
            for (DungeonTrapDefinition trap : room.definition().traps()) {
                spawnGeneratedTrap(session, room, trap);
            }
            spawnGeneratedMobs(session, room, MobTrigger.ON_DUNGEON_START, null);
        }
        if (session.layout().startRoom() != null) {
            spawnedRuntimeRooms.computeIfAbsent(session.id(), ignored -> new HashSet<>()).add(session.layout().startRoom().graphIndex());
            spawnGeneratedMobs(session, session.layout().startRoom(), MobTrigger.ON_ROOM_ENTER, null);
        }
    }

    private void spawnGeneratedRoomContent(net.dark.threecore.dungeons.runtime.DungeonSession session, Player player, PlacedDungeonRoom room) {
        if (session == null || room == null) return;
        if (spawnedRuntimeRooms.computeIfAbsent(session.id(), ignored -> new HashSet<>()).add(room.graphIndex())) {
            spawnGeneratedMobs(session, room, MobTrigger.ON_ROOM_ENTER, player);
        }
        if (room.definition().type() == RoomType.BOSS && spawnedRuntimeBossRooms.computeIfAbsent(session.id(), ignored -> new HashSet<>()).add(room.graphIndex())) {
            int before = spawnedRuntimeEntities.getOrDefault(session.id(), List.of()).size();
            spawnGeneratedMobs(session, room, MobTrigger.ON_BOSS_START, player);
            int after = spawnedRuntimeEntities.getOrDefault(session.id(), List.of()).size();
            if (after == before) spawnConfiguredBossFallback(session, room);
            if (player != null) Text.send(player, "<gradient:#7f1d1d:#ef4444>Boss room awakened.</gradient>");
        }
    }

    private void openRuntimeBossDoors(net.dark.threecore.dungeons.runtime.DungeonSession session) {
        if (session == null || session.layout() == null) return;
        int opened = 0;
        for (PlacedDungeonRoom room : session.layout().rooms()) {
            if (room.definition().type() != RoomType.BOSS) continue;
            opened += openBossDoor(room);
        }
        debug(null, "<green>Boss door opening complete.</green> <gray>Blocks opened:</gray> <white>" + opened + "</white>");
    }

    private int openBossDoor(PlacedDungeonRoom room) {
        YamlConfiguration rooms = configs.get("dungeons/rooms.yml");
        String base = "rooms." + room.definition().id() + ".boss-door";
        if (!rooms.getBoolean(base + ".enabled", false)) return 0;
        Vector a = listVector(rooms.getList(base + ".pos1", List.of()));
        Vector b = listVector(rooms.getList(base + ".pos2", List.of()));
        Vector wa = room.transform().localToWorld(a);
        Vector wb = room.transform().localToWorld(b);
        World world = Bukkit.getWorld(room.world());
        if (world == null) return 0;
        int minX = Math.min(wa.getBlockX(), wb.getBlockX());
        int minY = Math.min(wa.getBlockY(), wb.getBlockY());
        int minZ = Math.min(wa.getBlockZ(), wb.getBlockZ());
        int maxX = Math.max(wa.getBlockX(), wb.getBlockX());
        int maxY = Math.max(wa.getBlockY(), wb.getBlockY());
        int maxZ = Math.max(wa.getBlockZ(), wb.getBlockZ());
        int opened = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        block.setType(Material.AIR, false);
                        opened++;
                    }
                }
            }
        }
        return opened;
    }

    private void spawnGeneratedMobs(net.dark.threecore.dungeons.runtime.DungeonSession session, PlacedDungeonRoom room, MobTrigger trigger, Player player) {
        if (session == null || room == null || trigger == null) return;
        for (var spawn : room.definition().spawns()) {
            if (spawn.trigger() != trigger) continue;
            Location location = generatedLocalLocation(room, spawn.localPosition(), BlockFace.SOUTH);
            if (location == null) continue;
            String mobId = trigger == MobTrigger.ON_BOSS_START ? bossMobId(session.dungeonId(), session.difficulty(), spawn.id()) : spawn.id();
            for (int i = 0; i < spawn.amount(); i++) {
                openDungeonMobSpawnWindow(location.getWorld());
                Entity entity = mythicMobsHook.spawnMob(mobId.contains(":") ? mobId : mobId + ":" + Math.max(1, spawn.level()), location);
                if (entity == null) entity = location.getWorld().spawnEntity(location, spawn.fallback(), CreatureSpawnEvent.SpawnReason.CUSTOM);
                entity.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_MOB_KEY), PersistentDataType.BYTE, (byte) 1);
                if (trigger == MobTrigger.ON_BOSS_START) entity.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_BOSS_KEY), PersistentDataType.BYTE, (byte) 1);
                trackRuntimeEntity(session.id(), entity);
            }
        }
    }

    private void spawnConfiguredBossFallback(net.dark.threecore.dungeons.runtime.DungeonSession session, PlacedDungeonRoom room) {
        Location location = generatedLocalLocation(room, room.definition().facingMarker() == null ? new Vector(room.definition().size().getX() / 2.0D, 1.0D, room.definition().size().getZ() / 2.0D) : room.definition().facingMarker().localPosition(), BlockFace.SOUTH);
        if (location == null) return;
        String mobId = bossMobId(session.dungeonId(), session.difficulty(), configs.get("dungeons/dungeons.yml").getString("boss.mythicmob", "DungeonBoss"));
        openDungeonMobSpawnWindow(location.getWorld());
        Entity entity = mythicMobsHook.spawnMob(mobId, location);
        if (entity == null) entity = location.getWorld().spawnEntity(location, EntityType.WARDEN, CreatureSpawnEvent.SpawnReason.CUSTOM);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_MOB_KEY), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_BOSS_KEY), PersistentDataType.BYTE, (byte) 1);
        trackRuntimeEntity(session.id(), entity);
    }

    private void spawnGeneratedTrap(net.dark.threecore.dungeons.runtime.DungeonSession session, PlacedDungeonRoom room, DungeonTrapDefinition trap) {
        Location location = generatedLocalLocation(room, trap.localPosition(), trap.facing());
        if (location == null) {
            debug(null, "<red>Trap spawn skipped:</red> <white>" + trap.id() + "</white> <gray>room=" + room.definition().id() + " reason=no location</gray>");
            return;
        }
        String mobId = resolvedTrapMobId(trap.type(), trap.mythicMobId());
        openDungeonMobSpawnWindow(location.getWorld());
        Entity entity = mythicMobsHook.spawnMob(mobId, location);
        if (entity == null && !mobId.contains(":")) entity = mythicMobsHook.spawnMob(mobId + ":1", location);
        if (entity == null) {
            debug(null, "<yellow>Trap Mythic spawn failed; using hidden fallback marker.</yellow> <gray>trap=" + trap.id() + " type=" + trap.type() + " mob=" + mobId + " world=" + location.getWorld().getName() + " xyz=" + formatLocation(location) + "</gray>");
            entity = location.getWorld().spawn(location, ArmorStand.class, stand -> {
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setMarker(true);
                stand.setVisible(false);
                stand.setCustomNameVisible(false);
                stand.customName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>" + trap.type() + " trap</gradient>"));
            });
        } else {
            debug(null, "<green>Trap spawned.</green> <gray>trap=" + trap.id() + " type=" + trap.type() + " mob=" + mobId + " room=" + room.definition().id() + " xyz=" + formatLocation(location) + "</gray>");
        }
        entity.teleport(location);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_TRAP_KEY), PersistentDataType.STRING, trap.type());
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_MOB_KEY), PersistentDataType.BYTE, (byte) 1);
        trackRuntimeEntity(session.id(), entity);
    }

    private String formatLocation(Location location) {
        if (location == null) return "null";
        return round(location.getX()) + "," + round(location.getY()) + "," + round(location.getZ());
    }

    private Location generatedLocalLocation(PlacedDungeonRoom room, Vector local, BlockFace localFacing) {
        if (room == null || local == null) return null;
        World world = Bukkit.getWorld(room.world());
        if (world == null) return null;
        Vector transformed = room.transform().localPointToWorld(local);
        BlockFace facing = room.transform().localFacingToWorld(localFacing == null ? BlockFace.SOUTH : localFacing);
        return new Location(world, transformed.getX(), transformed.getY(), transformed.getZ(), yawFromBlockFace(facing), 0.0F);
    }

    private float yawFromBlockFace(BlockFace facing) {
        return switch (facing == null ? BlockFace.SOUTH : facing) {
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    private String bossMobId(String dungeonId, String difficulty, String fallback) {
        String level = dungeonId == null || dungeonId.isBlank() ? "jungle" : dungeonId.toLowerCase(Locale.ROOT);
        String diff = difficulty == null || difficulty.isBlank() ? "normal" : difficulty.toLowerCase(Locale.ROOT);
        String configured = configs.get("dungeons/dungeons.yml").getString("boss.mythicmobs." + level + "." + diff, "");
        if (configured != null && !configured.isBlank()) return resolveBossMobId(configured);
        configured = configs.get("dungeons/dungeons.yml").getString("boss.mythicmobs.default." + diff, "");
        if (configured != null && !configured.isBlank()) return resolveBossMobId(configured);
        return resolveBossMobId(fallback == null || fallback.isBlank() ? configs.get("dungeons/dungeons.yml").getString("boss.mythicmob", "Dungeon_Panda") : fallback);
    }

    private String resolveBossMobId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return bossDefaultMobId();
        int colon = value.indexOf(':');
        String mob = colon >= 0 ? value.substring(0, colon) : value;
        String suffix = colon >= 0 ? value.substring(colon) : "";
        String alias = configs.get("dungeons/dungeons.yml").getString("boss.legacy-aliases." + mob, "");
        if (alias != null && !alias.isBlank()) return alias.trim() + suffix;
        if (isLegacyBossMobId(mob)) return bossDefaultMobId() + suffix;
        return value;
    }

    private String bossDefaultMobId() {
        String configured = configs.get("dungeons/dungeons.yml").getString("boss.mythicmob", "Dungeon_Panda");
        if (configured == null || configured.isBlank() || isLegacyBossMobId(configured)) return "Dungeon_Panda";
        return configured.trim();
    }

    private boolean isLegacyBossMobId(String mobId) {
        if (mobId == null || mobId.isBlank()) return true;
        String normalized = mobId.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("dungeonboss")
            || normalized.equals("jungleboss")
            || normalized.equals("junglebosseasy")
            || normalized.equals("junglebossnormal")
            || normalized.equals("junglebossmedium")
            || normalized.equals("junglebosshard")
            || normalized.equals("junglebossnightmare");
    }

    private void trackRuntimeEntity(UUID sessionId, Entity entity) {
        if (sessionId == null || entity == null) return;
        spawnedRuntimeEntities.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(entity.getUniqueId());
    }

    private void cleanupRuntimeEntities(UUID sessionId) {
        List<UUID> ids = spawnedRuntimeEntities.remove(sessionId);
        if (ids == null) return;
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && !(entity instanceof Player)) entity.remove();
        }
    }

    private void spawnBossForRoom(Player player, PlacedRoom room) {
        List<SavedMarker> spawners = room.markers().stream().filter(marker -> marker.id().equalsIgnoreCase("boss_spawner")).toList();
        if (spawners.isEmpty()) return;
        ActiveDungeonRun run = activeRuns.get(player.getUniqueId());
        String mythicMob = bossMobId(run == null ? room.room().level() : run.level(), run == null ? "normal" : run.difficulty(), configs.get("dungeons/dungeons.yml").getString("boss.mythicmob", "DungeonBoss"));
        String commandTemplate = configs.get("dungeons/dungeons.yml").getString("boss.spawn-command", "mm mobs spawn %mob% 1 %world%,%x%,%y%,%z%");
        for (SavedMarker marker : spawners) {
            Location spawn = marker.world(room);
            boolean usedMythic = mythicMobsHook.isEnabled();
            if (usedMythic) {
                String command = commandTemplate.replace("%mob%", mythicMob).replace("%world%", spawn.getWorld().getName()).replace("%x%", String.valueOf(spawn.getBlockX())).replace("%y%", String.valueOf(spawn.getBlockY())).replace("%z%", String.valueOf(spawn.getBlockZ()));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                for (Entity nearby : spawn.getWorld().getNearbyEntities(spawn, 4.0D, 4.0D, 4.0D)) {
                    if (!(nearby instanceof LivingEntity) || nearby instanceof Player) continue;
                    nearby.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_BOSS_KEY), PersistentDataType.BYTE, (byte) 1);
                }
            } else {
                LivingEntity fallback = (LivingEntity) spawn.getWorld().spawnEntity(spawn, EntityType.ZOMBIE, CreatureSpawnEvent.SpawnReason.CUSTOM);
                fallback.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_BOSS_KEY), PersistentDataType.BYTE, (byte) 1);
                fallback.customName(Text.mm("<red>Dungeon Boss</red>"));
            }
        }
        Text.send(player, "<dark_red>Boss room awakened.</dark_red>");
    }
    private List<PlacedRoom> buildPlacedRooms(List<String> plan, List<RoomReservation> placed) {
        List<PlacedRoom> out = new ArrayList<>();
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        for (RoomReservation room : placed) {
            String id = templateIdFromReservation(room.key());
            if (id == null || !yaml.isConfigurationSection("templates." + id)) continue;
            String base = "templates." + id;
            List<SavedMarker> markers = new ArrayList<>();
            RoomSize templateSize = templateSize(id);
            for (String raw : yaml.getStringList(base + ".markers")) {
                SavedMarker parsed = parseMarker(raw);
                if (parsed != null) markers.add(transformMarker(normalizeConnectorFacing(parsed, templateSize), templateSize, effectiveStructureRotation(id, room.rotation())));
            }
            for (String raw : yaml.getStringList(base + ".precise-markers")) {
                SavedMarker parsed = parsePreciseMarker(raw);
                if (parsed != null) markers.add(transformMarker(normalizeConnectorFacing(parsed, templateSize), templateSize, effectiveStructureRotation(id, room.rotation())));
            }
            RoomSize size = rotatedSize(id, room.rotation());
            out.add(new PlacedRoom(room, id, yaml.getString(base + ".role", "normal"), size.x(), size.y(), size.z(), markers, new HashSet<>()));
        }
        return out;
    }
    private RoomReservation smartRoomPlacement(String key, RoomReservation previousRoom, String previousId, String nextId, List<RoomReservation> placed) {
        RoomReservation connected = nextConnectedRoom(key, previousRoom, previousId, nextId, placed);
        if (connected != null) return connected;
        for (int i = placed.size() - 1; i >= 0; i--) {
            RoomReservation branch = placed.get(i);
            String branchId = templateIdFromReservation(branch.key());
            if (branchId == null || branchId.equals(previousId)) continue;
            connected = nextConnectedRoom(key, branch, branchId, nextId, placed);
            if (connected != null) {
                debug(null, "<gray>Branched room</gray> <white>" + nextId + "</white> <gray>from</gray> <white>" + branchId + "</white> <gray>to keep the run full.</gray>");
                return connected;
            }
        }
        return null;
    }

    private RoomReservation nextConnectedRoom(String key, RoomReservation previousRoom, String previousId, String nextId, List<RoomReservation> placed) {
        List<ConnectionPair> pairs = connectionPairs(previousId, previousRoom.rotation(), nextId);
        if (pairs.isEmpty()) {
            debug(null, "<yellow>No compatible connector pairs between</yellow> <white>" + previousId + "</white> <yellow>and</yellow> <white>" + nextId + "</white><yellow>. Need entrance/connector markers facing each other, or up/down markers.</yellow>");
            return null;
        }
        for (ConnectionPair pair : pairs) {
            RoomReservation candidate = connectedRoomCandidate(key, previousRoom, previousId, nextId, pair);
            if (!overlapsAny(candidate, nextId, placed)) return candidate;
        }
        debug(null, "<yellow>All connector pairs overlapped for</yellow> <white>" + nextId + "</white><yellow>. Trying edge-anchored placement fallback.</yellow>");
        for (ConnectionPair pair : pairs) {
            RoomReservation candidate = edgeAnchoredRoomCandidate(key, previousRoom, previousId, nextId, pair);
            if (!overlapsAny(candidate, nextId, placed)) return candidate;
        }
        for (ConnectionPair pair : pairs) {
            RoomReservation candidate = spacedRoomCandidate(key, previousRoom, previousId, nextId, pair, placed);
            if (candidate != null) {
                debug(null, "<yellow>Used spaced fallback for</yellow> <white>" + nextId + "</white><yellow> so the dungeon path stays complete.</yellow>");
                return candidate;
            }
        }
        return null;
    }

    private RoomReservation connectedRoomCandidate(String key, RoomReservation previousRoom, String previousId, String nextId, ConnectionPair pair) {
        SavedMarker previous = transformMarker(pair.previous(), templateSize(previousId), effectiveStructureRotation(previousId, previousRoom.rotation()));
        SavedMarker next = transformMarker(pair.next(), templateSize(nextId), effectiveStructureRotation(nextId, pair.nextRotation()));
        int x = previousRoom.centerX() + previous.x() + pair.dx() - next.x();
        int z = previousRoom.centerZ() + previous.z() + pair.dz() - next.z();
        int y = isVerticalMarker(previous) || isVerticalMarker(next)
            ? previousRoom.y() + previous.y() + pair.dy() - next.y()
            : previousRoom.y();
        return new RoomReservation(key, previousRoom.world(), previousRoom.level(), x, y, z, pair.nextRotation());
    }

    private RoomReservation spacedRoomCandidate(String key, RoomReservation previousRoom, String previousId, String nextId, ConnectionPair pair, List<RoomReservation> placed) {
        SavedMarker previous = transformMarker(pair.previous(), templateSize(previousId), effectiveStructureRotation(previousId, previousRoom.rotation()));
        RoomSize previousSize = rotatedSize(previousId, previousRoom.rotation());
        RoomSize nextSize = rotatedSize(nextId, pair.nextRotation());
        int baseY = isVerticalMarker(previous) ? previousRoom.y() + pair.dy() * Math.max(previousSize.y(), nextSize.y()) : previousRoom.y();
        int gap = Math.max(4, configs.get("dungeons/dungeons.yml").getInt("generation.room-engine.spaced-fallback-gap", 6));
        int stepX = previousSize.x() + nextSize.x() + gap;
        int stepZ = previousSize.z() + nextSize.z() + gap;
        for (int distance = 1; distance <= 16; distance++) {
            int x = previousRoom.centerX();
            int z = previousRoom.centerZ();
            switch (previous.facing().toUpperCase(Locale.ROOT)) {
                case "EAST" -> x += stepX * distance;
                case "WEST" -> x -= stepX * distance;
                case "SOUTH" -> z += stepZ * distance;
                case "NORTH" -> z -= stepZ * distance;
                default -> {
                    x += stepX * distance;
                    z += stepZ * distance;
                }
            }
            RoomReservation candidate = new RoomReservation(key, previousRoom.world(), previousRoom.level(), x, baseY, z, pair.nextRotation());
            if (!overlapsAny(candidate, nextId, placed)) return candidate;
        }
        return null;
    }

    private RoomReservation edgeAnchoredRoomCandidate(String key, RoomReservation previousRoom, String previousId, String nextId, ConnectionPair pair) {
        SavedMarker previous = transformMarker(pair.previous(), templateSize(previousId), effectiveStructureRotation(previousId, previousRoom.rotation()));
        SavedMarker next = transformMarker(pair.next(), templateSize(nextId), effectiveStructureRotation(nextId, pair.nextRotation()));
        RoomSize previousSize = rotatedSize(previousId, previousRoom.rotation());
        RoomSize nextSize = rotatedSize(nextId, pair.nextRotation());
        int x = previousRoom.centerX() + previous.x() + pair.dx() - next.x();
        int z = previousRoom.centerZ() + previous.z() + pair.dz() - next.z();
        if (!isVerticalMarker(previous) && !isVerticalMarker(next)) {
            switch (previous.facing().toUpperCase(Locale.ROOT)) {
                case "EAST" -> x = previousRoom.centerX() + previousSize.x();
                case "WEST" -> x = previousRoom.centerX() - nextSize.x();
                case "SOUTH" -> z = previousRoom.centerZ() + previousSize.z();
                case "NORTH" -> z = previousRoom.centerZ() - nextSize.z();
                default -> { }
            }
        }
        int y = isVerticalMarker(previous) || isVerticalMarker(next)
            ? previousRoom.y() + previous.y() + pair.dy() - next.y()
            : previousRoom.y();
        return new RoomReservation(key, previousRoom.world(), previousRoom.level(), x, y, z, pair.nextRotation());
    }
    private int verticalDelta(String previous, String next) {
        if ("up".equalsIgnoreCase(previous) && "down".equalsIgnoreCase(next)) return 1;
        if ("down".equalsIgnoreCase(previous) && "up".equalsIgnoreCase(next)) return -1;
        return 0;
    }
    private int connectionDx(SavedMarker marker) {
        if (isVerticalMarker(marker)) return 0;
        return switch (marker.facing().toUpperCase(Locale.ROOT)) {
            case "EAST" -> 1;
            case "WEST" -> -1;
            default -> 0;
        };
    }
    private int connectionDz(SavedMarker marker) {
        if (isVerticalMarker(marker)) return 0;
        return switch (marker.facing().toUpperCase(Locale.ROOT)) {
            case "SOUTH" -> 1;
            case "NORTH" -> -1;
            default -> 0;
        };
    }
    private List<String> expectedMarkersFor(String previousType) {
        if ("up".equalsIgnoreCase(previousType)) return List.of("down");
        if ("down".equalsIgnoreCase(previousType)) return List.of("up");
        return List.of("connector", "entrance", "boss", "exit", "up", "down");
    }
    private List<ConnectionPair> connectionPairs(String previousId, StructureRotation previousRotation, String nextId) {
        List<SavedMarker> previousMarkers = markersFor(previousId).stream().filter(this::isConnectionMarker).toList();
        List<SavedMarker> nextMarkers = markersFor(nextId).stream().filter(this::isConnectionMarker).toList();
        List<ConnectionPair> vertical = new ArrayList<>();
        List<ConnectionPair> facingMatched = new ArrayList<>();
        for (SavedMarker previous : previousMarkers) {
            SavedMarker rotatedPrevious = transformMarker(previous, templateSize(previousId), effectiveStructureRotation(previousId, previousRotation));
            for (SavedMarker next : nextMarkers) {
                for (StructureRotation rotation : rotations()) {
                    SavedMarker rotatedNext = transformMarker(next, templateSize(nextId), effectiveStructureRotation(nextId, rotation));
                    if ("up".equalsIgnoreCase(rotatedPrevious.id()) && "down".equalsIgnoreCase(rotatedNext.id())) {
                        vertical.add(new ConnectionPair(previous, next, rotation, 0, 1, 0));
                    } else if ("down".equalsIgnoreCase(rotatedPrevious.id()) && "up".equalsIgnoreCase(rotatedNext.id())) {
                        vertical.add(new ConnectionPair(previous, next, rotation, 0, -1, 0));
                    } else if (horizontalConnectorsMatch(rotatedPrevious, rotatedNext)) {
                        DirectionOffset offset = facingOffsetIfCompatible(rotatedPrevious, rotatedNext);
                        if (offset != null) facingMatched.add(new ConnectionPair(previous, next, rotation, offset.dx(), 0, offset.dz()));
                    }
                }
            }
        }
        List<ConnectionPair> pool = new ArrayList<>();
        if (configs.get("dungeons/dungeons.yml").getBoolean("generation.room-engine.allow-vertical", false) && !vertical.isEmpty()) pool.addAll(vertical);
        if (!facingMatched.isEmpty()) pool.addAll(facingMatched);
        Collections.shuffle(pool);
        return pool;
    }
    private boolean horizontalConnectorsMatch(SavedMarker previous, SavedMarker next) {
        if (isVerticalMarker(previous) || isVerticalMarker(next)) return false;
        if (!facesEachOther(previous, next)) return false;
        boolean previousDoor = previous.id().equalsIgnoreCase("entrance") || previous.id().equalsIgnoreCase("connector");
        boolean nextDoor = next.id().equalsIgnoreCase("entrance") || next.id().equalsIgnoreCase("connector");
        return previousDoor && nextDoor;
    }
    private DirectionOffset facingOffsetIfCompatible(SavedMarker previous, SavedMarker next) {
        if (!facesEachOther(previous, next)) return null;
        return switch (previous.facing().toUpperCase(Locale.ROOT)) {
            case "EAST" -> new DirectionOffset(1, 0);
            case "WEST" -> new DirectionOffset(-1, 0);
            case "SOUTH" -> new DirectionOffset(0, 1);
            case "NORTH" -> new DirectionOffset(0, -1);
            default -> null;
        };
    }
    private List<StructureRotation> rotations() {
        return List.of(StructureRotation.NONE, StructureRotation.CLOCKWISE_90, StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90);
    }
    private StructureRotation parseStructureRotation(String raw) {
        String value = raw == null ? "0" : raw.toUpperCase(Locale.ROOT).replace("ROT_", "").replace("DEG", "");
        return switch (value) {
            case "90", "CLOCKWISE_90", "CW90" -> StructureRotation.CLOCKWISE_90;
            case "180", "CLOCKWISE_180", "CW180" -> StructureRotation.CLOCKWISE_180;
            case "270", "COUNTERCLOCKWISE_90", "CCW90" -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }
    private StructureRotation effectiveStructureRotation(String templateId, StructureRotation requested) {
        return addRotation(baseFacingCorrection(templateId), requested == null ? StructureRotation.NONE : requested);
    }
    private StructureRotation baseFacingCorrection(String templateId) {
        String facing = configs.get("dungeons/templates.yml").getString("templates." + templateId + ".base-facing", "SOUTH").toUpperCase(Locale.ROOT);
        return switch (facing) {
            case "NORTH" -> StructureRotation.CLOCKWISE_180;
            case "EAST" -> StructureRotation.CLOCKWISE_90;
            case "WEST" -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }
    private StructureRotation addRotation(StructureRotation first, StructureRotation second) {
        return switch ((quarterTurns(first) + quarterTurns(second)) & 3) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }
    private int quarterTurns(StructureRotation rotation) {
        return switch (rotation == null ? StructureRotation.NONE : rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
    }
    private RoomSize templateSize(String templateId) {
        return new RoomSize(templateSizeX(templateId), templateSizeY(templateId), templateSizeZ(templateId));
    }
    private SavedMarker transformMarker(SavedMarker marker, RoomSize size, StructureRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> marker.rotated(size.z() - marker.offsetZ(), marker.offsetY(), marker.offsetX(), size.z() - 1 - marker.z(), marker.y(), marker.x(), rotateFacing(marker.facing(), 1), 90.0F);
            case CLOCKWISE_180 -> marker.rotated(size.x() - marker.offsetX(), marker.offsetY(), size.z() - marker.offsetZ(), size.x() - 1 - marker.x(), marker.y(), size.z() - 1 - marker.z(), rotateFacing(marker.facing(), 2), 180.0F);
            case COUNTERCLOCKWISE_90 -> marker.rotated(marker.offsetZ(), marker.offsetY(), size.x() - marker.offsetX(), marker.z(), marker.y(), size.x() - 1 - marker.x(), rotateFacing(marker.facing(), 3), -90.0F);
            default -> marker;
        };
    }
    private SavedMarker normalizeConnectorFacing(SavedMarker marker, RoomSize size) {
        if (!marker.id().equalsIgnoreCase("entrance") && !marker.id().equalsIgnoreCase("connector")) return marker;
        int west = marker.x();
        int east = Math.max(0, size.x() - 1 - marker.x());
        int north = marker.z();
        int south = Math.max(0, size.z() - 1 - marker.z());
        int closest = Math.min(Math.min(west, east), Math.min(north, south));
        String facing = closest == west ? "WEST" : closest == east ? "EAST" : closest == north ? "NORTH" : "SOUTH";
        return marker.withFacing(facing);
    }
    private String rotateFacing(String facing, int quarterTurns) {
        List<String> order = List.of("NORTH", "EAST", "SOUTH", "WEST");
        int index = order.indexOf(facing.toUpperCase(Locale.ROOT));
        if (index < 0) return facing;
        return order.get((index + quarterTurns) % order.size());
    }
    private boolean facesEachOther(SavedMarker previous, SavedMarker next) {
        String previousFacing = previous.facing().toUpperCase(Locale.ROOT);
        String nextFacing = next.facing().toUpperCase(Locale.ROOT);
        return switch (previousFacing) {
            case "NORTH" -> nextFacing.equals("SOUTH");
            case "SOUTH" -> nextFacing.equals("NORTH");
            case "EAST" -> nextFacing.equals("WEST");
            case "WEST" -> nextFacing.equals("EAST");
            default -> false;
        };
    }
    private boolean isConnectionMarker(SavedMarker marker) {
        return List.of("connector", "entrance", "up", "down").stream().anyMatch(value -> value.equalsIgnoreCase(marker.id()));
    }
    private boolean isVerticalMarker(SavedMarker marker) {
        return "up".equalsIgnoreCase(marker.id()) || "down".equalsIgnoreCase(marker.id());
    }
    private SavedMarker pickConnectionMarker(String templateId, List<String> allowedTypes) {
        List<SavedMarker> candidates = markersFor(templateId).stream().filter(marker -> allowedTypes.stream().anyMatch(type -> type.equalsIgnoreCase(marker.id()))).toList();
        if (candidates.isEmpty()) return null;
        return candidates.get(new Random(Objects.hash(templateId, allowedTypes, System.nanoTime())).nextInt(candidates.size()));
    }
    private List<SavedMarker> markersFor(String templateId) {
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        List<SavedMarker> out = new ArrayList<>();
        for (String raw : yaml.getStringList("templates." + templateId + ".precise-markers")) {
            SavedMarker marker = parsePreciseMarker(raw);
            if (marker != null) out.add(normalizeConnectorFacing(marker, templateSize(templateId)));
        }
        return out;
    }
    private boolean overlapsAny(RoomReservation candidate, String templateId, List<RoomReservation> placed) {
        RoomBox candidateBox = box(candidate, templateId);
        for (RoomReservation existing : placed) {
            String existingTemplate = templateIdFromReservation(existing.key());
            if (existingTemplate == null) continue;
            if (candidateBox.intersects(box(existing, existingTemplate))) return true;
        }
        return false;
    }
    private String templateIdFromReservation(String key) {
        int index = key.lastIndexOf(":");
        if (index < 0) return null;
        return key.substring(index + 1);
    }
    private RoomBox box(RoomReservation room, String templateId) {
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        RoomSize size = rotatedSize(templateId, room.rotation());
        int sx = size.x();
        int sy = size.y();
        int sz = size.z();
        return new RoomBox(room.centerX(), room.y(), room.centerZ(), room.centerX() + sx - 1, room.y() + sy - 1, room.centerZ() + sz - 1);
    }
    private int templateSizeX(String templateId) { return Math.max(1, configs.get("dungeons/templates.yml").getInt("templates." + templateId + ".size.x", MAX_SIZE)); }
    private int templateSizeY(String templateId) { return Math.max(1, configs.get("dungeons/templates.yml").getInt("templates." + templateId + ".size.y", MAX_SIZE)); }
    private int templateSizeZ(String templateId) { return Math.max(1, configs.get("dungeons/templates.yml").getInt("templates." + templateId + ".size.z", MAX_SIZE)); }
    private RoomSize rotatedSize(String templateId, StructureRotation rotation) {
        RoomSize size = templateSize(templateId);
        StructureRotation effective = effectiveStructureRotation(templateId, rotation);
        if (effective == StructureRotation.CLOCKWISE_90 || effective == StructureRotation.COUNTERCLOCKWISE_90) return new RoomSize(size.z(), size.y(), size.x());
        return size;
    }
    private LocalBlockPos transformTemplateBlock(String templateId, int x, int y, int z, StructureRotation rotation) {
        RoomSize size = templateSize(templateId);
        if (rotationOriginMode(templateId).equals("ROOM_MIN")) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new LocalBlockPos(size.z() - 1 - z, y, x);
                case CLOCKWISE_180 -> new LocalBlockPos(size.x() - 1 - x, y, size.z() - 1 - z);
                case COUNTERCLOCKWISE_90 -> new LocalBlockPos(z, y, size.x() - 1 - x);
                default -> new LocalBlockPos(x, y, z);
            };
        }
        org.bukkit.util.Vector pivot = templateRotationPivot(templateId);
        org.bukkit.util.Vector min = templateRotatedMin(size, pivot, rotation);
        org.bukkit.util.Vector rotated = rotateTemplatePoint(new org.bukkit.util.Vector(x, y, z), pivot, rotation).subtract(min);
        return new LocalBlockPos((int) Math.round(rotated.getX()), y, (int) Math.round(rotated.getZ()));
    }
    private org.bukkit.util.Vector templateRotationPivot(String templateId) {
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        String base = "templates." + templateId + ".rotation-origin";
        String mode = rotationOriginMode(templateId);
        if (mode.equals("ROOM_CENTER")) {
            RoomSize size = templateSize(templateId);
            return new org.bukkit.util.Vector(size.x() / 2.0D, 0.0D, size.z() / 2.0D);
        }
        if (mode.equals("MARKER")) {
            var marker = yaml.getConfigurationSection("templates." + templateId + ".facing-marker");
            if (marker != null) {
                List<Integer> pos = marker.getIntegerList("pos");
                if (pos.size() >= 3) return new org.bukkit.util.Vector(pos.get(0), pos.get(1), pos.get(2));
            }
        }
        List<Integer> pos = yaml.getIntegerList(base + ".pos");
        return pos.size() >= 3 ? new org.bukkit.util.Vector(pos.get(0), pos.get(1), pos.get(2)) : new org.bukkit.util.Vector();
    }
    private String rotationOriginMode(String templateId) {
        return configs.get("dungeons/templates.yml").getString("templates." + templateId + ".rotation-origin.mode", "ROOM_MIN").toUpperCase(Locale.ROOT).replace('-', '_');
    }
    private org.bukkit.util.Vector templateRotatedMin(RoomSize size, org.bukkit.util.Vector pivot, StructureRotation rotation) {
        int maxX = Math.max(0, size.x() - 1);
        int maxZ = Math.max(0, size.z() - 1);
        List<org.bukkit.util.Vector> corners = List.of(new org.bukkit.util.Vector(0, 0, 0), new org.bukkit.util.Vector(maxX, 0, 0), new org.bukkit.util.Vector(0, 0, maxZ), new org.bukkit.util.Vector(maxX, 0, maxZ));
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        for (org.bukkit.util.Vector corner : corners) {
            org.bukkit.util.Vector rotated = rotateTemplatePoint(corner, pivot, rotation);
            minX = Math.min(minX, rotated.getX());
            minZ = Math.min(minZ, rotated.getZ());
        }
        return new org.bukkit.util.Vector(minX, 0, minZ);
    }
    private org.bukkit.util.Vector rotateTemplatePoint(org.bukkit.util.Vector point, org.bukkit.util.Vector pivot, StructureRotation rotation) {
        double dx = point.getX() - pivot.getX();
        double dz = point.getZ() - pivot.getZ();
        return switch (rotation) {
            case CLOCKWISE_90 -> new org.bukkit.util.Vector(pivot.getX() - dz, point.getY(), pivot.getZ() + dx);
            case CLOCKWISE_180 -> new org.bukkit.util.Vector(pivot.getX() - dx, point.getY(), pivot.getZ() - dz);
            case COUNTERCLOCKWISE_90 -> new org.bukkit.util.Vector(pivot.getX() + dz, point.getY(), pivot.getZ() - dx);
            default -> point.clone();
        };
    }
    private void rotateBlockData(BlockData data, StructureRotation rotation) {
        if (data == null || rotation == StructureRotation.NONE) return;
        try {
            data.rotate(rotation);
        } catch (Throwable ignored) {
        }
    }
    private SavedMarker parseMarker(String raw) {
        String[] parts = raw.split(":");
        if (parts.length < 2) return null;
        String[] xyz = parts[0].split(",");
        if (xyz.length != 3) return null;
        try {
            return new SavedMarker(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]), parts[1], parts.length >= 3 ? parts[2] : "UP");
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    private SavedMarker parsePreciseMarker(String raw) {
        String[] parts = raw.split("\\|");
        if (parts.length < 8) return null;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            return new SavedMarker((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), parts[3], parts[4], x, y, z, Float.parseFloat(parts[5]), Float.parseFloat(parts[6]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    private String serializePreciseMarker(SavedMarker marker) {
        return round(marker.offsetX()) + "|" + round(marker.offsetY()) + "|" + round(marker.offsetZ()) + "|" + marker.id() + "|" + marker.facing() + "|" + marker.yaw() + "|" + marker.pitch() + "|armor_stand";
    }
    private String armorStandMarker(Entity entity) {
        if (!(entity instanceof ArmorStand)) return null;
        String roomMarker = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, ROOM_MARKER_KEY), PersistentDataType.STRING);
        if (roomMarker != null && !roomMarker.isBlank()) return roomMarker;
        String tagged = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, "dungeon_editor_marker"), PersistentDataType.STRING);
        if (tagged != null && !tagged.isBlank()) return tagged;
        if (entity.customName() == null) return null;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(entity.customName()).toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        if (name.contains("player_spawn") || name.equals("spawn")) return "player_spawn";
        if (name.contains("entrance")) return "entrance";
        if (name.contains("connector")) return "connector";
        if (name.equals("up") || name.contains("vertical_up")) return "up";
        if (name.equals("down") || name.contains("vertical_down")) return "down";
        if (name.contains("enemy_spawn")) return "enemy_spawn";
        if (name.contains("room_facing") || name.contains("base_facing") || name.contains("base_rotation")) return "room_facing";
        if (name.equals("exit") || name.contains("dungeon_exit")) return "exit";
        if (name.contains("boss_spawner") || name.contains("boss_spawn")) return "boss_spawner";
        if (name.equals("boss") || name.contains("boss_marker")) return "boss";
        if (name.contains("trap_boulder") || name.contains("boulder_trap")) return "trap_boulder";
        if (name.contains("trap_spike") || name.contains("spike_trap")) return "trap_spike";
        if (name.contains("trap_bridge") || name.contains("bridge_trap")) return "trap_bridge";
        return null;
    }
    private String detectBaseFacing(List<String> preciseMarkers) {
        for (String raw : preciseMarkers) {
            SavedMarker marker = parsePreciseMarker(raw);
            if (marker != null && marker.id().equalsIgnoreCase("room_facing")) {
                return marker.facing().toUpperCase(Locale.ROOT);
            }
        }
        return "SOUTH";
    }
    private String facingFromYaw(float yaw) {
        float wrapped = ((yaw % 360.0F) + 360.0F) % 360.0F;
        if (wrapped >= 45.0F && wrapped < 135.0F) return "WEST";
        if (wrapped >= 135.0F && wrapped < 225.0F) return "NORTH";
        if (wrapped >= 225.0F && wrapped < 315.0F) return "EAST";
        return "SOUTH";
    }
    private String markerFacing(String markerId, Location location, int minX, int maxX, int minZ, int maxZ) {
        if ("up".equalsIgnoreCase(markerId)) return "UP";
        if ("down".equalsIgnoreCase(markerId)) return "DOWN";
        return facingFromYaw(location.getYaw());
    }
    private float yawFromFacing(String facing, float fallback) {
        return yawFromFacingStatic(facing, fallback);
    }
    private static float yawFromFacingStatic(String facing, float fallback) {
        return switch (facing.toUpperCase(Locale.ROOT)) {
            case "NORTH" -> 180.0F;
            case "EAST" -> -90.0F;
            case "WEST" -> 90.0F;
            case "SOUTH" -> 0.0F;
            default -> fallback;
        };
    }
    private float snapYaw(float yaw) {
        return switch (facingFromYaw(yaw)) {
            case "NORTH" -> 180.0F;
            case "EAST" -> -90.0F;
            case "WEST" -> 90.0F;
            default -> 0.0F;
        };
    }
    private double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
    private String serializeEntity(Entity entity, int minX, int minY, int minZ) {
        Location loc = entity.getLocation();
        String name = entity.customName() == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(entity.customName());
        return entity.getType().name() + "|" + (loc.getX() - minX) + "|" + (loc.getY() - minY) + "|" + (loc.getZ() - minZ) + "|" + loc.getYaw() + "|" + loc.getPitch() + "|" + name;
    }
    private void spawnSerializedEntity(World world, RoomReservation room, String raw) {
        String[] parts = raw.split("\\|", 7);
        if (parts.length < 6) return;
        try {
            EntityType type = EntityType.valueOf(parts[0]);
            Location loc = new Location(world, room.centerX() + Double.parseDouble(parts[1]), room.y() + Double.parseDouble(parts[2]), room.centerZ() + Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
            Entity entity = world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, DUNGEON_MOB_KEY), PersistentDataType.BYTE, (byte) 1);
            if (parts.length >= 7 && !parts[6].isBlank()) entity.customName(Component.text(parts[6]));
        } catch (Exception ignored) {
        }
    }
    private record Marker(String id){}
    private record RoomReservation(String key,String world,String level,int centerX,int y,int centerZ,StructureRotation rotation){}
    private record ActiveDungeonRun(String level, String difficulty, boolean bossDefeated, int kills){}
    private record ConnectionPair(SavedMarker previous, SavedMarker next, StructureRotation nextRotation, int dx, int dy, int dz){}
    private record DirectionOffset(int dx, int dz){}
    private record RoomSize(int x, int y, int z){}
    private record LocalBlockPos(int x, int y, int z){}
    private record EditorConnector(String id, String role, String type, SavedMarker marker, String verticalDirection, int targetYOffset, String snapMode, int anchorX, int anchorY, int anchorZ){}
    private record RoomBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private boolean intersects(RoomBox other) {
            return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
        }
    }
    private static final class SavedMarker {
        private final int x;
        private final int y;
        private final int z;
        private final String id;
        private final String facing;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final float yaw;
        private final float pitch;

        private SavedMarker(int x, int y, int z, String id, String facing) {
            this(x, y, z, id, facing, x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
        }

        private SavedMarker(int x, int y, int z, String id, String facing, double offsetX, double offsetY, double offsetZ, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.facing = facing == null ? "UP" : facing;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        private int x() { return x; }
        private int y() { return y; }
        private int z() { return z; }
        private String id() { return id; }
        private String facing() { return facing; }
        private double offsetX() { return offsetX; }
        private double offsetY() { return offsetY; }
        private double offsetZ() { return offsetZ; }
        private float yaw() { return yaw; }
        private float pitch() { return pitch; }

        private SavedMarker rotated(double offsetX, double offsetY, double offsetZ, int x, int y, int z, String facing, float yawOffset) {
            return new SavedMarker(x, y, z, id, facing, offsetX, offsetY, offsetZ, yaw + yawOffset, pitch);
        }

        private SavedMarker withFacing(String facing) {
            return new SavedMarker(x, y, z, id, facing, offsetX, offsetY, offsetZ, yawFromFacingStatic(facing, yaw), pitch);
        }

        private Location world(RoomReservation room) { return new Location(Bukkit.getWorld(room.world()), room.centerX() + offsetX, room.y() + offsetY, room.centerZ() + offsetZ, yaw, pitch); }
        private Location world(PlacedRoom room) { return new Location(Bukkit.getWorld(room.room().world()), room.room().centerX() + offsetX, room.room().y() + offsetY, room.room().centerZ() + offsetZ, yaw, pitch); }
    }
    private record PlacedRoom(RoomReservation room, String templateId, String role, int sizeX, int sizeY, int sizeZ, List<SavedMarker> markers, Set<UUID> entered) {
        private boolean contains(Location location) {
            if (location == null || location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(room.world())) return false;
            return location.getBlockX() >= room.centerX() && location.getBlockX() < room.centerX() + sizeX && location.getBlockY() >= room.y() && location.getBlockY() < room.y() + sizeY && location.getBlockZ() >= room.centerZ() && location.getBlockZ() < room.centerZ() + sizeZ;
        }
        private boolean isNearMarker(Location location, String markerId) {
            for (SavedMarker marker : markers) if (marker.id().equalsIgnoreCase(markerId) && marker.world(this).distanceSquared(location) <= 4.0D) return true;
            return false;
        }
        private boolean isInsideMarkerRegion(Location location, String markerId) {
            List<SavedMarker> selected = markers.stream().filter(marker -> marker.id().equalsIgnoreCase(markerId)).toList();
            if (selected.size() < 2) return isNearMarker(location, markerId);
            int minX = selected.stream().mapToInt(SavedMarker::x).min().orElse(0) + room.centerX();
            int maxX = selected.stream().mapToInt(SavedMarker::x).max().orElse(0) + room.centerX();
            int minY = selected.stream().mapToInt(SavedMarker::y).min().orElse(0) + room.y();
            int maxY = selected.stream().mapToInt(SavedMarker::y).max().orElse(0) + room.y();
            int minZ = selected.stream().mapToInt(SavedMarker::z).min().orElse(0) + room.centerZ();
            int maxZ = selected.stream().mapToInt(SavedMarker::z).max().orElse(0) + room.centerZ();
            int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY + 2 && z >= minZ && z <= maxZ;
        }
    }
    private static final class DungeonRunOptions { private String level; private final boolean[] party; private final String[] difficulty; private DungeonRunOptions(String level, boolean[] party, String[] difficulty){this.level=level;this.party=party;this.difficulty=difficulty;} private String level(){return level;} private void level(String level){this.level=level;} private boolean[] party(){return party;} private String[] difficulty(){return difficulty;} }
    private static final class BoulderEditSession {
        private final String roomId;
        private final String trapId;
        private final RoomBox bounds;
        private final List<Vector> path = new ArrayList<>();
        private Vector spawn;
        private Vector triggerMin;
        private Vector triggerMax;
        private boolean triggerFirstSet;
        private float yaw;
        private BoulderEditSession(String roomId, String trapId, RoomBox bounds){this.roomId=roomId.toLowerCase(Locale.ROOT);this.trapId=trapId.toLowerCase(Locale.ROOT);this.bounds=bounds;}
        private String roomId(){return roomId;}
        private String trapId(){return trapId;}
        private RoomBox bounds(){return bounds;}
        private List<Vector> path(){return path;}
        private Vector spawn(){return spawn;}
        private void spawn(Vector spawn){this.spawn=spawn;}
        private Vector triggerMin(){return triggerMin;}
        private void triggerMin(Vector triggerMin){this.triggerMin=triggerMin;}
        private Vector triggerMax(){return triggerMax;}
        private void triggerMax(Vector triggerMax){this.triggerMax=triggerMax;}
        private boolean triggerFirstSet(){return triggerFirstSet;}
        private void triggerFirstSet(boolean triggerFirstSet){this.triggerFirstSet=triggerFirstSet;}
        private float yaw(){return yaw;}
        private void yaw(float yaw){this.yaw=yaw;}
    }
    private record DungeonHolder(String context) implements InventoryHolder { @Override public Inventory getInventory(){ return null; } }
    private static final class VoidChunkGenerator extends ChunkGenerator {}
}










