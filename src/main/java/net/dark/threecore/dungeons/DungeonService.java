package net.dark.threecore.dungeons;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.survival.SurvivalService;
import net.dark.threecore.text.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.util.*;

public final class DungeonService implements Listener {
    private static final String ITEM_KEY = "3smpcore_dungeon_item";
    private static final String ITEM_ID = "dungeon_menu";
    private static final String DEV_TOOL_ID = "dungeon_dev_save";
    private static final String DEV_MARKER_ID = "dungeon_marker";
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
    private net.dark.threecore.social.SocialTabService socialTabService;

    public DungeonService(JavaPlugin plugin, ConfigFiles configs, MenuService menuService, PlayerDataRepository repository, PartyService partyService, SurvivalService survivalService) {
        this.plugin = plugin;
        this.configs = configs;
        this.menuService = menuService;
        this.repository = repository;
        this.partyService = partyService;
        this.survivalService = survivalService;
        loadReservations();
    }

    public void reload() { reservations.clear(); activeGroups.clear(); loadReservations(); }

    public void setSocialTabService(net.dark.threecore.social.SocialTabService socialTabService) {
        this.socialTabService = socialTabService;
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) { if (sender instanceof Player player) teleportDungeonSpawn(player); else Text.send(sender, "<red>Players only.</red>"); return; }
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
                if (args.length >= 2 && args[1].equalsIgnoreCase("leave")) {
                    leaveDungeonEditor(player);
                    return;
                }
                openDungeonEditor(player);
            }
            case "leave" -> { if (sender instanceof Player player) leave(player); }
            case "save" -> { if (sender instanceof Player player) saveTemplate(player, args.length >= 2 ? args[1] : "room_" + System.currentTimeMillis(), args.length >= 3 ? args[2] : "jungle"); }
            case "templates" -> listTemplates(sender);
            case "spawnset" -> { if (sender instanceof Player player) setDungeonSpawn(player); }
            case "give" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { Text.send(sender, "<red>Usage: /dungeon give <player></red>"); return; } Player target = Bukkit.getPlayerExact(args[1]); if (target != null) giveItem(target); }
            case "reload" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reload(); Text.send(sender, "<green>Dungeons reloaded.</green>"); }
            case "clear" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reservations.clear(); saveReservations(); Text.send(sender, "<yellow>Dungeon room reservations cleared.</yellow>"); }
            default -> Text.send(sender, "<gray>/dungeon menu|spawn|spawn set|template|enter [level]|save <id> [level]|dev|templates|leave|give <player>|reload|clear</gray>");
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("menu", "spawn", "spawnset", "template", "setspawn", "enter", "save", "dev", "editor", "templates", "leave", "give", "reload", "clear");
        if (args[0].equalsIgnoreCase("editor")) return List.of("leave");
        return levelIds();
    }

    private void teleportDungeonSpawn(Player player) {
        Location spawn = readConfiguredLocation("spawn");
        if (spawn == null) { Text.send(player, "<red>Dungeon spawn is not configured.</red>"); return; }
        if (survivalService != null) survivalService.saveCurrentProfile(player);
        clearPlayerState(player);
        clearDungeonVision(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(spawn);
        loadDungeonProfile(player);
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
        loadDungeonProfile(player);
        clearDungeonHubItems(player);
        debug(player, "<gray>Dungeon template initializer used once and locked globally.</gray>");
        Text.send(player, "<green>Dungeon template world prepared.</green> <gray>The one-time initializer is now locked.</gray>");
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder("main"), 27, "3SMP Dungeons");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        DungeonRunOptions options = options(player);
        inv.setItem(4, button(Material.COMPASS, "<gradient:#4c1d95:#a78bfa>Dungeons</gradient>", List.of("<gray>Configure your run, then start.</gray>", "<gray>Level:</gray> <white>" + options.level() + "</white>", "<gray>Difficulty:</gray> <white>" + options.difficulty()[0] + "</white>", "<gray>Party:</gray> <white>" + (options.party()[0] ? "party" : "solo") + "</white>")));
        inv.setItem(10, button(Material.OAK_SAPLING, "<gradient:#34d399:#22c55e>Select Level</gradient>", List.of("<gray>Choose dungeon theme.</gray>")));
        inv.setItem(12, button(difficultyIcon(options.difficulty()[0]), "<gradient:#f59e0b:#ef4444>Select Difficulty</gradient>", List.of("<gray>Current:</gray> <white>" + options.difficulty()[0] + "</white>", "<gray>Money per kill:</gray> <white>" + moneyPerKill(options.difficulty()[0]) + "</white>")));
        inv.setItem(14, button(options.party()[0] ? Material.ORANGE_BANNER : Material.LIGHT_BLUE_BANNER, "<gradient:#60a5fa:#c084fc>Party Mode</gradient>", List.of("<gray>Current:</gray> <white>" + (options.party()[0] ? "party" : "solo") + "</white>", partyService != null && partyService.isInParty(player.getUniqueId()) ? "<gray>Click to configure.</gray>" : "<red>Create/join a party to enable party runs.</red>")));
        inv.setItem(16, button(Material.LIME_DYE, "<green>Start Dungeon</green>", List.of("<gray>Base kit: wooden sword and leather armor.</gray>")));
        player.openInventory(inv);
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
        inv.setItem(15, button(Material.ORANGE_BANNER, "<gold>Party</gold>", List.of(partyService != null && partyService.isInParty(player.getUniqueId()) ? "<gray>Run with your party.</gray>" : "<red>You are not in a party.</red>")));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
    }

    public void enablePartyMode(Player player) { options(player).party()[0] = true; }
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
        if (!isDungeonPlayer(player) && !isDungeonWorld(player.getWorld())) {
            Text.send(player, "<gray>You are not in a dungeon.</gray>");
            return;
        }
        saveDungeonProfile(player);
        activeRuns.remove(player.getUniqueId());
        ACTIVE_DUNGEON_PLAYERS.remove(player.getUniqueId());
        activeGroups.remove(player.getUniqueId());
        activeLayouts.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        teleportDungeonSpawn(player);
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
        World world = dungeonWorld(); if (world == null) { Text.send(player, "<red>Dungeon world could not be loaded.</red>"); return; }
        List<String> plan = buildRoomPlan(id, options(player).difficulty()[0]);
        if (plan.isEmpty()) { Text.send(player, "<red>No saved room templates for level " + id + ". Use /d save <id> " + id + ".</red>"); return; }
        RoomReservation start = allocate(player.getUniqueId(), id, world);
        List<RoomReservation> placed = pasteRoomPlan(plan, start);
        Location spawn = placed.isEmpty() ? null : markerSpawn(plan.get(0), placed.get(0));
        if (survivalService != null) survivalService.saveCurrentProfile(player);
        clearPlayerState(player);
        clearDungeonVision(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(spawn == null ? new Location(world, start.centerX() + 0.5, start.y() + 2, start.centerZ() + 0.5) : spawn);
        clearPlayerState(player);
        if (isEmptyDungeonProfile(player.getUniqueId())) debug(player, "<gray>First dungeon visit detected; applying starter kit.</gray>");
        applyBaseKit(player);
        activeRuns.put(player.getUniqueId(), new ActiveDungeonRun(id, options(player).difficulty()[0], false, 0));
        activeLayouts.put(player.getUniqueId(), buildPlacedRooms(plan, placed));
        ACTIVE_DUNGEON_PLAYERS.add(player.getUniqueId());
        debug(player, "<gray>Dungeon run started:</gray> <white>" + id + "</white> <gray>difficulty</gray> <white>" + options(player).difficulty()[0] + "</white> <gray>rooms</gray> <white>" + plan.size() + "</white>");
        Text.send(player, "<green>Entered " + id + " dungeon.</green> <gray>Rooms:</gray> <white>" + plan.size() + "</white>");
    }

    private void giveDevTool(Player player) {
        if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        player.getInventory().setItem(DEV_TOOL_SLOT, tagged(Material.STRUCTURE_BLOCK, "<gradient:#4c1d95:#a78bfa>Dungeon Room Saver</gradient>", DEV_TOOL_ID));
        Text.send(player, "<green>Dungeon room saver given.</green> <gray>Right-click saves nearby shulker-marked room. Shift-right-click opens marker toolbox.</gray>");
    }

    private void openDevToolbox(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder("dev-toolbox"), 27, "Dungeon Dev Toolbox");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(10, devMarker(Material.WHITE_SHULKER_BOX, "Bounds"));
        inv.setItem(11, devMarker(Material.GREEN_SHULKER_BOX, "Player Spawn"));
        inv.setItem(12, devMarker(Material.YELLOW_SHULKER_BOX, "Entrance"));
        inv.setItem(13, devMarker(Material.ORANGE_SHULKER_BOX, "Connector"));
        inv.setItem(14, devMarker(Material.MAGENTA_SHULKER_BOX, "Vertical Up"));
        inv.setItem(15, devMarker(Material.BROWN_SHULKER_BOX, "Vertical Down"));
        inv.setItem(16, devMarker(Material.LIGHT_BLUE_SHULKER_BOX, "Enemy Spawn"));
        inv.setItem(19, devMarker(Material.RED_SHULKER_BOX, "Exit"));
        inv.setItem(20, devMarker(Material.BLACK_SHULKER_BOX, "Boss"));
        inv.setItem(21, devMarker(Material.CRYING_OBSIDIAN, "Boss Spawner"));
        inv.setItem(17, connectionPreviewItem(player));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
        debug(player, "<gray>Dev toolbox opened with live connection preview.</gray>");
    }

    private void handleDevToolboxClick(Player player, int raw) {
        Map<Integer, Material> markers = Map.ofEntries(
                Map.entry(10, Material.WHITE_SHULKER_BOX),
                Map.entry(11, Material.GREEN_SHULKER_BOX),
                Map.entry(12, Material.YELLOW_SHULKER_BOX),
                Map.entry(13, Material.ORANGE_SHULKER_BOX),
                Map.entry(14, Material.MAGENTA_SHULKER_BOX),
                Map.entry(15, Material.BROWN_SHULKER_BOX),
                Map.entry(16, Material.LIGHT_BLUE_SHULKER_BOX),
                Map.entry(19, Material.RED_SHULKER_BOX),
                Map.entry(20, Material.BLACK_SHULKER_BOX),
                Map.entry(21, Material.CRYING_OBSIDIAN)
        );
        Material material = markers.get(raw);
        if (material == null) return;
        player.getInventory().addItem(devMarker(material, material.name()));
        Text.actionBar(player, "<gray>Added dungeon marker:</gray> <white>" + material.name() + "</white>");
        debug(player, "<gray>Added marker:</gray> <white>" + material.name() + "</white> <gray>and refreshed the toolbox.</gray>");
        Bukkit.getScheduler().runTask(plugin, () -> openDevToolbox(player));
    }

    private void placeDevMarker(Player player, Material material) {
        Location loc = player.getTargetBlockExact(6) == null ? player.getLocation().add(player.getLocation().getDirection().multiply(2)) : player.getTargetBlockExact(6).getLocation().add(0, 1, 0);
        loc.getBlock().setType(material, false);
        Text.actionBar(player, "<gray>Placed marker:</gray> <white>" + material.name() + "</white>");
    }

    private void saveTemplate(Player player, String id, String level) {
        if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        List<Block> bounds = nearby(player, Material.WHITE_SHULKER_BOX);
        if (bounds.size() < 2) { Text.send(player, "<red>Place two white shulker boxes as pos1/pos2 bounds.</red>"); return; }
        Block a = bounds.get(0), b = bounds.get(1);
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        if (maxX - minX + 1 > MAX_SIZE || maxY - minY + 1 > MAX_SIZE || maxZ - minZ + 1 > MAX_SIZE) { Text.send(player, "<red>Room exceeds 64x64x64.</red>"); return; }
        YamlConfiguration yaml = configs.get("dungeons/templates.yml");
        String path = "templates." + id.toLowerCase(Locale.ROOT);
        yaml.set(path, null); yaml.set(path + ".level", level.toLowerCase(Locale.ROOT)); yaml.set(path + ".role", "normal"); yaml.set(path + ".size.x", maxX-minX+1); yaml.set(path + ".size.y", maxY-minY+1); yaml.set(path + ".size.z", maxZ-minZ+1);
        List<String> blocks = new ArrayList<>(); List<String> markers = new ArrayList<>(); List<String> entities = new ArrayList<>();
        for (int x=minX;x<=maxX;x++) for (int y=minY;y<=maxY;y++) for (int z=minZ;z<=maxZ;z++) {
            Block block = player.getWorld().getBlockAt(x,y,z); Material type = block.getType(); if (type.isAir()) continue;
            String rel = (x-minX)+","+(y-minY)+","+(z-minZ);
            Marker marker = marker(type); if (marker != null) { markers.add(rel+":"+marker.id()+":"+facing(block)); continue; }
            blocks.add(rel+"|"+serializeBlockData(block));
        }
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Player) continue;
            Location loc = entity.getLocation();
            if (loc.getBlockX() < minX || loc.getBlockX() > maxX || loc.getBlockY() < minY || loc.getBlockY() > maxY || loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) continue;
            entities.add(serializeEntity(entity, minX, minY, minZ));
        }
        String safeId = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        File structureFile = templateStructureFile(safeId);
        try {
            StructureManager structureManager = Bukkit.getStructureManager();
            Structure structure = structureManager.createStructure();
            structure.fill(new Location(player.getWorld(), minX, minY, minZ), new Location(player.getWorld(), maxX, maxY, maxZ), true);
            structureManager.saveStructure(structureFile, structure);
            yaml.set(path + ".structure", "templates/" + structureFile.getName());
        } catch (Exception ex) {
            Text.send(player, "<yellow>Structure save failed, using YAML fallback only: " + ex.getMessage() + "</yellow>");
            yaml.set(path + ".structure", null);
        }
        yaml.set(path + ".role", detectRoomRole(markers));
        yaml.set(path + ".size-category", sizeCategory(maxX-minX+1, maxY-minY+1, maxZ-minZ+1));
        yaml.set(path + ".blocks", blocks); yaml.set(path + ".markers", markers); yaml.set(path + ".entities", entities);
        try { yaml.save(new File(plugin.getDataFolder(), "dungeons/templates.yml")); } catch (Exception ignored) {}
        debug(player, "<gray>Saved template</gray> <white>" + id + "</white> <gray>level</gray> <white>" + level + "</white> <gray>role</gray> <white>" + detectRoomRole(markers) + "</white> <gray>markers</gray> <white>" + markers.size() + "</white>");
        Text.send(player, "<green>Saved dungeon room template " + id + " with " + blocks.size() + " blocks and " + markers.size() + " markers.</green>");
    }

    private void pasteTemplate(String id, RoomReservation room) {
        World world = Bukkit.getWorld(room.world()); if (world == null) return;
        var yaml = configs.get("dungeons/templates.yml");
        clearTemplateArea(world, room, yaml.getInt("templates." + id + ".size.x", MAX_SIZE), yaml.getInt("templates." + id + ".size.y", MAX_SIZE), yaml.getInt("templates." + id + ".size.z", MAX_SIZE));
        if (!roomIsConnectable(yaml, id)) return;
        if (pasteStructure(id, room)) {
            stripRuntimeMarkers(world, room, yaml.getInt("templates." + id + ".size.x", MAX_SIZE), yaml.getInt("templates." + id + ".size.y", MAX_SIZE), yaml.getInt("templates." + id + ".size.z", MAX_SIZE));
            return;
        }
        for (String entry : yaml.getStringList("templates." + id + ".blocks")) {
            String[] p = entry.split("\\|", 3); if (p.length < 2) continue; String[] xyz = p[0].split(",");
            Block target = world.getBlockAt(room.centerX()+Integer.parseInt(xyz[0]), room.y()+Integer.parseInt(xyz[1]), room.centerZ()+Integer.parseInt(xyz[2]));
            Material mat = parseMaterial(p[1]);
            target.setType(mat, false);
            if (p.length >= 3 && !p[2].isBlank()) {
                BlockData data = Bukkit.createBlockData(p[2]);
                target.setBlockData(data, false);
            }
        }
        for (String raw : yaml.getStringList("templates." + id + ".entities")) spawnSerializedEntity(world, room, raw);
    }

    private void clearTemplateArea(World world, RoomReservation room, int sizeX, int sizeY, int sizeZ) {
        if (!configs.get("dungeons/dungeons.yml").getBoolean("generation.reset-before-paste", true)) return;
        int maxX = Math.min(MAX_SIZE, Math.max(1, sizeX));
        int maxY = Math.min(MAX_SIZE, Math.max(1, sizeY));
        int maxZ = Math.min(MAX_SIZE, Math.max(1, sizeZ));
        for (Entity entity : new ArrayList<>(world.getEntities())) {
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
        for (String entry : yaml.getStringList("templates." + id + ".markers")) if (entry.contains(":player_spawn:")) { String[] xyz = entry.split(":")[0].split(","); return new Location(world, room.centerX()+Integer.parseInt(xyz[0])+0.5, room.y()+Integer.parseInt(xyz[1])+1, room.centerZ()+Integer.parseInt(xyz[2])+0.5); }
        return null;
    }

    public void giveItem(Player player) { if (net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || net.dark.threecore.duels.DuelService.isDuelPlayer(player) || isDungeonWorld(player.getWorld()) || !isSpawnWorld(player.getWorld())) { clearItem(player); return; } int slot = Math.max(0, Math.min(8, configs.get("dungeons/dungeons.yml").getInt("item.slot", 1))); clearItem(player); player.getInventory().setItem(slot, item()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { giveItem(event.getPlayer()); }
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
                loadDungeonProfile(player);
                clearDungeonHubItems(player);
                giveItem(player);
                applyDungeonSpawnBuffs(player);
                clearDungeonVision(player);
            });
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> giveItem(event.getPlayer()));
    }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) { Player player = event.getPlayer(); if (isDungeonWorld(event.getFrom()) && !isDungeonEditorWorld(event.getFrom())) saveDungeonProfile(player); if (isDungeonEditorWorld(player.getWorld())) { clearPlayerState(player); clearDungeonHubItems(player); giveDevTool(player); } else if (isDungeonWorld(player.getWorld())) { loadDungeonProfile(player); clearDungeonHubItems(player); giveItem(player); } if (isDungeonSpawnWorld(player.getWorld())) applyDungeonSpawnBuffs(player); else clearDungeonSpawnBuffs(player); }
    @EventHandler public void onMove(PlayerMoveEvent event) { if (event.getTo() == null) return; if (event.getFrom().getBlockX()==event.getTo().getBlockX() && event.getFrom().getBlockY()==event.getTo().getBlockY() && event.getFrom().getBlockZ()==event.getTo().getBlockZ()) return; if (isDungeonSpawnWorld(event.getPlayer().getWorld())) applyDungeonSpawnBuffs(event.getPlayer()); handleDungeonRoomProgress(event.getPlayer(), event.getTo()); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (event.getAction()!=Action.RIGHT_CLICK_AIR && event.getAction()!=Action.RIGHT_CLICK_BLOCK) return; if (isDevTool(event.getItem())) { event.setCancelled(true); if (!isDungeonEditorWorld(event.getPlayer().getWorld())) return; if (event.getPlayer().isSneaking()) openDevToolbox(event.getPlayer()); else saveTemplate(event.getPlayer(), "room_" + System.currentTimeMillis(), options(event.getPlayer()).level()); return; } if (isDevMarker(event.getItem())) { event.setCancelled(true); if (!isDungeonEditorWorld(event.getPlayer().getWorld())) return; placeDevMarker(event.getPlayer(), event.getItem().getType()); return; } if (isItem(event.getItem())) { event.setCancelled(true); teleportDungeonSpawn(event.getPlayer()); } }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (isItem(event.getItemDrop().getItemStack()) || isDevTool(event.getItemDrop().getItemStack()) || isDevMarker(event.getItemDrop().getItemStack())) { event.setCancelled(true); if (isDungeonEditorWorld(event.getPlayer().getWorld())) Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(event.getPlayer())); } }
    @EventHandler public void onClick(InventoryClickEvent event) { if (event.getInventory().getHolder() instanceof DungeonHolder holder) { event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player p)) return; DungeonRunOptions options = options(p); int raw = event.getRawSlot(); if (raw == 22) { playBackSound(p); openMenu(p); return; } switch (holder.context()) { case "main" -> { if (raw == 10) { playBackSound(p); openLevelMenu(p); } else if (raw == 12) { playBackSound(p); openDifficultyMenu(p); } else if (raw == 14) { playBackSound(p); openPartyMenu(p); } else if (raw == 16) enter(p, options.level()); } case "levels" -> { int[] slots={10,11,12,13,14}; int idx=-1; for(int i=0;i<slots.length;i++) if(slots[i]==raw) idx=i; List<String> levels=levelIds(); if(idx>=0&&idx<levels.size()){String level=levels.get(idx); if(!configs.get("dungeons/dungeons.yml").getBoolean("levels."+level+".coming-soon",false)) options.level(level); playBackSound(p); openMenu(p);} } case "difficulty" -> { if(raw==10) options.difficulty()[0]="easy"; else if(raw==12) options.difficulty()[0]="normal"; else if(raw==14) options.difficulty()[0]="hard"; else if(raw==16) { if (canUseNightmare(p, options.level())) options.difficulty()[0]="nightmare"; else Text.send(p, "<red>Beat Easy, Normal and Hard on this level before Nightmare.</red>"); } playBackSound(p); openMenu(p); } case "party" -> { if(raw==11) options.party()[0]=false; else if(raw==15 && partyService!=null && partyService.isInParty(p.getUniqueId())) options.party()[0]=true; playBackSound(p); openMenu(p); } case "dev-toolbox" -> handleDevToolboxClick(p, raw); default -> { } } return; } if (isItem(event.getCurrentItem())) { event.setCancelled(true); clearCursor(event); if (event.getWhoClicked() instanceof Player p && event.getClickedInventory()==p.getInventory()) { p.setItemOnCursor(null); p.updateInventory(); teleportDungeonSpawn(p); } return; } if (event.getWhoClicked() instanceof Player p && isDungeonEditorWorld(p.getWorld()) && (isDevTool(event.getCurrentItem()) || isDevMarker(event.getCurrentItem()) || isDevTool(event.getCursor()) || isDevMarker(event.getCursor()))) { event.setCancelled(true); clearCursor(event); Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(p)); } }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (!(event.getWhoClicked() instanceof Player player) || !isDungeonEditorWorld(player.getWorld())) return; if (isDevTool(event.getOldCursor()) || isDevMarker(event.getOldCursor())) { event.setCancelled(true); Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(player)); } }
    @EventHandler public void onMobDeath(EntityDeathEvent event) { if (event.getEntity().getKiller() == null) return; Player killer = event.getEntity().getKiller(); ActiveDungeonRun run = activeRuns.get(killer.getUniqueId()); if (run == null) return; int kills = run.kills() + 1; boolean bossKill = event.getEntity().getPersistentDataContainer().has(new NamespacedKey(plugin, DUNGEON_BOSS_KEY)); boolean firstBossKill = bossKill && !run.bossDefeated(); boolean bossDefeated = run.bossDefeated() || bossKill; activeRuns.put(killer.getUniqueId(), new ActiveDungeonRun(run.level(), run.difficulty(), bossDefeated, kills)); double amount = moneyPerKill(run.difficulty()); repository.setMoneyBalance(killer.getUniqueId(), repository.getMoneyBalance(killer.getUniqueId()) + amount); if (firstBossKill) rewardBossClear(killer, run); Text.actionBar(killer, "<gradient:#4c1d95:#a78bfa>Dungeon kill</gradient> <gray>+ $" + amount + "</gray>" + (bossDefeated ? " <green>Boss defeated. Exit room unlocked.</green>" : "")); }
    @EventHandler public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isDungeonWorld(event.getLocation().getWorld())) return;
        List<String> allowedReasons = configs.get("dungeons/dungeons.yml").getStringList("world-generation.allowed-spawn-reasons");
        String reason = event.getSpawnReason().name();
        if (allowedReasons.stream().anyMatch(value -> value.equalsIgnoreCase(reason))) {
            return;
        }
        event.setCancelled(true);
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
        if (isDungeonWorld(event.getPlayer().getWorld()) && !isDungeonEditorWorld(event.getPlayer().getWorld())) saveDungeonProfile(event.getPlayer());
        activeRuns.remove(event.getPlayer().getUniqueId());
        ACTIVE_DUNGEON_PLAYERS.remove(event.getPlayer().getUniqueId());
        activeGroups.remove(event.getPlayer().getUniqueId());
        activeLayouts.remove(event.getPlayer().getUniqueId());
    }

    private void enterParty(Player player, String level) {
        if (partyService == null || !partyService.isInParty(player.getUniqueId())) { Text.send(player, "<red>Create or join a party first.</red>"); return; }
        if (!partyService.isLeader(player.getUniqueId())) { Text.send(player, "<yellow>Only the party leader can start a party dungeon.</yellow>"); return; }
        String difficulty = options(player).difficulty()[0];
        java.util.LinkedHashSet<UUID> members = new java.util.LinkedHashSet<>(partyService.partyMembers(player.getUniqueId()));
        if (members.isEmpty()) members.add(player.getUniqueId());
        java.util.List<Player> onlineMembers = members.stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
        if (onlineMembers.size() != members.size()) { Text.send(player, "<red>All party members must be online to start a party dungeon.</red>"); return; }
        for (Player member : onlineMembers) {
            if (!canStartDifficulty(member, level, difficulty)) return;
        }
        World world = dungeonWorld(); if (world == null) { Text.send(player, "<red>Dungeon world could not be loaded.</red>"); return; }
        List<String> plan = buildRoomPlan(level, difficulty);
        if (plan.isEmpty()) { Text.send(player, "<red>No saved room templates for level " + level + ".</red>"); return; }
        RoomReservation start = allocate(player.getUniqueId(), level, world);
        List<RoomReservation> placed = pasteRoomPlan(plan, start);
        Location spawn = placed.isEmpty() ? null : markerSpawn(plan.get(0), placed.get(0));
        int offsetIndex = 0;
        for (Player member : onlineMembers) {
            if (survivalService != null) survivalService.saveCurrentProfile(member);
            clearPlayerState(member);
            Location target = spawn == null ? new Location(world, start.centerX() + 0.5 + (offsetIndex * 1.5), start.y() + 2, start.centerZ() + 0.5) : spawn.clone().add(offsetIndex * 1.25, 0.0, 0.0);
            member.teleport(target);
            clearPlayerState(member);
            applyBaseKit(member);
            ActiveDungeonRun run = new ActiveDungeonRun(level, difficulty, false, 0);
            activeRuns.put(member.getUniqueId(), run);
            activeLayouts.put(member.getUniqueId(), buildPlacedRooms(plan, placed));
            ACTIVE_DUNGEON_PLAYERS.add(member.getUniqueId());
            activeGroups.put(member.getUniqueId(), new java.util.LinkedHashSet<>(members));
            Text.send(member, "<gradient:#4c1d95:#a78bfa>Party dungeon started.</gradient> <gray>Level:</gray> <white>" + level + "</white> <gray>Difficulty:</gray> <white>" + difficulty + "</white>");
            offsetIndex++;
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
        int target = Math.max(1, configs.get("dungeons/dungeons.yml").getInt("generation.rooms." + difficulty.toLowerCase(Locale.ROOT), configs.get("dungeons/dungeons.yml").getInt("generation.rooms.easy", 5)));
        List<String> shuffled = new ArrayList<>(normal); Collections.shuffle(shuffled);
        List<String> plan = new ArrayList<>();
        plan.add(entrance.get(new Random().nextInt(entrance.size())));
        for (int i = 0; i < target && !shuffled.isEmpty(); i++) {
            if (i > 0 && i % shuffled.size() == 0) Collections.shuffle(shuffled);
            String candidate = shuffled.get(i % shuffled.size());
            if (i < shuffled.size()) {
                if (!plan.contains(candidate)) plan.add(candidate);
            } else {
                plan.add(candidate);
            }
        }
        if (!boss.isEmpty()) plan.add(boss.get(new Random().nextInt(boss.size())));
        if (!exit.isEmpty()) plan.add(exit.get(new Random().nextInt(exit.size())));
        return plan;
    }

    private List<RoomReservation> pasteRoomPlan(List<String> plan, RoomReservation start) {
        List<RoomReservation> placed = new ArrayList<>();
        RoomReservation previousRoom = null;
        String previousTemplate = null;
        for (int i = 0; i < plan.size(); i++) {
            String id = plan.get(i);
            if (!roomIsConnectable(configs.get("dungeons/templates.yml"), id)) {
                Bukkit.getLogger().info("[3SMPCore] DungeonDebug skipped template " + id + " because it has no usable markers.");
                continue;
            }
            RoomReservation room = previousRoom == null ? new RoomReservation(start.key() + ":" + i, start.world(), start.level(), start.centerX(), start.y(), start.centerZ()) : nextConnectedRoom(start.key() + ":" + i, previousRoom, previousTemplate, id);
            pasteTemplate(id, room);
            placed.add(room);
            previousRoom = room;
            previousTemplate = id;
            debug(null, "<gray>Placed room</gray> <white>" + id + "</white> <gray>at</gray> <white>" + room.centerX() + "," + room.y() + "," + room.centerZ() + "</white> <gray>using marker chaining.</gray>");
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

        private List<Block> nearby(Player player, Material material) { List<Block> out = new ArrayList<>(); Location c = player.getLocation(); int r = 80; for (int x=c.getBlockX()-r;x<=c.getBlockX()+r;x++) for(int y=Math.max(player.getWorld().getMinHeight(), c.getBlockY()-r);y<=Math.min(player.getWorld().getMaxHeight()-1,c.getBlockY()+r);y++) for(int z=c.getBlockZ()-r;z<=c.getBlockZ()+r;z++) { Block b=player.getWorld().getBlockAt(x,y,z); if(b.getType()==material) out.add(b); } return out; }
    private Marker marker(Material m) { return switch(m) { case YELLOW_SHULKER_BOX -> new Marker("entrance"); case ORANGE_SHULKER_BOX -> new Marker("connector"); case MAGENTA_SHULKER_BOX -> new Marker("up"); case BROWN_SHULKER_BOX -> new Marker("down"); case LIGHT_BLUE_SHULKER_BOX -> new Marker("enemy_spawn"); case RED_SHULKER_BOX -> new Marker("exit"); case PURPLE_SHULKER_BOX -> new Marker("trigger"); case GREEN_SHULKER_BOX -> new Marker("player_spawn"); case BLACK_SHULKER_BOX -> new Marker("boss"); case CRYING_OBSIDIAN -> new Marker("boss_spawner"); default -> null; }; }
    private String facing(Block b) { return b.getBlockData() instanceof Directional d ? d.getFacing().name() : "UP"; }
    private String serializeBlockData(Block block) {
        BlockData data = block.getBlockData();
        return block.getType().name() + "|" + data.getAsString();
    }
    private String firstTemplate(String level) { var sec=configs.get("dungeons/templates.yml").getConfigurationSection("templates"); if(sec==null)return null; for(String id:sec.getKeys(false)) if(level.equalsIgnoreCase(sec.getString(id+".level"))) return id; return null; }
    private void listTemplates(CommandSender s) { var sec=configs.get("dungeons/templates.yml").getConfigurationSection("templates"); Text.send(s, "<gray>Templates: " + (sec==null?"none":String.join(", ",sec.getKeys(false))) + "</gray>"); }
    private RoomReservation allocate(UUID uuid, String level, World world) { String key=uuid+":"+level; if(reservations.containsKey(key))return reservations.get(key); int spacing=configs.get("dungeons/dungeons.yml").getInt("generation.spacing",96); int y=configs.get("dungeons/dungeons.yml").getInt("levels."+level+".y",80); int idx=reservations.size(); int cols=Math.max(1, configs.get("dungeons/dungeons.yml").getInt("generation.columns",8)); RoomReservation r=new RoomReservation(key,world.getName(),level,(idx%cols)*spacing,y,(idx/cols)*spacing); reservations.put(key,r); saveReservations(); return r; }
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
        if (configs.get("dungeons/dungeons.yml").getBoolean("world-generation.multiverse", true) && Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import " + created.getName() + " normal"));
        }
        return created;
    }
    private void clearItem(Player player){ for(int i=0;i<player.getInventory().getSize();i++) if(isItem(player.getInventory().getItem(i))) player.getInventory().setItem(i,null); }
    private ItemStack item(){ return tagged(Material.COMPASS, configs.get("dungeons/dungeons.yml").getString("item.name","<gradient:#4c1d95:#a78bfa>Dungeons</gradient>")); }
    private ItemStack tagged(Material mat,String name){ return tagged(mat, name, ITEM_ID); }
    private ItemStack tagged(Material mat,String name,String id){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm(name)); m.lore(List.of(Text.mm(id.equals(DEV_TOOL_ID) ? "<gray>Right-click save. Shift-right-click toolbox.</gray>" : "<gray>Click to open dungeons.</gray>"))); m.getPersistentDataContainer().set(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING, id); s.setItemMeta(m); return s; }
    private boolean isItem(ItemStack i){ return i!=null&&i.hasItemMeta()&&ITEM_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private boolean isDevTool(ItemStack i){ return i!=null&&i.hasItemMeta()&&DEV_TOOL_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private boolean isDevMarker(ItemStack i){ return i!=null&&i.hasItemMeta()&&DEV_MARKER_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private ItemStack devMarker(Material mat, String label){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm("<gradient:#4c1d95:#a78bfa>"+label+" Marker</gradient>")); m.lore(List.of(Text.mm("<gray>Right-click to place this marker.</gray>"))); m.getPersistentDataContainer().set(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING, DEV_MARKER_ID); s.setItemMeta(m); return s; }
    private ItemStack pane(){ return button(Material.GRAY_STAINED_GLASS_PANE," ",List.of()); }
    private ItemStack button(Material mat,String name,List<String> lore){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm(name)); m.lore(lore.stream().map(Text::mm).toList()); s.setItemMeta(m); return s; }
    private void clearCursor(InventoryClickEvent event){ event.setCursor(null); if(event.getWhoClicked() instanceof Player p) Bukkit.getScheduler().runTask(plugin, p::updateInventory); }
    private void enforceEditorToolbar(Player player){ if(!isDungeonEditorWorld(player.getWorld())) return; ItemStack tool = null; for(int i=0;i<player.getInventory().getSize();i++){ ItemStack item = player.getInventory().getItem(i); if(isDevTool(item)){ tool = item.clone(); if(i != DEV_TOOL_SLOT) player.getInventory().setItem(i, null); } } player.getInventory().setItem(DEV_TOOL_SLOT, tool == null ? tagged(Material.STRUCTURE_BLOCK, "<gradient:#4c1d95:#a78bfa>Dungeon Room Saver</gradient>", DEV_TOOL_ID) : tool); player.updateInventory(); }
    private boolean isDungeonEditorWorld(World world){ return world != null && world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("editor-world","dungeons_editor")); }
    private <T> void setGameRule(World world, String ruleName, T value){ @SuppressWarnings("unchecked") GameRule<T> rule = (GameRule<T>) Registry.GAME_RULE.get(NamespacedKey.minecraft(toSnakeCase(ruleName))); if(rule != null) world.setGameRule(rule, value); }
    private String toSnakeCase(String value){ return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT); }
    private String nameOf(UUID uuid){ Player online = Bukkit.getPlayer(uuid); if(online != null) return online.getName(); var offline = Bukkit.getOfflinePlayer(uuid); return offline.getName() == null ? uuid.toString() : offline.getName(); }
    private Material parseMaterial(String in){ try{return Material.valueOf(in.toUpperCase(Locale.ROOT));}catch(Exception e){return Material.STONE;} }
    private Location readConfiguredLocation(String path) { var s = configs.get("dungeons/dungeons.yml").getConfigurationSection(path); if (s == null) return null; String fallback = path.equalsIgnoreCase("spawn") ? configs.get("dungeons/dungeons.yml").getString("spawn.world", configs.get("dungeons/dungeons.yml").getString("world", "dungeons_spawn")) : configs.get("dungeons/dungeons.yml").getString("world", "dungeons"); World w = Bukkit.getWorld(s.getString("world", fallback)); return w == null ? null : new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")); }
    private void writeLocation(org.bukkit.configuration.file.YamlConfiguration y, String p, Location l) { y.set(p+".world", l.getWorld().getName()); y.set(p+".x", l.getX()); y.set(p+".y", l.getY()); y.set(p+".z", l.getZ()); y.set(p+".yaw", l.getYaw()); y.set(p+".pitch", l.getPitch()); }
    private List<String> levelIds(){ var sec=configs.get("dungeons/dungeons.yml").getConfigurationSection("levels"); return sec==null?List.of("jungle"):List.copyOf(sec.getKeys(false)); }
    private void loadReservations(){ var sec=configs.get("dungeons/rooms.yml").getConfigurationSection("rooms"); if(sec==null)return; for(String k:sec.getKeys(false)) reservations.put(k,new RoomReservation(k,sec.getString(k+".world"),sec.getString(k+".level"),sec.getInt(k+".x"),sec.getInt(k+".y"),sec.getInt(k+".z"))); }
    private void saveReservations(){ var y=configs.get("dungeons/rooms.yml"); y.set("rooms",null); for(RoomReservation r:reservations.values()){String p="rooms."+r.key(); y.set(p+".world",r.world()); y.set(p+".level",r.level()); y.set(p+".x",r.centerX()); y.set(p+".y",r.y()); y.set(p+".z",r.centerZ());} try{y.save(new File(plugin.getDataFolder(),"dungeons/rooms.yml"));}catch(Exception ignored){} }
    public boolean isDungeonWorld(World world){ return world != null && (world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("world","dungeons")) || world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("editor-world","dungeons_editor")) || world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("spawn.world","dungeons_spawn"))); }
    private boolean isDungeonSpawnWorld(World world){ return world != null && world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("spawn.world","dungeons_spawn")); }
    private boolean isSpawnWorld(World world){ return world != null && world.getName().equalsIgnoreCase(configs.get("core/config.yml").getString("spawn.world","spawn")); }
    private void saveDungeonProfile(Player player){ repository.saveInventoryProfile(player.getUniqueId(), "dungeon", player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand()); }
    private void loadDungeonProfile(Player player){ var data = repository.loadInventoryProfile(player.getUniqueId(), "dungeon"); clearPlayerState(player); player.getInventory().setContents(data.contents()); player.getInventory().setArmorContents(data.armor()); player.getInventory().setItemInOffHand(data.offhand() == null ? new ItemStack(Material.AIR) : data.offhand()); player.updateInventory(); }
    private boolean isEmptyDungeonProfile(UUID uuid) { return isEmptyProfile(repository.loadInventoryProfile(uuid, "dungeon")); }
    private boolean isEmptyProfile(PlayerDataRepository.InventoryProfile data) {
        if (data == null) return true;
        boolean contentsEmpty = Arrays.stream(data.contents()).allMatch(this::isAir);
        boolean armorEmpty = Arrays.stream(data.armor()).allMatch(this::isAir);
        boolean offhandEmpty = isAir(data.offhand());
        return contentsEmpty && armorEmpty && offhandEmpty;
    }
    private boolean isAir(ItemStack item) { return item == null || item.getType().isAir(); }
    private void stripSpawnTools(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;
            if (isItem(item) || isDevTool(item) || isDevMarker(item)) player.getInventory().setItem(i, null);
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
    private boolean roomIsConnectable(YamlConfiguration yaml, String id){ List<String> markers = yaml.getStringList("templates." + id + ".markers"); if (markers.isEmpty()) return false; String role = yaml.getString("templates." + id + ".role", "normal"); boolean entrance = markers.stream().anyMatch(v -> v.contains(":entrance:") || v.contains(":player_spawn:")); boolean connector = markers.stream().anyMatch(v -> v.contains(":connector:") || v.contains(":up:") || v.contains(":down:")); boolean boss = markers.stream().anyMatch(v -> v.contains(":boss:")); boolean exit = markers.stream().anyMatch(v -> v.contains(":exit:")); if (role.equalsIgnoreCase("entrance")) return entrance || connector; if (role.equalsIgnoreCase("boss")) return boss || connector; if (role.equalsIgnoreCase("exit")) return exit || connector; return connector || entrance || boss || exit; }
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
        List<Block> bounds = nearby(player, Material.WHITE_SHULKER_BOX);
        lore.add(Text.mm("<gray>Bounds found:</gray> <white>" + bounds.size() + "</white>"));
        if (bounds.size() >= 2) {
            Block a = bounds.get(0), b = bounds.get(1);
            int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
            int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
            int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
            boolean sized = (maxX - minX + 1) <= MAX_SIZE && (maxY - minY + 1) <= MAX_SIZE && (maxZ - minZ + 1) <= MAX_SIZE;
            lore.add(Text.mm(sized ? "<green>Room size within limits.</green>" : "<red>Room exceeds 64x64x64.</red>"));
            lore.add(Text.mm("<gray>Size:</gray> <white>" + (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1) + "</white>"));
        } else lore.add(Text.mm("<yellow>Place two bounds to preview size.</yellow>"));
        List<Block> entrance = nearby(player, Material.YELLOW_SHULKER_BOX);
        List<Block> connector = nearby(player, Material.ORANGE_SHULKER_BOX);
        List<Block> exit = nearby(player, Material.RED_SHULKER_BOX);
        List<Block> boss = nearby(player, Material.BLACK_SHULKER_BOX);
        List<Block> playerSpawn = nearby(player, Material.GREEN_SHULKER_BOX);
        boolean connectable = !entrance.isEmpty() || !connector.isEmpty() || !exit.isEmpty() || !boss.isEmpty() || !playerSpawn.isEmpty();
        lore.add(Text.mm(connectable ? "<green>Connection markers detected.</green>" : "<red>No connection markers detected.</red>"));
        lore.add(Text.mm("<gray>Detected room type:</gray> <white>" + roomTypeName(entrance, connector, exit, boss, playerSpawn) + "</white>"));
        lore.add(Text.mm("<gray>Entrance:</gray> <white>" + entrance.size() + "</white> <gray>Connector:</gray> <white>" + connector.size() + "</white> <gray>Exit:</gray> <white>" + exit.size() + "</white> <gray>Boss:</gray> <white>" + boss.size() + "</white> <gray>Player spawn:</gray> <white>" + playerSpawn.size() + "</white>"));
        lore.add(Text.mm("<gray>Link mode:</gray> <white>Flexible</white>"));
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
    private String roomTypeName(List<Block> entrance, List<Block> connector, List<Block> exit, List<Block> boss, List<Block> playerSpawn) {
        if (!playerSpawn.isEmpty()) return "entrance";
        if (!boss.isEmpty()) return "boss";
        if (!exit.isEmpty()) return "exit";
        if (!entrance.isEmpty()) return "entrance";
        if (!connector.isEmpty()) return "connector";
        return "normal";
    }
    private boolean debugEnabled() { return configs.get("dungeons/dungeons.yml").getBoolean(DEBUG_PATH, true); }
    private void handleDungeonRoomProgress(Player player, Location to) {
        ActiveDungeonRun run = activeRuns.get(player.getUniqueId());
        if (run == null) return;
        List<PlacedRoom> rooms = activeLayouts.get(player.getUniqueId());
        if (rooms == null) return;
        for (PlacedRoom room : rooms) {
            if (!room.contains(to)) continue;
            if (room.role().equalsIgnoreCase("boss") && room.entered().add(player.getUniqueId())) spawnBossForRoom(player, room);
            if (room.role().equalsIgnoreCase("exit") && room.isInsideMarkerRegion(to, "exit")) {
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

    private boolean pasteStructure(String id, RoomReservation room) {
        String relative = configs.get("dungeons/templates.yml").getString("templates." + id + ".structure", "");
        if (relative == null || relative.isBlank()) return false;
        File file = new File(plugin.getDataFolder(), "dungeons/" + relative.replace('\\', '/').replaceFirst("^dungeons/", ""));
        if (!file.exists()) return false;
        try {
            Structure structure = Bukkit.getStructureManager().loadStructure(file);
            World world = Bukkit.getWorld(room.world());
            if (world == null) return false;
            structure.place(new Location(world, room.centerX(), room.y(), room.centerZ()), true, StructureRotation.NONE, Mirror.NONE, 0, 1.0F, new Random());
            return true;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[3SMPCore] Failed to paste dungeon structure " + file.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private void stripRuntimeMarkers(World world, RoomReservation room, int sizeX, int sizeY, int sizeZ) {
        int maxX = Math.min(MAX_SIZE, Math.max(1, sizeX));
        int maxY = Math.min(MAX_SIZE, Math.max(1, sizeY));
        int maxZ = Math.min(MAX_SIZE, Math.max(1, sizeZ));
        for (int x = 0; x < maxX; x++) {
            for (int y = 0; y < maxY; y++) {
                for (int z = 0; z < maxZ; z++) {
                    Block block = world.getBlockAt(room.centerX() + x, room.y() + y, room.centerZ() + z);
                    if (marker(block.getType()) != null) block.setType(Material.AIR, false);
                }
            }
        }
    }

    private File templateStructureFile(String safeId) {
        File dir = new File(plugin.getDataFolder(), "dungeons/templates");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, safeId + ".nbt");
    }
    private void spawnBossForRoom(Player player, PlacedRoom room) {
        List<SavedMarker> spawners = room.markers().stream().filter(marker -> marker.id().equalsIgnoreCase("boss_spawner")).toList();
        if (spawners.isEmpty()) return;
        String mythicMob = configs.get("dungeons/dungeons.yml").getString("boss.mythicmob", "DungeonBoss");
        String commandTemplate = configs.get("dungeons/dungeons.yml").getString("boss.spawn-command", "mm mobs spawn %mob% 1 %world%,%x%,%y%,%z%");
        for (SavedMarker marker : spawners) {
            Location spawn = marker.world(room).add(0.5D, 0.0D, 0.5D);
            boolean usedMythic = Bukkit.getPluginManager().getPlugin("MythicBukkit") != null || Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
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
        for (int i = 0; i < Math.min(plan.size(), placed.size()); i++) {
            String id = plan.get(i);
            RoomReservation room = placed.get(i);
            String base = "templates." + id;
            List<SavedMarker> markers = new ArrayList<>();
            for (String raw : yaml.getStringList(base + ".markers")) {
                SavedMarker parsed = parseMarker(raw);
                if (parsed != null) markers.add(parsed);
            }
            out.add(new PlacedRoom(room, id, yaml.getString(base + ".role", "normal"), yaml.getInt(base + ".size.x", 1), yaml.getInt(base + ".size.y", 1), yaml.getInt(base + ".size.z", 1), markers, new HashSet<>()));
        }
        return out;
    }
    private RoomReservation nextConnectedRoom(String key, RoomReservation previousRoom, String previousId, String nextId) {
        ConnectionPair pair = pickConnectionPair(previousId, nextId);
        int spacing = configs.get("dungeons/dungeons.yml").getInt("generation.spacing", 96);
        if (pair == null) return new RoomReservation(key, previousRoom.world(), previousRoom.level(), previousRoom.centerX() + spacing, previousRoom.y(), previousRoom.centerZ());
        SavedMarker previous = pair.previous();
        SavedMarker next = pair.next();
        int x = previousRoom.centerX() + previous.x() - next.x();
        int z = previousRoom.centerZ() + previous.z() - next.z();
        int y = previousRoom.y() + previous.y() - next.y() + verticalDelta(previous.id(), next.id());
        return new RoomReservation(key, previousRoom.world(), previousRoom.level(), x, y, z);
    }
    private int verticalDelta(String previous, String next) {
        if ("up".equalsIgnoreCase(previous) && "down".equalsIgnoreCase(next)) return 1;
        if ("down".equalsIgnoreCase(previous) && "up".equalsIgnoreCase(next)) return -1;
        return 0;
    }
    private List<String> expectedMarkersFor(String previousType) {
        if ("up".equalsIgnoreCase(previousType)) return List.of("down");
        if ("down".equalsIgnoreCase(previousType)) return List.of("up");
        return List.of("connector", "entrance", "boss", "exit", "up", "down");
    }
    private ConnectionPair pickConnectionPair(String previousId, String nextId) {
        List<SavedMarker> previousMarkers = markersFor(previousId).stream().filter(this::isConnectionMarker).toList();
        List<SavedMarker> nextMarkers = markersFor(nextId).stream().filter(this::isConnectionMarker).toList();
        List<ConnectionPair> vertical = new ArrayList<>();
        List<ConnectionPair> horizontal = new ArrayList<>();
        for (SavedMarker previous : previousMarkers) {
            for (SavedMarker next : nextMarkers) {
                if (("up".equalsIgnoreCase(previous.id()) && "down".equalsIgnoreCase(next.id())) || ("down".equalsIgnoreCase(previous.id()) && "up".equalsIgnoreCase(next.id()))) {
                    vertical.add(new ConnectionPair(previous, next));
                } else if (!isVerticalMarker(previous) && !isVerticalMarker(next)) {
                    horizontal.add(new ConnectionPair(previous, next));
                }
            }
        }
        List<ConnectionPair> pool = !vertical.isEmpty() ? vertical : horizontal;
        if (pool.isEmpty()) return null;
        return pool.get(new Random(Objects.hash(previousId, nextId, System.nanoTime())).nextInt(pool.size()));
    }
    private boolean isConnectionMarker(SavedMarker marker) {
        return List.of("connector", "entrance", "boss", "exit", "up", "down").stream().anyMatch(value -> value.equalsIgnoreCase(marker.id()));
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
        for (String raw : yaml.getStringList("templates." + templateId + ".markers")) {
            SavedMarker marker = parseMarker(raw);
            if (marker != null) out.add(marker);
        }
        return out;
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
    private record RoomReservation(String key,String world,String level,int centerX,int y,int centerZ){}
    private record ActiveDungeonRun(String level, String difficulty, boolean bossDefeated, int kills){}
    private record ConnectionPair(SavedMarker previous, SavedMarker next){}
    private record SavedMarker(int x, int y, int z, String id, String facing) {
        private Location world(PlacedRoom room) { return new Location(Bukkit.getWorld(room.room().world()), room.room().centerX() + x, room.room().y() + y, room.room().centerZ() + z); }
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
    private record DungeonHolder(String context) implements InventoryHolder { @Override public Inventory getInventory(){ return null; } }
    private static final class VoidChunkGenerator extends ChunkGenerator {}
}









