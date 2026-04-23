package net.dark.threecore.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class DungeonManager {
    private static final List<String> TEMPLATE_TYPES = List.of("entrence", "room", "exit", "boss");

    private final Dunguons3SMP plugin;
    private final DungeonItems items;
    private final File progressFile;
    private final YamlConfiguration progress;
    private final Map<UUID, Integer> unlocked = new HashMap<>();
    private final Map<UUID, DungeonSession> sessions = new HashMap<>();
    private final Map<UUID, DungeonMenu.MenuState> states = new HashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, PreviewState> previews = new HashMap<>();
    private final Map<UUID, DungeonFlow> flows = new HashMap<>();
    private long tickCounter;

    public DungeonManager(Dunguons3SMP plugin) {
        this.plugin = plugin;
        this.items = new DungeonItems(plugin);
        this.progressFile = new File(plugin.getDataFolder(), "dungeons/progress.yml");
        this.progress = YamlConfiguration.loadConfiguration(progressFile);
        ensureTemplateFolders();
    }

    public DungeonItems items() {
        return items;
    }

    public void loadAll() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        ensureTemplateFolders();
        loadProgress();
    }

    public DungeonMenu.MenuState menuState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new DungeonMenu.MenuState());
    }

    public int roomsForDifficulty(DungeonDifficulty difficulty) {
        return plugin.getConfig().getInt("dungeon.difficulty-room-count." + difficulty.name(), difficulty.roomCount);
    }

    public boolean hasActiveDungeon(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean shouldProtectDungeonBlocks(Player player) {
        return plugin.getConfig().getBoolean("dungeon.protect-blocks", true) && hasActiveDungeon(player);
    }

    public int unlockedLevelValue(Player player) {
        return unlockedLevel(player);
    }

    public void giveMenuItem(Player player) {
        items.giveMenuItem(player);
    }

    public void openMenu(Player player) {
        player.openInventory(new DungeonMenu(this).build(player));
    }

    public void openLevelMenu(Player player) {
        player.openInventory(new DungeonMenu(this).buildLevels(player));
    }

    public void handleMenuClick(Player player, int slot, String title) {
        DungeonMenu.MenuState state = menuState(player);
        if (DungeonMenu.TITLE.equals(title)) {
            if (slot == 10) {
                state.solo = !state.solo;
            } else if (slot == 11) {
                openLevelMenu(player);
                return;
            } else if (slot == 12) {
                state.difficulty = state.difficulty.next();
            } else if (slot == 16) {
                startDungeon(player, state);
                return;
            }
            openMenu(player);
            return;
        }

        if (DungeonMenu.LEVELS_TITLE.equals(title)) {
            int selectedLevel = switch (slot) {
                case 10 -> 1;
                case 11 -> 2;
                case 12 -> 3;
                case 13 -> 4;
                case 14 -> 5;
                default -> -1;
            };
            if (slot == 16) {
                openMenu(player);
                return;
            }
            if (selectedLevel != -1) {
                if (selectedLevel > unlockedLevel(player)) {
                    player.sendMessage(ChatColor.RED + "That dungeon level is locked.");
                } else {
                    state.level = selectedLevel;
                }
            }
            openLevelMenu(player);
        }
    }

    public void startDungeon(Player player, DungeonMenu.MenuState state) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You already have an active dungeon.");
            return;
        }

        DungeonLevel level = DungeonLevel.byId(state.level);
        if (level.id > unlockedLevel(player)) {
            player.sendMessage(ChatColor.RED + "That level is locked.");
            return;
        }
        if (level.underDev && !plugin.getConfig().getBoolean("dungeon.dev-mode", false)) {
            player.sendMessage(ChatColor.RED + level.displayName + " is coming soon.");
            return;
        }

        List<TemplateMeta> templates = loadTemplates(level.id);
        if (templates.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No structure templates found for level " + level.id + ".");
            return;
        }

        Location returnLocation = player.getLocation().clone();
        Location origin = randomOrigin(player.getWorld());
        DungeonSession session = new DungeonSession(player.getUniqueId(), level, state.difficulty, player.getWorld(), origin);
        session.returnLocation = returnLocation;
        session.roomCount = roomsForDifficulty(state.difficulty);
        session.starterRadius = plugin.getConfig().getDouble("dungeon.starter-radius", 8.0D);
        session.freezeUntilTick = tickCounter + (plugin.getConfig().getInt("dungeon.starter-wait-seconds", 5) * 20L);

        boolean generated = initializeDungeon(session, templates, session.roomCount);
        if (!generated || session.rooms.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Dungeon generation failed. Check that the level has entrence, room, and exit structures saved.");
            return;
        }

        sessions.put(player.getUniqueId(), session);
        if (!session.spawns.isEmpty()) {
            Location spawn = session.spawns.get(0).clone().add(
                plugin.getConfig().getDouble("dungeon.spawn-offset-x", 0.5D),
                plugin.getConfig().getDouble("dungeon.spawn-offset-y", 1.0D),
                plugin.getConfig().getDouble("dungeon.spawn-offset-z", 0.5D)
            );
            session.starterCenter = spawn;
        } else {
            session.starterCenter = origin.clone().add(0.5D, 1.0D, 0.5D);
            player.sendMessage(ChatColor.RED + "No spawn marker found in the entrance room.");
        }
        player.teleport(session.starterCenter.clone());

        setupScoreboard(player, session);
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "Dungeon started: " + level.displayName + ChatColor.GRAY + " / " + state.difficulty.name());
        player.sendMessage(ChatColor.YELLOW + "Stand on the starter marker for " + plugin.getConfig().getInt("dungeon.starter-wait-seconds", 5) + " seconds.");
    }

    public void setRoomPos1(Player player, Location loc) {
        pos1.put(player.getUniqueId(), targetBlock(loc));
    }

    public void setRoomPos2(Player player, Location loc) {
        pos2.put(player.getUniqueId(), targetBlock(loc));
    }

    public void previewSelection(Player player) {
        SelectionPreview preview = buildSelectionPreview(player);
        if (preview == null) {
            return;
        }
        previews.put(player.getUniqueId(), preview.previewState);
        player.sendMessage(ChatColor.AQUA + "Previewing room markers for 10 seconds.");
        player.sendMessage(ChatColor.GRAY + preview.summary);
    }

    public void inspectSelection(Player player) {
        SelectionPreview preview = buildSelectionPreview(player);
        if (preview == null) {
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Room inspect: " + preview.summary);
        player.sendMessage(ChatColor.GRAY + "Sides: " + String.join(", ", preview.sides));
        player.sendMessage(ChatColor.GRAY + "Openings: " + preview.openings.size() + " | Rotation: " + preview.rotation);
        for (String opening : preview.openings) {
            player.sendMessage(ChatColor.DARK_AQUA + " - " + opening);
        }
        player.sendMessage(ChatColor.GRAY + "Boss room: " + (preview.bossRoom ? "yes" : "no") + " | Entrance: " + (preview.entranceRoom ? "yes" : "no"));
    }

    private SelectionPreview buildSelectionPreview(Player player) {
        Location first = pos1.get(player.getUniqueId());
        Location second = pos2.get(player.getUniqueId());
        if (first == null || second == null) {
            player.sendMessage(ChatColor.RED + "Set pos1 and pos2 first.");
            return null;
        }
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            player.sendMessage(ChatColor.RED + "Both points must be in the same world.");
            return null;
        }
        Location min = min(first, second);
        Location max = max(first, second);
        PreviewState preview = buildPreview(min, max);
        if (!preview.bounds.isEmpty()) {
            BoundingBox box = boundsFrom(preview.bounds);
            min = box.min;
            max = box.max;
            preview = buildPreview(min, max);
        }
        List<String> sides = summarizeSides(preview.doors);
        boolean bossRoom = inferBossRoom(preview);
        boolean entranceRoom = inferEntranceRoom(preview);
        String rotation = preview.doors.isEmpty() ? "none" : primaryFacing(preview.doors.get(0).facing()).name();
        List<String> openings = describeOpenings(preview.doors);
        String summary = "Spawn: " + preview.spawns.size() + " | Links: " + preview.doors.size() + " | Enemy: " + preview.enemies.size() + " | Exit: " + preview.exits.size() + " | Trigger: " + preview.triggers.size();
        return new SelectionPreview(preview, summary, sides, openings, rotation, bossRoom, entranceRoom);
    }

    public void saveRoomTemplate(Player player, int level, String name) {
        saveRoomTemplate(player, level, inferType(name), name);
    }

    public void saveRoomTemplate(Player player, int level, String type, String name) {
        Location first = pos1.get(player.getUniqueId());
        Location second = pos2.get(player.getUniqueId());
        if (first == null || second == null) {
            player.sendMessage(ChatColor.RED + "Set pos1 and pos2 first.");
            return;
        }
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            player.sendMessage(ChatColor.RED + "Both points must be in the same world.");
            return;
        }

        String normalizedType = normalizeType(type);
        String safeName = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        File levelDir = new File(new File(templateRoot(), "level" + level), normalizedType);
        if (!levelDir.exists() && !levelDir.mkdirs()) {
            player.sendMessage(ChatColor.RED + "Could not create template folder.");
            return;
        }

        Location min = min(first, second);
        Location max = max(first, second);
        PreviewState preview = buildPreview(min, max);
        if (!preview.bounds.isEmpty()) {
            BoundingBox box = boundsFrom(preview.bounds);
            min = box.min;
            max = box.max;
            preview = buildPreview(min, max);
        }

        File nbtFile = new File(levelDir, safeName + ".nbt");
        File metaFile = new File(levelDir, safeName + ".yml");
        StructureManager structureManager = Bukkit.getStructureManager();
        Structure structure = structureManager.createStructure();
        structure.fill(min, max, true);
        structure.getPersistentDataContainer().set(plugin.key("room_type"), PersistentDataType.STRING, normalizedType);
        structure.getPersistentDataContainer().set(plugin.key("level"), PersistentDataType.INTEGER, level);

        YamlConfiguration meta = new YamlConfiguration();
        meta.set("name", safeName);
        meta.set("type", normalizedType);
        meta.set("level", level);
        meta.set("structure", nbtFile.getName());
        meta.set("width", max.getBlockX() - min.getBlockX() + 1);
        meta.set("height", max.getBlockY() - min.getBlockY() + 1);
        meta.set("length", max.getBlockZ() - min.getBlockZ() + 1);
        writeMarkers(meta, "markers.spawn", preview.spawns, min);
        writeMarkers(meta, "markers.trigger", preview.triggers, min);
        writeMarkers(meta, "markers.exit", preview.exits, min);
        writeMarkers(meta, "markers.enemy", preview.enemies, min);
        writeDoorMarkers(meta, preview.doors);

        try {
            structureManager.saveStructure(nbtFile, structure);
            meta.save(metaFile);
            player.sendMessage(ChatColor.GREEN + "Saved " + normalizedType + " structure to level " + level + ": " + safeName + ".nbt");
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Failed to save structure: " + e.getMessage());
        }
    }

    public void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        DungeonSession session = sessions.get(player.getUniqueId());
        if (session == null || session.starterCenter == null) {
            return;
        }
        if (plugin.getConfig().getBoolean("dungeon.dev-mode", false)) {
            return;
        }
        if (session.isFrozen(tickCounter)) {
            Location anchor = session.starterCenter.clone();
            anchor.setYaw(player.getLocation().getYaw());
            anchor.setPitch(player.getLocation().getPitch());
            if (event.getTo() != null && event.getFrom().distanceSquared(event.getTo()) > 0.0001D) {
                event.setTo(anchor);
            }
            return;
        }
        if (!session.started) {
            session.started = true;
            player.sendMessage(ChatColor.GREEN + "Dungeon run started.");
        }
    }

    public void tick() {
        tickCounter++;
        tickPreviews();

        for (Player player : Bukkit.getOnlinePlayers()) {
            DungeonSession session = sessions.get(player.getUniqueId());
            if (session == null) {
                continue;
            }

            updateRooms(player, session);
            updateScoreboard(player, session);

            if (!session.completed && atUnlockedExit(player, session)) {
                completeDungeon(player, session);
                continue;
            }

            for (Location trigger : session.triggers) {
                boolean inside = player.getLocation().distanceSquared(trigger.clone().add(0.5D, 0.5D, 0.5D)) <= 4.0D;
                String key = player.getUniqueId() + ":" + trigger.getBlockX() + ":" + trigger.getBlockY() + ":" + trigger.getBlockZ();
                if (inside && session.insideTriggers.add(key)) {
                    runConfiguredCommand(player, "enter");
                }
                if (!inside && session.insideTriggers.remove(key)) {
                    runConfiguredCommand(player, "exit");
                }
            }
        }
    }

    public void shutdown() {
        saveProgress();
        sessions.clear();
        previews.clear();
    }
    private void tickPreviews() {
        if (previews.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            PreviewState preview = previews.get(player.getUniqueId());
            if (preview == null) {
                continue;
            }
            if (tickCounter > preview.expiresAtTick) {
                previews.remove(player.getUniqueId());
                continue;
            }
            showPreview(player, preview);
        }
    }

    private void updateRooms(Player player, DungeonSession session) {
        if (!session.started) {
            return;
        }
        for (DungeonSession.RoomInstance room : session.rooms) {
            if (!room.activated && room.contains(player.getLocation())) {
                room.activated = true;
                spawnRoomMobs(session, room);
                if (room.enemySpawns.isEmpty()) {
                    room.cleared = true;
                    onRoomCleared(player, session, room);
                } else {
                    player.sendMessage(room.bossRoom ? ChatColor.DARK_RED + "Boss room activated." : ChatColor.RED + "Enemies incoming.");
                }
            }
            if (room.activated && !room.cleared && !room.hasLivingMobs()) {
                room.cleared = true;
                onRoomCleared(player, session, room);
            }
        }
    }

    private boolean atUnlockedExit(Player player, DungeonSession session) {
        for (Location exit : session.exits) {
            if (player.getLocation().distanceSquared(exit.clone().add(0.5D, 1.0D, 0.5D)) <= 4.0D) {
                if (session.clearedRooms < session.totalCombatRooms) {
                    player.sendMessage(ChatColor.RED + "Clear the dungeon first.");
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private void onRoomCleared(Player player, DungeonSession session, DungeonSession.RoomInstance room) {
        if (session.completed) {
            return;
        }
        if (room == null) {
            return;
        }
        if (session.clearedRooms < session.totalCombatRooms) {
            session.clearedRooms++;
        }
        updateScoreboard(player, session);
        DungeonFlow flow = flows.get(session.ownerId);
        if (flow == null) {
            return;
        }
        if (!flow.pending.isEmpty()) {
            TemplateMeta next = flow.pending.remove(0);
            RoomAnchor anchor = flow.lastAnchor;
            if (anchor == null && !session.rooms.isEmpty()) {
                DungeonSession.RoomInstance lastRoom = session.rooms.get(session.rooms.size() - 1);
                anchor = new RoomAnchor(lastRoom.origin, lastRoom.doors, new RotatedSize(
                    Math.max(1, lastRoom.max.getBlockX() - lastRoom.min.getBlockX() + 1),
                    Math.max(1, lastRoom.max.getBlockZ() - lastRoom.min.getBlockZ() + 1)
                ), next, lastRoom.doors.isEmpty() ? lastRoom.origin : lastRoom.doors.get(0).location(), lastRoom.doors.isEmpty() ? BlockFace.SOUTH : lastRoom.doors.get(0).facing());
            }
            RoomAnchor placed = placeNextRoom(session, next, false, anchor);
            flow.lastAnchor = placed;
        } else {
            flows.remove(session.ownerId);
        }
    }

    private RoomAnchor placeNextRoom(DungeonSession session, TemplateMeta template, boolean firstRoom) {
        DungeonFlow flow = flows.get(session.ownerId);
        RoomAnchor anchor = flow == null ? null : flow.lastAnchor;
        return placeNextRoom(session, template, firstRoom, anchor);
    }

    private RoomAnchor placeNextRoom(DungeonSession session, TemplateMeta template, boolean firstRoom, RoomAnchor anchor) {
        RoomAnchor placed = placeStructure(session, template, anchor, firstRoom, template.type().equals("exit"));
        if (flowFor(session) != null) {
            flowFor(session).lastAnchor = placed;
        }

        return placed;
    }

    private DungeonFlow flowFor(DungeonSession session) {
        return flows.get(session.ownerId);
    }

    private int timeSeconds(DungeonSession session) {
        return (int) Math.max(0L, (tickCounter - session.startTick) / 20L);
    }
    private void ensureTemplateFolders() {
        File root = templateRoot();
        for (int level = 1; level <= plugin.getConfig().getInt("dungeon.max-level", 5); level++) {
            for (String type : TEMPLATE_TYPES) {
                File dir = new File(new File(root, "level" + level), type);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }
        }
    }

    private void loadProgress() {
        unlocked.clear();
        for (String key : progress.getKeys(false)) {
            try {
                unlocked.put(UUID.fromString(key), Math.max(1, progress.getInt(key, 1)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid progress key: " + key);
            }
        }
    }

    private void saveProgress() {
        for (String key : new HashSet<>(progress.getKeys(false))) {
            progress.set(key, null);
        }
        for (Map.Entry<UUID, Integer> entry : unlocked.entrySet()) {
            progress.set(entry.getKey().toString(), Math.max(1, entry.getValue()));
        }
        try {
            progress.save(progressFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save progress.yml: " + e.getMessage());
        }
    }

    private int unlockedLevel(Player player) {
        return unlocked.computeIfAbsent(player.getUniqueId(), id -> 1);
    }

    private File templateRoot() {
        return new File(plugin.getDataFolder(), plugin.getConfig().getString("template-path", "templates"));
    }

    private List<TemplateMeta> loadTemplates(int level) {
        List<TemplateMeta> out = new ArrayList<>();
        File levelDir = new File(templateRoot(), "level" + level);
        if (!levelDir.exists()) {
            return out;
        }

        for (String type : TEMPLATE_TYPES) {
            File typeDir = new File(levelDir, type);
            File[] metas = typeDir.listFiles((FilenameFilter) (dir, name) -> name.endsWith(".yml"));
            if (metas == null) {
                continue;
            }
            for (File metaFile : metas) {
                YamlConfiguration meta = YamlConfiguration.loadConfiguration(metaFile);
                File structureFile = new File(typeDir, meta.getString("structure", metaFile.getName().replaceFirst("\\.yml$", ".nbt")));
                if (!structureFile.exists()) {
                    continue;
                }
                out.add(new TemplateMeta(
                    meta.getString("name", structureFile.getName().replaceFirst("\\.nbt$", "")),
                    normalizeType(meta.getString("type", type)),
                    structureFile,
                    Math.max(1, meta.getInt("width", 1)),
                    Math.max(1, meta.getInt("height", 1)),
                    Math.max(1, meta.getInt("length", 1)),
                    readMarkers(meta.getMapList("markers.spawn")),
                    readMarkers(meta.getMapList("markers.trigger")),
                    readMarkers(meta.getMapList("markers.exit")),
                    readMarkers(meta.getMapList("markers.enemy")),
                    readDoorMarkers(meta.getMapList("markers.link"))
                ));
            }
        }

        out.sort(Comparator.comparing(TemplateMeta::name));
        return out;
    }

    private boolean initializeDungeon(DungeonSession session, List<TemplateMeta> templates, int rooms) {
        TemplateMeta entrance = pickType(templates, "entrence");
        TemplateMeta exit = pickType(templates, "exit");
        List<TemplateMeta> normalRooms = filterType(templates, "room");
        List<TemplateMeta> bossRooms = filterType(templates, "boss");
        if (entrance == null || exit == null || normalRooms.isEmpty()) {
            return false;
        }

        Random random = new Random();
        List<TemplateMeta> pending = new ArrayList<>();
        int combatRooms = Math.max(0, rooms - 1);
        for (int i = 0; i < combatRooms; i++) {
            List<TemplateMeta> source = normalRooms;
            if (!bossRooms.isEmpty() && i == combatRooms - 1 && session.difficulty.level >= 3) {
                source = bossRooms;
            }
            pending.add(pickDifferent(source, pending.isEmpty() ? null : pending.get(pending.size() - 1), random));
        }
        pending.add(exit);

        DungeonFlow flow = new DungeonFlow(entrance, pending);
        flows.put(session.ownerId, flow);
        session.totalCombatRooms = combatRooms;
        RoomAnchor entranceAnchor = placeNextRoom(session, entrance, true);
        flow.lastAnchor = entranceAnchor;
        return !session.rooms.isEmpty();
    }

    private TemplateMeta pickType(List<TemplateMeta> templates, String type) {
        for (TemplateMeta template : templates) {
            if (template.type().equals(type)) {
                return template;
            }
        }
        return null;
    }

    private List<TemplateMeta> filterType(List<TemplateMeta> templates, String type) {
        List<TemplateMeta> out = new ArrayList<>();
        for (TemplateMeta template : templates) {
            if (template.type().equals(type)) {
                out.add(template);
            }
        }
        return out;
    }

    private RoomAnchor placeStructure(DungeonSession session, TemplateMeta template, RoomAnchor anchor, boolean firstRoom, boolean lastRoom) {
        StructureManager structureManager = Bukkit.getStructureManager();
        Structure structure;
        try {
            structure = structureManager.loadStructure(template.structureFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load structure " + template.structureFile().getName() + ": " + e.getMessage());
            return new RoomAnchor(session.origin.clone(), new ArrayList<>(), new RotatedSize(1, 1), template, session.origin.clone(), BlockFace.SOUTH);
        }

        StructureRotation rotation = selectRotation(template, anchor);
        RotatedSize size = rotatedSize(template, rotation);
        Location placeAt = computePlacement(session, template, rotation, size, anchor, firstRoom);
        structure.place(placeAt, true, rotation, Mirror.NONE, 0, 1.0F, new Random());

        List<DungeonSession.DoorMarker> doors = new ArrayList<>();
        List<Location> enemySpawns = new ArrayList<>();
        applyMarkers(session, template, rotation, placeAt, size, doors, enemySpawns);
        Location min = placeAt.clone();
        Location max = placeAt.clone().add(size.width() - 1, template.height() - 1, size.length() - 1);
        session.rooms.add(new DungeonSession.RoomInstance(placeAt, min, max, doors, enemySpawns, template.type().equals("boss")));
        DungeonSession.DoorMarker nextDoor = pickNextDoor(doors, anchor == null ? null : anchor.nextFacing);
        if (nextDoor == null) {
            nextDoor = doors.isEmpty() ? new DungeonSession.DoorMarker(placeAt.clone(), BlockFace.SOUTH) : doors.get(0);
        }
        return new RoomAnchor(placeAt, doors, size, template, nextDoor.location(), nextDoor.facing());
    }

    private void applyMarkers(DungeonSession session, TemplateMeta template, StructureRotation rotation, Location origin, RotatedSize size, List<DungeonSession.DoorMarker> doors, List<Location> enemySpawns) {
        for (LocalMarker marker : template.spawns()) {
            Location world = toWorld(origin, template, rotation, size, marker.x(), marker.y(), marker.z());
            session.spawns.add(world);
            replaceMarker(world, Material.LIME_SHULKER_BOX);
        }
        for (LocalMarker marker : template.triggers()) {
            Location world = toWorld(origin, template, rotation, size, marker.x(), marker.y(), marker.z());
            session.triggers.add(world);
            replaceMarker(world, Material.PURPLE_SHULKER_BOX);
        }
        for (LocalMarker marker : template.exits()) {
            Location world = toWorld(origin, template, rotation, size, marker.x(), marker.y(), marker.z());
            session.exits.add(world);
            replaceMarker(world, Material.RED_SHULKER_BOX);
        }
        for (LocalMarker marker : template.enemies()) {
            Location world = toWorld(origin, template, rotation, size, marker.x(), marker.y(), marker.z());
            enemySpawns.add(world.clone().add(0.5D, 1.0D, 0.5D));
            replaceMarker(world, Material.LIGHT_BLUE_SHULKER_BOX);
        }
        for (LocalDoorMarker marker : template.doors()) {
            if (doors.size() >= 3) break;
            Location world = toWorld(origin, template, rotation, size, marker.x(), marker.y(), marker.z());
            doors.add(new DungeonSession.DoorMarker(world, rotateFace(marker.facing(), rotation)));
            replaceMarker(world, Material.YELLOW_SHULKER_BOX);
        }
    }

    private void replaceMarker(Location world, Material markerType) {
        if (!plugin.getConfig().getBoolean("dungeon.replace-markers-with-air", true)) {
            return;
        }
        return;
    }

    private StructureRotation selectRotation(TemplateMeta template, RoomAnchor anchor) {
        if (anchor == null) {
            return StructureRotation.NONE;
        }
        BlockFace required = anchor.nextFacing.getOppositeFace();
        StructureRotation best = StructureRotation.NONE;
        int bestScore = Integer.MIN_VALUE;
        for (StructureRotation rotation : List.of(StructureRotation.NONE, StructureRotation.CLOCKWISE_90, StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90)) {
            int score = hasDoorFacing(template, rotation, required) ? 100 : -100;
            for (LocalDoorMarker door : template.doors()) {
                BlockFace rotated = rotateFace(door.facing(), rotation);
                if (rotated == required) score += 20;
                if (rotated == required.getOppositeFace()) score += 5;
            }
            if (score > bestScore) {
                bestScore = score;
                best = rotation;
            }
        }
        return best;
    }

    private boolean hasDoorFacing(TemplateMeta template, StructureRotation rotation, BlockFace desired) {
        for (LocalDoorMarker door : template.doors()) {
            if (rotateFace(door.facing(), rotation) == desired) {
                return true;
            }
        }
        return false;
    }

    private RotatedSize rotatedSize(TemplateMeta template, StructureRotation rotation) {
        boolean sideways = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
        return sideways ? new RotatedSize(template.length(), template.width()) : new RotatedSize(template.width(), template.length());
    }

    private Location toWorld(Location origin, TemplateMeta template, StructureRotation rotation, RotatedSize size, int x, int y, int z) {
        int wx;
        int wz;
        switch (rotation) {
            case NONE -> {
                wx = x;
                wz = z;
            }
            case CLOCKWISE_90 -> {
                wx = size.width() - 1 - z;
                wz = x;
            }
            case CLOCKWISE_180 -> {
                wx = size.width() - 1 - x;
                wz = size.length() - 1 - z;
            }
            case COUNTERCLOCKWISE_90 -> {
                wx = z;
                wz = size.length() - 1 - x;
            }
            default -> {
                wx = x;
                wz = z;
            }
        }
        return origin.clone().add(wx, y, wz);
    }

    private Location computePlacement(DungeonSession session, TemplateMeta template, StructureRotation rotation, RotatedSize size, RoomAnchor anchor, boolean firstRoom) {
        if (anchor == null || firstRoom) {
            return session.origin.clone();
        }
        LocalDoorMarker localDoor = bestLocalDoor(template, rotation, anchor.nextFacing.getOppositeFace());
        if (localDoor == null) {
            return anchor.lastDoorWorld.clone().add(anchor.nextFacing.getModX() * (anchor.size.width() + 1), 0, anchor.nextFacing.getModZ() * (anchor.size.length() + 1));
        }
        int doorX = rotatedX(localDoor, rotation, size);
        int doorZ = rotatedZ(localDoor, rotation, size);
        int targetX = anchor.lastDoorWorld.getBlockX() + anchor.nextFacing.getModX();
        int targetY = anchor.lastDoorWorld.getBlockY();
        int targetZ = anchor.lastDoorWorld.getBlockZ() + anchor.nextFacing.getModZ();
        return new Location(session.world, targetX - doorX, targetY - localDoor.y(), targetZ - doorZ);
    }

    private LocalDoorMarker bestLocalDoor(TemplateMeta template, StructureRotation rotation, BlockFace requiredFacing) {
        LocalDoorMarker best = null;
        for (LocalDoorMarker door : template.doors()) {
            BlockFace rotated = rotateFace(door.facing(), rotation);
            if (rotated == requiredFacing) {
                return door;
            }
            if (best == null) {
                best = door;
            }
        }
        return best;
    }

    private int rotatedX(LocalDoorMarker marker, StructureRotation rotation, RotatedSize size) {
        return switch (rotation) {
            case NONE -> marker.x();
            case CLOCKWISE_90 -> size.width() - 1 - marker.z();
            case CLOCKWISE_180 -> size.width() - 1 - marker.x();
            case COUNTERCLOCKWISE_90 -> marker.z();
        };
    }

    private int rotatedZ(LocalDoorMarker marker, StructureRotation rotation, RotatedSize size) {
        return switch (rotation) {
            case NONE -> marker.z();
            case CLOCKWISE_90 -> marker.x();
            case CLOCKWISE_180 -> size.length() - 1 - marker.z();
            case COUNTERCLOCKWISE_90 -> size.length() - 1 - marker.x();
        };
    }
    private boolean overlaps(DungeonSession session, Location origin, RotatedSize size) {
        int minX = origin.getBlockX();
        int minY = origin.getBlockY();
        int minZ = origin.getBlockZ();
        int maxX = minX + size.width() - 1;
        int maxZ = minZ + size.length() - 1;
        for (DungeonSession.RoomInstance room : session.rooms) {
            if (boxesOverlap(minX, minY, minZ, maxX, minY + 255, maxZ,
                room.min.getBlockX(), room.min.getBlockY(), room.min.getBlockZ(),
                room.max.getBlockX(), room.max.getBlockY(), room.max.getBlockZ())) {
                return true;
            }
        }
        return false;
    }

    private boolean boxesOverlap(int minX1, int minY1, int minZ1, int maxX1, int maxY1, int maxZ1,
                                 int minX2, int minY2, int minZ2, int maxX2, int maxY2, int maxZ2) {
        return minX1 <= maxX2 && maxX1 >= minX2 && minY1 <= maxY2 && maxY1 >= minY2 && minZ1 <= maxZ2 && maxZ1 >= minZ2;
    }

    private static final class PlacementChoice {
        private final StructureRotation rotation;
        private final RotatedSize size;
        private final Location origin;

        private PlacementChoice(StructureRotation rotation, RotatedSize size, Location origin) {
            this.rotation = rotation;
            this.size = size;
            this.origin = origin;
        }
    }
    private DungeonSession.DoorMarker pickNextDoor(List<DungeonSession.DoorMarker> doors, BlockFace avoidFacing) {
        List<DungeonSession.DoorMarker> candidates = new ArrayList<>();
        for (DungeonSession.DoorMarker door : doors) {
            if (avoidFacing == null || door.facing() != avoidFacing) {
                candidates.add(door);
            }
        }
        if (candidates.isEmpty()) {
            return doors.isEmpty() ? null : doors.get(0);
        }
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private <T> T pickDifferent(List<T> options, T previous, Random random) {
        if (options.isEmpty()) {
            return previous;
        }
        if (options.size() == 1) {
            return options.get(0);
        }
        T choice = options.get(random.nextInt(options.size()));
        if (previous == null) {
            return choice;
        }
        for (int i = 0; i < 4 && choice.equals(previous); i++) {
            choice = options.get(random.nextInt(options.size()));
        }
        return choice;
    }

    private void connectRooms(DungeonSession.RoomInstance a, DungeonSession.RoomInstance b) {
        DungeonSession.DoorMarker left = bestDoor(a.doors, b.doors);
        DungeonSession.DoorMarker right = bestDoor(b.doors, a.doors);
        Location from = left != null ? left.location() : a.origin.clone();
        Location to = right != null ? right.location() : b.origin.clone();
        carveCorridor(a.origin.getWorld(), from, to);
    }

    private DungeonSession.DoorMarker bestDoor(List<DungeonSession.DoorMarker> source, List<DungeonSession.DoorMarker> target) {
        DungeonSession.DoorMarker best = null;
        double bestDistance = Double.MAX_VALUE;
        for (DungeonSession.DoorMarker candidate : source) {
            for (DungeonSession.DoorMarker other : target) {
                if (candidate.facing().getOppositeFace() != other.facing()) {
                    continue;
                }
                double distance = candidate.location().distanceSquared(other.location());
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private void carveCorridor(World world, Location from, Location to) {
        int x = from.getBlockX();
        int y = from.getBlockY();
        int z = from.getBlockZ();
        int tx = to.getBlockX();
        int tz = to.getBlockZ();
        while (x != tx) {
            carveStep(world, x, y, z);
            x += Integer.compare(tx, x);
        }
        while (z != tz) {
            carveStep(world, x, y, z);
            z += Integer.compare(tz, z);
        }
        carveStep(world, x, y, z);
    }

    private void carveStep(World world, int x, int y, int z) {
        for (int dy = 0; dy < 3; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
        }
    }

    private void spawnRoomMobs(DungeonSession session, DungeonSession.RoomInstance room) {
        if (room.enemySpawns.isEmpty()) {
            return;
        }
        List<EntityType> pool = mobPool(session.level, room.bossRoom);
        if (pool.isEmpty()) {
            return;
        }
        for (int i = 0; i < room.enemySpawns.size(); i++) {
            Location spawn = room.enemySpawns.get(i);
            EntityType type = pool.get(i % pool.size());
            if (!type.isSpawnable() || !type.isAlive()) {
                continue;
            }
            LivingEntity entity = (LivingEntity) session.world.spawnEntity(spawn, type);
            entity.setRemoveWhenFarAway(false);
            entity.getPersistentDataContainer().set(plugin.key("dungeon_mob"), PersistentDataType.STRING, session.ownerId.toString());
            room.activeMobs.add(entity.getUniqueId());
        }
    }

    private List<EntityType> mobPool(DungeonLevel level, boolean bossRoom) {
        String path = bossRoom ? "mobs.boss." + level.name() : "mobs.normal." + level.name();
        List<String> raw = plugin.getConfig().getStringList(path);
        if (raw.isEmpty()) {
            raw = bossRoom ? List.of("IRON_GOLEM") : List.of("ZOMBIE", "SPIDER");
        }
        List<EntityType> out = new ArrayList<>();
        for (String value : raw) {
            try {
                out.add(EntityType.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid mob in config: " + value);
            }
        }
        return out;
    }

    private Material marker(String path, Material fallback) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name()));
        return material == null ? fallback : material;
    }

    private Location targetBlock(Location loc) {
        if (loc.getWorld() == null) {
            return loc.clone();
        }
        Location eye = loc.clone().add(0.0D, 1.62D, 0.0D);
        var hit = loc.getWorld().rayTraceBlocks(eye, loc.getDirection(), 16.0D);
        return hit != null && hit.getHitBlock() != null ? hit.getHitBlock().getLocation() : loc.getBlock().getLocation();
    }

    private Location randomOrigin(World world) {
        Random random = new Random();
        int minDistance = plugin.getConfig().getInt("dungeon.origin-min-distance", 200);
        int spread = plugin.getConfig().getInt("dungeon.origin-spread", 2000);
        int x = world.getSpawnLocation().getBlockX() + minDistance + random.nextInt(Math.max(1, spread));
        int z = world.getSpawnLocation().getBlockZ() + minDistance + random.nextInt(Math.max(1, spread));
        int y = Math.max(world.getMinHeight() + 5, world.getHighestBlockYAt(x, z) + 5);
        return new Location(world, x, y, z);
    }

    private String inferType(String name) {
        String lowered = name.toLowerCase(Locale.ROOT);
        if (lowered.contains("entrance") || lowered.contains("entrence")) {
            return "entrence";
        }
        if (lowered.contains("exit")) {
            return "exit";
        }
        if (lowered.contains("boss")) {
            return "boss";
        }
        return "room";
    }

    private String normalizeType(String type) {
        String lowered = type.toLowerCase(Locale.ROOT);
        if (lowered.equals("entrance")) {
            return "entrence";
        }
        return TEMPLATE_TYPES.contains(lowered) ? lowered : inferType(lowered);
    }

    private Location min(Location a, Location b) {
        return new Location(a.getWorld(), Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()));
    }

    private Location max(Location a, Location b) {
        return new Location(a.getWorld(), Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }
    private PreviewState buildPreview(Location min, Location max) {
        PreviewState preview = new PreviewState(min, max, tickCounter + 200L);
        Material spawnMarker = marker("markers.spawn", Material.LIME_SHULKER_BOX);
        Material linkMarker = marker("markers.link", Material.YELLOW_SHULKER_BOX);
        Material connectorMarker = marker("markers.connector", Material.ORANGE_SHULKER_BOX);
        Material exitMarker = marker("markers.exit", Material.RED_SHULKER_BOX);
        Material triggerMarker = marker("markers.trigger", Material.PURPLE_SHULKER_BOX);
        Material enemyMarker = marker("markers.enemy", Material.LIGHT_BLUE_SHULKER_BOX);
        Material boundsMarker = marker("markers.bounds", Material.WHITE_SHULKER_BOX);
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = min.getWorld().getBlockAt(x, y, z);
                    Material type = block.getType();
                    Location at = block.getLocation();
                    if (type == spawnMarker) {
                        preview.spawns.add(at);
                    } else if ((type == linkMarker || type == connectorMarker) && block.getBlockData() instanceof Directional directional) {
                        preview.doors.add(new LocalDoorMarker(x - min.getBlockX(), y - min.getBlockY(), z - min.getBlockZ(), directional.getFacing()));
                    } else if (type == exitMarker) {
                        preview.exits.add(at);
                    } else if (type == triggerMarker) {
                        preview.triggers.add(at);
                    } else if (type == enemyMarker) {
                        preview.enemies.add(at);
                    } else if (type == boundsMarker) {
                        preview.bounds.add(at);
                    }
                }
            }
        }
        return preview;
    }

    private void showPreview(Player player, PreviewState preview) {
        if (player.getWorld() != preview.min.getWorld()) {
            return;
        }
        spawnOutline(player, preview.min, preview.max);
        for (Location location : preview.spawns) {
            player.spawnParticle(Particle.HAPPY_VILLAGER, center(location), 3, 0.15D, 0.15D, 0.15D, 0.0D);
        }
        for (Location location : preview.exits) {
            player.spawnParticle(Particle.FLAME, center(location), 3, 0.15D, 0.15D, 0.15D, 0.0D);
        }
        for (Location location : preview.triggers) {
            player.spawnParticle(Particle.PORTAL, center(location), 6, 0.15D, 0.15D, 0.15D, 0.0D);
        }
        for (Location location : preview.enemies) {
            player.spawnParticle(Particle.SOUL_FIRE_FLAME, center(location), 4, 0.15D, 0.15D, 0.15D, 0.0D);
        }
        for (LocalDoorMarker door : preview.doors) {
            Location world = preview.min.clone().add(door.x(), door.y(), door.z());
            player.spawnParticle(Particle.DUST, center(world), 3, 0.15D, 0.15D, 0.15D, new Particle.DustOptions(Color.YELLOW, 1.2F));
        }
        for (Location location : preview.bounds) {
            player.spawnParticle(Particle.WAX_ON, center(location), 2, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    private void spawnOutline(Player player, Location min, Location max) {
        int[][] corners = {
            {min.getBlockX(), min.getBlockY(), min.getBlockZ()},
            {max.getBlockX() + 1, min.getBlockY(), min.getBlockZ()},
            {min.getBlockX(), max.getBlockY() + 1, min.getBlockZ()},
            {min.getBlockX(), min.getBlockY(), max.getBlockZ() + 1},
            {max.getBlockX() + 1, max.getBlockY() + 1, min.getBlockZ()},
            {max.getBlockX() + 1, min.getBlockY(), max.getBlockZ() + 1},
            {min.getBlockX(), max.getBlockY() + 1, max.getBlockZ() + 1},
            {max.getBlockX() + 1, max.getBlockY() + 1, max.getBlockZ() + 1}
        };
        for (int[] corner : corners) {
            player.spawnParticle(Particle.END_ROD, new Location(min.getWorld(), corner[0], corner[1], corner[2]), 1, 0, 0, 0, 0);
        }
    }

    private Location center(Location location) {
        return location.clone().add(0.5D, 0.5D, 0.5D);
    }

    private void writeMarkers(YamlConfiguration meta, String path, List<Location> locations, Location min) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Location location : locations) {
            values.add(localMap(location, min));
        }
        meta.set(path, values);
    }

    private void writeDoorMarkers(YamlConfiguration meta, List<LocalDoorMarker> doors) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (LocalDoorMarker door : doors) {
            Map<String, Object> map = new HashMap<>();
            map.put("x", door.x());
            map.put("y", door.y());
            map.put("z", door.z());
            map.put("facing", door.facing().name());
            values.add(map);
        }
        meta.set("markers.link", values);
    }

    private Map<String, Object> localMap(Location location, Location min) {
        Map<String, Object> map = new HashMap<>();
        map.put("x", location.getBlockX() - min.getBlockX());
        map.put("y", location.getBlockY() - min.getBlockY());
        map.put("z", location.getBlockZ() - min.getBlockZ());
        return map;
    }

    private List<LocalMarker> readMarkers(List<Map<?, ?>> values) {
        List<LocalMarker> out = new ArrayList<>();
        for (Map<?, ?> value : values) {
            Object x = value.get("x");
            Object y = value.get("y");
            Object z = value.get("z");
            if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                out.add(new LocalMarker(nx.intValue(), ny.intValue(), nz.intValue()));
            }
        }
        return out;
    }

    private List<LocalDoorMarker> readDoorMarkers(List<Map<?, ?>> values) {
        List<LocalDoorMarker> out = new ArrayList<>();
        for (Map<?, ?> value : values) {
            Object x = value.get("x");
            Object y = value.get("y");
            Object z = value.get("z");
            Object facing = value.get("facing");
            if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz && facing instanceof String face) {
                try {
                    out.add(new LocalDoorMarker(nx.intValue(), ny.intValue(), nz.intValue(), BlockFace.valueOf(face)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid saved door facing: " + face);
                }
            }
        }
        return out;
    }

    private BlockFace faceFromDelta(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private BlockFace rotateFace(BlockFace face, StructureRotation rotation) {
        return switch (rotation) {
            case NONE -> face;
            case CLOCKWISE_90 -> switch (face) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> face;
            };
            case CLOCKWISE_180 -> face.getOppositeFace();
            case COUNTERCLOCKWISE_90 -> switch (face) {
                case NORTH -> BlockFace.WEST;
                case WEST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.EAST;
                case EAST -> BlockFace.NORTH;
                default -> face;
            };
        };
    }

    private void setupScoreboard(Player player, DungeonSession session) {
        if (!plugin.getConfig().getBoolean("dungeon.use-scoreboard", true) || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("dungeon", "dummy", ChatColor.GOLD + "Dungeon");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        session.scoreboard = scoreboard;
        session.objective = objective;
        updateScoreboard(player, session);
        player.setScoreboard(scoreboard);
    }

    private record BoundingBox(Location min, Location max) {}
    private record SelectionPreview(PreviewState previewState, String summary, List<String> sides, List<String> openings, String rotation, boolean bossRoom, boolean entranceRoom) {}

    private static final class RoomAnchor {
        private final Location origin;
        private final List<DungeonSession.DoorMarker> doors;
        private final RotatedSize size;
        private final TemplateMeta template;
        private BlockFace nextFacing;
        private Location lastDoorWorld;

        private RoomAnchor(Location origin, List<DungeonSession.DoorMarker> doors, RotatedSize size, TemplateMeta template, Location lastDoorWorld, BlockFace nextFacing) {
            this.origin = origin;
            this.doors = doors;
            this.size = size;
            this.template = template;
            this.lastDoorWorld = lastDoorWorld;
            this.nextFacing = nextFacing;
        }
    }

    private BoundingBox boundsFrom(List<Location> points) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        World world = points.get(0).getWorld();
        for (Location point : points) {
            minX = Math.min(minX, point.getBlockX());
            minY = Math.min(minY, point.getBlockY());
            minZ = Math.min(minZ, point.getBlockZ());
            maxX = Math.max(maxX, point.getBlockX());
            maxY = Math.max(maxY, point.getBlockY());
            maxZ = Math.max(maxZ, point.getBlockZ());
        }
        return new BoundingBox(new Location(world, minX, minY, minZ), new Location(world, maxX, maxY, maxZ));
    }

    private List<String> summarizeSides(List<LocalDoorMarker> doors) {
        Set<String> sides = new HashSet<>();
        for (LocalDoorMarker door : doors) {
            BlockFace face = door.facing();
            if (face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST) {
                sides.add(face.name());
            }
        }
        List<String> ordered = new ArrayList<>(sides);
        ordered.sort(String::compareTo);
        return ordered;
    }

    private List<String> describeOpenings(List<LocalDoorMarker> doors) {
        List<String> out = new ArrayList<>();
        for (LocalDoorMarker door : doors) {
            out.add(door.facing().name() + " @ " + door.x() + "," + door.y() + "," + door.z());
        }
        out.sort(String::compareTo);
        return out;
    }

    private BlockFace primaryFacing(BlockFace face) {
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST -> face;
            default -> BlockFace.NORTH;
        };
    }

    private boolean inferBossRoom(PreviewState preview) {
        return !preview.enemies.isEmpty() && preview.exits.isEmpty() && preview.doors.size() >= 2;
    }

    private boolean inferEntranceRoom(PreviewState preview) {
        return !preview.spawns.isEmpty() && preview.exits.size() <= 1;
    }

    private void updateScoreboard(Player player, DungeonSession session) {
        if (session.objective == null || session.scoreboard == null) {
            return;
        }
        for (String entry : new HashSet<>(session.scoreboard.getEntries())) {
            session.scoreboard.resetScores(entry);
        }
        session.objective.getScore(ChatColor.GREEN + "Level: " + session.level.displayName).setScore(6);
        session.objective.getScore(ChatColor.YELLOW + "Diff: " + session.difficulty.name()).setScore(5);
        session.objective.getScore(ChatColor.AQUA + "Rooms: " + session.roomCount).setScore(4);
        session.objective.getScore(ChatColor.LIGHT_PURPLE + "Clear: " + session.clearedRooms + "/" + session.totalCombatRooms).setScore(4);
        session.objective.getScore(ChatColor.AQUA + "Clear %: " + clearPercent(session) + "%").setScore(3);
        session.objective.getScore(ChatColor.GREEN + "Time: " + timeSeconds(session) + "s").setScore(2);
        session.objective.getScore(ChatColor.GRAY + player.getName()).setScore(1);
    }

    private int clearPercent(DungeonSession session) {
        double clearRatio = session.totalCombatRooms <= 0 ? 0.0D : (double) session.clearedRooms / (double) session.totalCombatRooms;
        return (int) Math.round(clearRatio * 100.0D);
    }


    private void runConfiguredCommand(Player player, String path) {
        String command = plugin.getConfig().getString("triggers." + path, "");
        if (command == null || command.isBlank()) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
    }

    private void completeDungeon(Player player, DungeonSession session) {
        session.completed = true;
        RewardTuning reward = calculateReward(session);
        applyRewards(player, reward);
        int nextLevel = Math.min(plugin.getConfig().getInt("dungeon.max-level", 5), session.level.id + 1);
        if (nextLevel > unlockedLevel(player)) {
            unlocked.put(player.getUniqueId(), nextLevel);
            saveProgress();
        }
        player.sendMessage(ChatColor.GREEN + "Dungeon complete: " + session.level.displayName);
        player.sendMessage(ChatColor.YELLOW + reward.summary);
        if (nextLevel > session.level.id) {
            player.sendMessage(ChatColor.YELLOW + "Unlocked level " + nextLevel + ".");
        }
        teleportAndClearSession(player, session, true);
    }
    public void toggleDevMode(Player player) {
        boolean enabled = !plugin.getConfig().getBoolean("dungeon.dev-mode", false);
        plugin.getConfig().set("dungeon.dev-mode", enabled);
        plugin.saveConfig();
        player.sendMessage(ChatColor.YELLOW + "Dev mode is now " + (enabled ? "enabled" : "disabled") + ".");
    }

    public void forceExitDungeon(Player player) {
        DungeonSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.RED + "You are not in a dungeon.");
            return;
        }
        teleportAndClearSession(player, session, false);
        player.sendMessage(ChatColor.YELLOW + "Dungeon run ended.");
    }

    private void teleportAndClearSession(Player player, DungeonSession session, boolean completed) {
        sessions.remove(player.getUniqueId());
        if (session.returnLocation != null && session.returnLocation.getWorld() != null) {
            player.teleport(session.returnLocation.clone());
        }
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        if (completed) {
            player.sendMessage(ChatColor.GREEN + "Returned to where you started the dungeon.");
        }
    }

    private void applyRewards(Player player, RewardTuning reward) {
        if (reward.xp() > 0) {
            player.giveExp(reward.xp());
        }
    }

    private RewardTuning calculateReward(DungeonSession session) {
        double clearRatio = session.totalCombatRooms <= 0 ? 0.0D : (double) session.clearedRooms / (double) session.totalCombatRooms;
        long elapsedTicks = Math.max(1L, tickCounter - session.freezeUntilTick);
        double timeFactor = Math.max(0.35D, 1.0D - (elapsedTicks / (20.0D * 60.0D * 30.0D)));
        double score = clearRatio * timeFactor;
        int xp = (int) Math.max(0, Math.round(50.0D * score * Math.max(1, session.difficulty.level)));
        String summary = "Rewards scaled by clear rate " + Math.round(clearRatio * 100.0D) + "% and time factor " + Math.round(timeFactor * 100.0D) + "%";
        return new RewardTuning(xp, summary);
    }

    private record RewardTuning(int xp, String summary) {}

    private record GridPos(int x, int z) {}
    private record RotatedSize(int width, int length) {}
    private record LocalMarker(int x, int y, int z) {}
    private record LocalDoorMarker(int x, int y, int z, BlockFace facing) {}
    private record TemplateMeta(String name, String type, File structureFile, int width, int height, int length,
                                List<LocalMarker> spawns, List<LocalMarker> triggers, List<LocalMarker> exits,
                                List<LocalMarker> enemies, List<LocalDoorMarker> doors) {}

    private static final class DungeonFlow {
        private final TemplateMeta entrance;
        private final List<TemplateMeta> pending;
        private RoomAnchor lastAnchor;

        private DungeonFlow(TemplateMeta entrance, List<TemplateMeta> pending) {
            this.entrance = entrance;
            this.pending = pending;
        }
    }

    private static final class PreviewState {
        private final Location min;
        private final Location max;
        private final long expiresAtTick;
        private final List<Location> spawns = new ArrayList<>();
        private final List<Location> triggers = new ArrayList<>();
        private final List<Location> exits = new ArrayList<>();
        private final List<Location> enemies = new ArrayList<>();
        private final List<Location> bounds = new ArrayList<>();
        private final List<LocalDoorMarker> doors = new ArrayList<>();

        private PreviewState(Location min, Location max, long expiresAtTick) {
            this.min = min;
            this.max = max;
            this.expiresAtTick = expiresAtTick;
        }
    }
}







