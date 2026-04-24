package net.dark.threecore.dungeons;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.survival.SurvivalService;
import net.dark.threecore.text.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.*;

public final class DungeonService implements Listener {
    private static final String ITEM_KEY = "3smpcore_dungeon_item";
    private static final String ITEM_ID = "dungeon_menu";
    private static final String DEV_TOOL_ID = "dungeon_dev_save";
    private static final String DEV_MARKER_ID = "dungeon_marker";
    private static final int DEV_TOOL_SLOT = 8;
    private static final int MAX_SIZE = 64;
    private static final Set<UUID> ACTIVE_DUNGEON_PLAYERS = new HashSet<>();
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
            case "spawn" -> { if (sender instanceof Player player) teleportDungeonSpawn(player); }
            case "setspawn" -> { if (sender instanceof Player player) setDungeonSpawn(player); }
            case "dev", "editor" -> { if (sender instanceof Player player) openDungeonEditor(player); }
            case "leave" -> { if (sender instanceof Player player) leave(player); }
            case "save" -> { if (sender instanceof Player player) saveTemplate(player, args.length >= 2 ? args[1] : "room_" + System.currentTimeMillis(), args.length >= 3 ? args[2] : "jungle"); }
            case "templates" -> listTemplates(sender);
            case "give" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { Text.send(sender, "<red>Usage: /dungeon give <player></red>"); return; } Player target = Bukkit.getPlayerExact(args[1]); if (target != null) giveItem(target); }
            case "reload" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reload(); Text.send(sender, "<green>Dungeons reloaded.</green>"); }
            case "clear" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reservations.clear(); saveReservations(); Text.send(sender, "<yellow>Dungeon room reservations cleared.</yellow>"); }
            default -> Text.send(sender, "<gray>/dungeon menu|spawn|enter [level]|save <id> [level]|dev|templates|leave|give <player>|reload|clear</gray>");
        }
    }

    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("menu", "spawn", "setspawn", "enter", "save", "dev", "editor", "templates", "leave", "give", "reload", "clear") : levelIds(); }

    private void teleportDungeonSpawn(Player player) {
        Location spawn = readConfiguredLocation("spawn");
        if (spawn == null) { Text.send(player, "<red>Dungeon spawn is not configured.</red>"); return; }
        if (survivalService != null) survivalService.saveCurrentProfile(player);
        clearPlayerState(player);
        player.teleport(spawn);
        loadDungeonProfile(player);
        Text.send(player, "<gradient:#4c1d95:#a78bfa>Sent to dungeon spawn.</gradient>");
    }

    private void setDungeonSpawn(Player player) {
        if (!player.hasPermission("3smpcore.dungeons.admin")) { Text.send(player, "<red>No permission.</red>"); return; }
        var yaml = configs.get("dungeons/dungeons.yml");
        writeLocation(yaml, "spawn", player.getLocation());
        try { yaml.save(new File(plugin.getDataFolder(), "dungeons/dungeons.yml")); Text.send(player, "<green>Dungeon spawn saved.</green>"); } catch (Exception ex) { Text.send(player, "<red>Failed to save dungeon spawn.</red>"); }
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
        player.teleport(new Location(world, 0.5D, world.getHighestBlockYAt(0, 0) + 2.0D, 0.5D));
        loadDungeonProfile(player);
        giveDevTool(player);
        openDevToolbox(player);
        Text.send(player, "<gradient:#4c1d95:#a78bfa>Dungeon editor opened.</gradient>");
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
        player.performCommand("spawn");
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
        player.teleport(spawn == null ? new Location(world, start.centerX() + 0.5, start.y() + 2, start.centerZ() + 0.5) : spawn);
        clearPlayerState(player);
        applyBaseKit(player);
        activeRuns.put(player.getUniqueId(), new ActiveDungeonRun(id, options(player).difficulty()[0], false, 0));
        ACTIVE_DUNGEON_PLAYERS.add(player.getUniqueId());
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
        inv.setItem(14, devMarker(Material.LIGHT_BLUE_SHULKER_BOX, "Enemy Spawn"));
        inv.setItem(15, devMarker(Material.RED_SHULKER_BOX, "Exit"));
        inv.setItem(16, devMarker(Material.BLACK_SHULKER_BOX, "Boss"));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        player.openInventory(inv);
    }

    private void handleDevToolboxClick(Player player, int raw) {
        Map<Integer, Material> markers = Map.of(10, Material.WHITE_SHULKER_BOX, 11, Material.GREEN_SHULKER_BOX, 12, Material.YELLOW_SHULKER_BOX, 13, Material.ORANGE_SHULKER_BOX, 14, Material.LIGHT_BLUE_SHULKER_BOX, 15, Material.RED_SHULKER_BOX, 16, Material.BLACK_SHULKER_BOX);
        Material material = markers.get(raw);
        if (material == null) return;
        player.getInventory().addItem(devMarker(material, material.name()));
        Text.actionBar(player, "<gray>Added dungeon marker:</gray> <white>" + material.name() + "</white>");
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
        List<String> blocks = new ArrayList<>(); List<String> markers = new ArrayList<>();
        for (int x=minX;x<=maxX;x++) for (int y=minY;y<=maxY;y++) for (int z=minZ;z<=maxZ;z++) {
            Block block = player.getWorld().getBlockAt(x,y,z); Material type = block.getType(); if (type.isAir()) continue;
            String rel = (x-minX)+","+(y-minY)+","+(z-minZ);
            Marker marker = marker(type); if (marker != null) { markers.add(rel+":"+marker.id()+":"+facing(block)); continue; }
            blocks.add(rel+":"+type.name());
        }
        yaml.set(path + ".role", detectRoomRole(markers));
        yaml.set(path + ".size-category", sizeCategory(maxX-minX+1, maxY-minY+1, maxZ-minZ+1));
        yaml.set(path + ".blocks", blocks); yaml.set(path + ".markers", markers);
        try { yaml.save(new File(plugin.getDataFolder(), "dungeons/templates.yml")); } catch (Exception ignored) {}
        Text.send(player, "<green>Saved dungeon room template " + id + " with " + blocks.size() + " blocks and " + markers.size() + " markers.</green>");
    }

    private void pasteTemplate(String id, RoomReservation room) {
        World world = Bukkit.getWorld(room.world()); if (world == null) return;
        var yaml = configs.get("dungeons/templates.yml");
        clearTemplateArea(world, room, yaml.getInt("templates." + id + ".size.x", MAX_SIZE), yaml.getInt("templates." + id + ".size.y", MAX_SIZE), yaml.getInt("templates." + id + ".size.z", MAX_SIZE));
        for (String entry : yaml.getStringList("templates." + id + ".blocks")) {
            String[] p = entry.split(":"); if (p.length < 2) continue; String[] xyz = p[0].split(",");
            Material mat = parseMaterial(p[1]); world.getBlockAt(room.centerX()+Integer.parseInt(xyz[0]), room.y()+Integer.parseInt(xyz[1]), room.centerZ()+Integer.parseInt(xyz[2])).setType(mat, false);
        }
    }

    private void clearTemplateArea(World world, RoomReservation room, int sizeX, int sizeY, int sizeZ) {
        if (!configs.get("dungeons/dungeons.yml").getBoolean("generation.reset-before-paste", true)) return;
        int maxX = Math.min(MAX_SIZE, Math.max(1, sizeX));
        int maxY = Math.min(MAX_SIZE, Math.max(1, sizeY));
        int maxZ = Math.min(MAX_SIZE, Math.max(1, sizeZ));
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
    }
    private Location markerSpawn(String id, RoomReservation room) {
        var yaml = configs.get("dungeons/templates.yml"); World world = Bukkit.getWorld(room.world()); if (world == null) return null;
        for (String entry : yaml.getStringList("templates." + id + ".markers")) if (entry.contains(":player_spawn:")) { String[] xyz = entry.split(":")[0].split(","); return new Location(world, room.centerX()+Integer.parseInt(xyz[0])+0.5, room.y()+Integer.parseInt(xyz[1])+1, room.centerZ()+Integer.parseInt(xyz[2])+0.5); }
        return null;
    }

    public void giveItem(Player player) { if (net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || net.dark.threecore.duels.DuelService.isDuelPlayer(player) || isDungeonWorld(player.getWorld()) || !isSpawnWorld(player.getWorld())) { clearItem(player); return; } int slot = Math.max(0, Math.min(8, configs.get("dungeons/dungeons.yml").getInt("item.slot", 1))); clearItem(player); player.getInventory().setItem(slot, item()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { giveItem(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, () -> giveItem(event.getPlayer())); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) { if (isDungeonWorld(event.getFrom())) saveDungeonProfile(event.getPlayer()); if (isDungeonWorld(event.getPlayer().getWorld())) loadDungeonProfile(event.getPlayer()); giveItem(event.getPlayer()); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (event.getAction()!=Action.RIGHT_CLICK_AIR && event.getAction()!=Action.RIGHT_CLICK_BLOCK) return; if (isDevTool(event.getItem())) { event.setCancelled(true); if (event.getPlayer().isSneaking()) openDevToolbox(event.getPlayer()); else saveTemplate(event.getPlayer(), "room_" + System.currentTimeMillis(), options(event.getPlayer()).level()); return; } if (isDevMarker(event.getItem())) { event.setCancelled(true); placeDevMarker(event.getPlayer(), event.getItem().getType()); return; } if (isItem(event.getItem())) { event.setCancelled(true); teleportDungeonSpawn(event.getPlayer()); } }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (isItem(event.getItemDrop().getItemStack()) || isDevTool(event.getItemDrop().getItemStack()) || isDevMarker(event.getItemDrop().getItemStack())) { event.setCancelled(true); if (isDungeonEditorWorld(event.getPlayer().getWorld())) Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(event.getPlayer())); } }
    @EventHandler public void onClick(InventoryClickEvent event) { if (event.getInventory().getHolder() instanceof DungeonHolder holder) { event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player p)) return; DungeonRunOptions options = options(p); int raw = event.getRawSlot(); if (raw == 22) { openMenu(p); return; } switch (holder.context()) { case "main" -> { if (raw == 10) openLevelMenu(p); else if (raw == 12) openDifficultyMenu(p); else if (raw == 14) openPartyMenu(p); else if (raw == 16) enter(p, options.level()); } case "levels" -> { int[] slots={10,11,12,13,14}; int idx=-1; for(int i=0;i<slots.length;i++) if(slots[i]==raw) idx=i; List<String> levels=levelIds(); if(idx>=0&&idx<levels.size()){String level=levels.get(idx); if(!configs.get("dungeons/dungeons.yml").getBoolean("levels."+level+".coming-soon",false)) options.level(level); openMenu(p);} } case "difficulty" -> { if(raw==10) options.difficulty()[0]="easy"; else if(raw==12) options.difficulty()[0]="normal"; else if(raw==14) options.difficulty()[0]="hard"; else if(raw==16) { if (canUseNightmare(p, options.level())) options.difficulty()[0]="nightmare"; else Text.send(p, "<red>Beat Easy, Normal and Hard on this level before Nightmare.</red>"); } openMenu(p); } case "party" -> { if(raw==11) options.party()[0]=false; else if(raw==15 && partyService!=null && partyService.isInParty(p.getUniqueId())) options.party()[0]=true; openMenu(p); } case "dev-toolbox" -> handleDevToolboxClick(p, raw); default -> { } } return; } if (isItem(event.getCurrentItem())) { event.setCancelled(true); clearCursor(event); if (event.getWhoClicked() instanceof Player p && event.getClickedInventory()==p.getInventory()) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!p.isOnline()) return; p.setItemOnCursor(null); p.updateInventory(); teleportDungeonSpawn(p); }, 20L); return; } if (event.getWhoClicked() instanceof Player p && isDungeonEditorWorld(p.getWorld()) && (isDevTool(event.getCurrentItem()) || isDevMarker(event.getCurrentItem()) || isDevTool(event.getCursor()) || isDevMarker(event.getCursor()))) { event.setCancelled(true); clearCursor(event); Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(p)); } }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (!(event.getWhoClicked() instanceof Player player) || !isDungeonEditorWorld(player.getWorld())) return; if (isDevTool(event.getOldCursor()) || isDevMarker(event.getOldCursor())) { event.setCancelled(true); Bukkit.getScheduler().runTask(plugin, () -> enforceEditorToolbar(player)); } }
    @EventHandler public void onMobDeath(EntityDeathEvent event) { if (event.getEntity().getKiller() == null) return; Player killer = event.getEntity().getKiller(); ActiveDungeonRun run = activeRuns.get(killer.getUniqueId()); if (run == null) return; int kills = run.kills() + 1; boolean bossKill = event.getEntity().getPersistentDataContainer().has(new NamespacedKey(plugin, "dungeon_boss")); boolean firstBossKill = bossKill && !run.bossDefeated(); boolean bossDefeated = run.bossDefeated() || bossKill; activeRuns.put(killer.getUniqueId(), new ActiveDungeonRun(run.level(), run.difficulty(), bossDefeated, kills)); double amount = moneyPerKill(run.difficulty()); repository.setMoneyBalance(killer.getUniqueId(), repository.getMoneyBalance(killer.getUniqueId()) + amount); if (firstBossKill) rewardBossClear(killer, run); Text.actionBar(killer, "<gradient:#4c1d95:#a78bfa>Dungeon kill</gradient> <gray>+ $" + amount + "</gray>" + (bossDefeated ? " <green>Boss defeated. Exit room unlocked.</green>" : "")); }

    @EventHandler public void onPlayerDeath(PlayerDeathEvent event) {
        ACTIVE_DUNGEON_PLAYERS.remove(event.getEntity().getUniqueId());
        ActiveDungeonRun run = activeRuns.remove(event.getEntity().getUniqueId());
        if (run == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> event.getEntity().performCommand("spawn"));
        if (socialTabService != null) socialTabService.refreshAll();
        if (run.difficulty().equalsIgnoreCase("nightmare")) Text.send(event.getEntity(), "<gradient:#7f1d1d:#020617>Nightmare failed.</gradient> <gray>Your run has ended.</gray>");
        else Text.send(event.getEntity(), "<red>Dungeon run failed.</red>");
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (isDungeonWorld(event.getPlayer().getWorld())) saveDungeonProfile(event.getPlayer());
        activeRuns.remove(event.getPlayer().getUniqueId());
        ACTIVE_DUNGEON_PLAYERS.remove(event.getPlayer().getUniqueId());
        activeGroups.remove(event.getPlayer().getUniqueId());
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
        int maxMiddle = Math.min(target, normal.size());
        List<String> shuffled = new ArrayList<>(normal); Collections.shuffle(shuffled);
        List<String> plan = new ArrayList<>();
        plan.add(entrance.get(new Random().nextInt(entrance.size())));
        for (int i = 0; i < maxMiddle; i++) {
            String candidate = shuffled.get(i);
            if (!plan.contains(candidate)) plan.add(candidate);
        }
        if (!boss.isEmpty()) plan.add(boss.get(new Random().nextInt(boss.size())));
        if (!exit.isEmpty()) plan.add(exit.get(new Random().nextInt(exit.size())));
        return plan;
    }

    private List<RoomReservation> pasteRoomPlan(List<String> plan, RoomReservation start) {
        List<RoomReservation> placed = new ArrayList<>();
        int spacing = configs.get("dungeons/dungeons.yml").getInt("generation.spacing", 96);
        for (int i = 0; i < plan.size(); i++) {
            RoomReservation room = new RoomReservation(start.key() + ":" + i, start.world(), start.level(), start.centerX() + i * spacing, start.y(), start.centerZ());
            pasteTemplate(plan.get(i), room);
            placed.add(room);
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
    private Marker marker(Material m) { return switch(m) { case YELLOW_SHULKER_BOX -> new Marker("entrance"); case ORANGE_SHULKER_BOX -> new Marker("connector"); case LIGHT_BLUE_SHULKER_BOX -> new Marker("enemy_spawn"); case RED_SHULKER_BOX -> new Marker("exit"); case PURPLE_SHULKER_BOX -> new Marker("trigger"); case GREEN_SHULKER_BOX -> new Marker("player_spawn"); case BLACK_SHULKER_BOX -> new Marker("boss"); default -> null; }; }
    private String facing(Block b) { return b.getBlockData() instanceof Directional d ? d.getFacing().name() : "UP"; }
    private String firstTemplate(String level) { var sec=configs.get("dungeons/templates.yml").getConfigurationSection("templates"); if(sec==null)return null; for(String id:sec.getKeys(false)) if(level.equalsIgnoreCase(sec.getString(id+".level"))) return id; return null; }
    private void listTemplates(CommandSender s) { var sec=configs.get("dungeons/templates.yml").getConfigurationSection("templates"); Text.send(s, "<gray>Templates: " + (sec==null?"none":String.join(", ",sec.getKeys(false))) + "</gray>"); }
    private RoomReservation allocate(UUID uuid, String level, World world) { String key=uuid+":"+level; if(reservations.containsKey(key))return reservations.get(key); int spacing=configs.get("dungeons/dungeons.yml").getInt("generation.spacing",96); int y=configs.get("dungeons/dungeons.yml").getInt("levels."+level+".y",80); int idx=reservations.size(); int cols=Math.max(1, configs.get("dungeons/dungeons.yml").getInt("generation.columns",8)); RoomReservation r=new RoomReservation(key,world.getName(),level,(idx%cols)*spacing,y,(idx/cols)*spacing); reservations.put(key,r); saveReservations(); return r; }
    private World dungeonWorld(){ return loadDungeonWorld(configs.get("dungeons/dungeons.yml").getString("world","dungeons")); }
    private World dungeonEditorWorld(){ return loadDungeonWorld(configs.get("dungeons/dungeons.yml").getString("editor-world","dungeons_editor")); }
    private World loadDungeonWorld(String name){ World w=Bukkit.getWorld(name); if(w!=null){applyDungeonWorldRules(w); return w;} WorldCreator creator=new WorldCreator(name); creator.environment(World.Environment.NORMAL); creator.generateStructures(false); String generator=configs.get("dungeons/dungeons.yml").getString("world-generation.generator", ""); if(generator!=null&&!generator.isBlank()&&Bukkit.getPluginManager().getPlugin(generator) != null) creator.generator(generator); else creator.generator(new VoidChunkGenerator()); World created=Bukkit.createWorld(creator); applyDungeonWorldRules(created); if(created!=null&&configs.get("dungeons/dungeons.yml").getBoolean("world-generation.multiverse", true)&&Bukkit.getPluginManager().getPlugin("Multiverse-Core")!=null){Bukkit.getScheduler().runTask(plugin,()->Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv import "+created.getName()+" normal"));} return created; }
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
    private Location readConfiguredLocation(String path) { var s = configs.get("dungeons/dungeons.yml").getConfigurationSection(path); if (s == null) return null; World w = Bukkit.getWorld(s.getString("world", configs.get("dungeons/dungeons.yml").getString("world", "dungeons"))); return w == null ? null : new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")); }
    private void writeLocation(org.bukkit.configuration.file.YamlConfiguration y, String p, Location l) { y.set(p+".world", l.getWorld().getName()); y.set(p+".x", l.getX()); y.set(p+".y", l.getY()); y.set(p+".z", l.getZ()); y.set(p+".yaw", l.getYaw()); y.set(p+".pitch", l.getPitch()); }
    private List<String> levelIds(){ var sec=configs.get("dungeons/dungeons.yml").getConfigurationSection("levels"); return sec==null?List.of("jungle"):List.copyOf(sec.getKeys(false)); }
    private void loadReservations(){ var sec=configs.get("dungeons/rooms.yml").getConfigurationSection("rooms"); if(sec==null)return; for(String k:sec.getKeys(false)) reservations.put(k,new RoomReservation(k,sec.getString(k+".world"),sec.getString(k+".level"),sec.getInt(k+".x"),sec.getInt(k+".y"),sec.getInt(k+".z"))); }
    private void saveReservations(){ var y=configs.get("dungeons/rooms.yml"); y.set("rooms",null); for(RoomReservation r:reservations.values()){String p="rooms."+r.key(); y.set(p+".world",r.world()); y.set(p+".level",r.level()); y.set(p+".x",r.centerX()); y.set(p+".y",r.y()); y.set(p+".z",r.centerZ());} try{y.save(new File(plugin.getDataFolder(),"dungeons/rooms.yml"));}catch(Exception ignored){} }
    public boolean isDungeonWorld(World world){ return world != null && (world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("world","dungeons")) || world.getName().equalsIgnoreCase(configs.get("dungeons/dungeons.yml").getString("editor-world","dungeons_editor"))); }
    private boolean isSpawnWorld(World world){ return world != null && world.getName().equalsIgnoreCase(configs.get("core/config.yml").getString("spawn.world","spawn")); }
    private void saveDungeonProfile(Player player){ repository.saveInventoryProfile(player.getUniqueId(), "dungeon", player.getInventory().getContents(), player.getInventory().getArmorContents(), player.getInventory().getItemInOffHand()); }
    private void loadDungeonProfile(Player player){ var data = repository.loadInventoryProfile(player.getUniqueId(), "dungeon"); clearPlayerState(player); player.getInventory().setContents(data.contents()); player.getInventory().setArmorContents(data.armor()); player.getInventory().setItemInOffHand(data.offhand() == null ? new ItemStack(Material.AIR) : data.offhand()); player.updateInventory(); }
    private record Marker(String id){}
    private record RoomReservation(String key,String world,String level,int centerX,int y,int centerZ){}
    private record ActiveDungeonRun(String level, String difficulty, boolean bossDefeated, int kills){}
    private static final class DungeonRunOptions { private String level; private final boolean[] party; private final String[] difficulty; private DungeonRunOptions(String level, boolean[] party, String[] difficulty){this.level=level;this.party=party;this.difficulty=difficulty;} private String level(){return level;} private void level(String level){this.level=level;} private boolean[] party(){return party;} private String[] difficulty(){return difficulty;} }
    private record DungeonHolder(String context) implements InventoryHolder { @Override public Inventory getInventory(){ return null; } }
    private static final class VoidChunkGenerator extends ChunkGenerator {}
}









