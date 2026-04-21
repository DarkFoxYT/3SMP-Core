package net.dark.threecore.dungeons;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
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
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class DungeonService implements Listener {
    private static final String ITEM_KEY = "3smpcore_dungeon_item";
    private static final String ITEM_ID = "dungeon_menu";
    private static final int MAX_SIZE = 64;
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MenuService menuService;
    private final Map<String, RoomReservation> reservations = new HashMap<>();

    public DungeonService(JavaPlugin plugin, ConfigFiles configs, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.menuService = menuService;
        loadReservations();
    }

    public void reload() { reservations.clear(); loadReservations(); }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) { if (sender instanceof Player player) openMenu(player); else Text.send(sender, "<red>Players only.</red>"); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "enter" -> { if (sender instanceof Player player) enter(player, args.length >= 2 ? args[1] : "jungle"); }
            case "leave" -> { if (sender instanceof Player player) player.performCommand("spawn"); }
            case "save" -> { if (sender instanceof Player player) saveTemplate(player, args.length >= 2 ? args[1] : "room_" + System.currentTimeMillis(), args.length >= 3 ? args[2] : "jungle"); }
            case "templates" -> listTemplates(sender);
            case "give" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { Text.send(sender, "<red>Usage: /dungeon give <player></red>"); return; } Player target = Bukkit.getPlayerExact(args[1]); if (target != null) giveItem(target); }
            case "reload" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reload(); Text.send(sender, "<green>Dungeons reloaded.</green>"); }
            case "clear" -> { if (!sender.hasPermission("3smpcore.dungeons.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } reservations.clear(); saveReservations(); Text.send(sender, "<yellow>Dungeon room reservations cleared.</yellow>"); }
            default -> Text.send(sender, "<gray>/dungeon menu|enter [level]|save <id> [level]|templates|leave|give <player>|reload|clear</gray>");
        }
    }

    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("menu", "enter", "save", "templates", "leave", "give", "reload", "clear") : levelIds(); }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new DungeonHolder(), 45, "3SMP Dungeons");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(4, button(Material.SCULK_SHRIEKER, "<gradient:#4c1d95:#a78bfa>Dungeons</gradient>", List.of("<gray>Premade room chains, party-scaled.</gray>", "<gray>Rooms are generated from saved templates.</gray>")));
        int slot = 19;
        for (String level : levelIds()) {
            boolean comingSoon = configs.get("dungeons.yml").getBoolean("levels." + level + ".coming-soon", false);
            inv.setItem(slot, button(comingSoon ? Material.BARRIER : Material.SHULKER_BOX, configs.get("dungeons.yml").getString("levels." + level + ".display-name", level), List.of(comingSoon ? "<red>Coming soon.</red>" : "<gray>Click to enter.</gray>", "<gray>Theme level:</gray> <white>" + level + "</white>")));
            slot += 2; if (slot > 25) break;
        }
        player.openInventory(inv);
    }

    public void enter(Player player, String level) {
        String id = level.toLowerCase(Locale.ROOT);
        if (!configs.get("dungeons.yml").isConfigurationSection("levels." + id)) id = "jungle";
        if (configs.get("dungeons.yml").getBoolean("levels." + id + ".coming-soon", false)) { Text.send(player, "<red>That dungeon level is coming soon.</red>"); return; }
        World world = dungeonWorld(); if (world == null) { Text.send(player, "<red>Dungeon world could not be loaded.</red>"); return; }
        String template = firstTemplate(id);
        if (template == null) { Text.send(player, "<red>No saved room templates for level " + id + ". Use /d save <id> " + id + ".</red>"); return; }
        RoomReservation room = allocate(player.getUniqueId(), id, world);
        pasteTemplate(template, room);
        Location spawn = markerSpawn(template, room);
        player.teleport(spawn == null ? new Location(world, room.centerX() + 0.5, room.y() + 2, room.centerZ() + 0.5) : spawn);
        Text.send(player, "<green>Entered " + id + " dungeon.</green>");
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
        YamlConfiguration yaml = configs.get("dungeon_templates.yml");
        String path = "templates." + id.toLowerCase(Locale.ROOT);
        yaml.set(path, null); yaml.set(path + ".level", level.toLowerCase(Locale.ROOT)); yaml.set(path + ".size.x", maxX-minX+1); yaml.set(path + ".size.y", maxY-minY+1); yaml.set(path + ".size.z", maxZ-minZ+1);
        List<String> blocks = new ArrayList<>(); List<String> markers = new ArrayList<>();
        for (int x=minX;x<=maxX;x++) for (int y=minY;y<=maxY;y++) for (int z=minZ;z<=maxZ;z++) {
            Block block = player.getWorld().getBlockAt(x,y,z); Material type = block.getType(); if (type.isAir()) continue;
            String rel = (x-minX)+","+(y-minY)+","+(z-minZ);
            Marker marker = marker(type); if (marker != null) { markers.add(rel+":"+marker.id()+":"+facing(block)); continue; }
            blocks.add(rel+":"+type.name());
        }
        yaml.set(path + ".blocks", blocks); yaml.set(path + ".markers", markers);
        try { yaml.save(new File(plugin.getDataFolder(), "dungeon_templates.yml")); } catch (Exception ignored) {}
        Text.send(player, "<green>Saved dungeon room template " + id + " with " + blocks.size() + " blocks and " + markers.size() + " markers.</green>");
    }

    private void pasteTemplate(String id, RoomReservation room) {
        World world = Bukkit.getWorld(room.world()); if (world == null) return;
        var yaml = configs.get("dungeon_templates.yml");
        for (String entry : yaml.getStringList("templates." + id + ".blocks")) {
            String[] p = entry.split(":"); if (p.length < 2) continue; String[] xyz = p[0].split(",");
            Material mat = parseMaterial(p[1]); world.getBlockAt(room.centerX()+Integer.parseInt(xyz[0]), room.y()+Integer.parseInt(xyz[1]), room.centerZ()+Integer.parseInt(xyz[2])).setType(mat, false);
        }
    }

    private Location markerSpawn(String id, RoomReservation room) {
        var yaml = configs.get("dungeon_templates.yml"); World world = Bukkit.getWorld(room.world()); if (world == null) return null;
        for (String entry : yaml.getStringList("templates." + id + ".markers")) if (entry.contains(":player_spawn:")) { String[] xyz = entry.split(":")[0].split(","); return new Location(world, room.centerX()+Integer.parseInt(xyz[0])+0.5, room.y()+Integer.parseInt(xyz[1])+1, room.centerZ()+Integer.parseInt(xyz[2])+0.5); }
        return null;
    }

    public void giveItem(Player player) { int slot = Math.max(0, Math.min(8, configs.get("dungeons.yml").getInt("item.slot", 1))); clearItem(player); player.getInventory().setItem(slot, item()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { giveItem(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { Bukkit.getScheduler().runTask(plugin, () -> giveItem(event.getPlayer())); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) { giveItem(event.getPlayer()); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if ((event.getAction()==Action.RIGHT_CLICK_AIR || event.getAction()==Action.RIGHT_CLICK_BLOCK) && isItem(event.getItem())) { event.setCancelled(true); openMenu(event.getPlayer()); } }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (isItem(event.getItemDrop().getItemStack())) event.setCancelled(true); }
    @EventHandler public void onClick(InventoryClickEvent event) { if (isItem(event.getCurrentItem())) { event.setCancelled(true); if (event.getWhoClicked() instanceof Player p && event.getClickedInventory()==p.getInventory()) openMenu(p); return; } if (!(event.getInventory().getHolder() instanceof DungeonHolder)) return; event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player p)) return; int index=(event.getRawSlot()-19)/2; if(event.getRawSlot()<19||event.getRawSlot()>25||event.getRawSlot()%2==0)return; List<String> levels=levelIds(); if(index>=0&&index<levels.size()) enter(p, levels.get(index)); }

    private List<Block> nearby(Player player, Material material) { List<Block> out = new ArrayList<>(); Location c = player.getLocation(); int r = 80; for (int x=c.getBlockX()-r;x<=c.getBlockX()+r;x++) for(int y=Math.max(player.getWorld().getMinHeight(), c.getBlockY()-r);y<=Math.min(player.getWorld().getMaxHeight()-1,c.getBlockY()+r);y++) for(int z=c.getBlockZ()-r;z<=c.getBlockZ()+r;z++) { Block b=player.getWorld().getBlockAt(x,y,z); if(b.getType()==material) out.add(b); } return out; }
    private Marker marker(Material m) { return switch(m) { case YELLOW_SHULKER_BOX -> new Marker("entrance"); case ORANGE_SHULKER_BOX -> new Marker("connector"); case LIGHT_BLUE_SHULKER_BOX -> new Marker("enemy_spawn"); case RED_SHULKER_BOX -> new Marker("exit"); case PURPLE_SHULKER_BOX -> new Marker("trigger"); case GREEN_SHULKER_BOX -> new Marker("player_spawn"); case BLACK_SHULKER_BOX -> new Marker("boss"); default -> null; }; }
    private String facing(Block b) { return b.getBlockData() instanceof Directional d ? d.getFacing().name() : "UP"; }
    private String firstTemplate(String level) { var sec=configs.get("dungeon_templates.yml").getConfigurationSection("templates"); if(sec==null)return null; for(String id:sec.getKeys(false)) if(level.equalsIgnoreCase(sec.getString(id+".level"))) return id; return null; }
    private void listTemplates(CommandSender s) { var sec=configs.get("dungeon_templates.yml").getConfigurationSection("templates"); Text.send(s, "<gray>Templates: " + (sec==null?"none":String.join(", ",sec.getKeys(false))) + "</gray>"); }
    private RoomReservation allocate(UUID uuid, String level, World world) { String key=uuid+":"+level; if(reservations.containsKey(key))return reservations.get(key); int spacing=configs.get("dungeons.yml").getInt("generation.spacing",96); int y=configs.get("dungeons.yml").getInt("levels."+level+".y",80); int idx=reservations.size(); int cols=Math.max(1, configs.get("dungeons.yml").getInt("generation.columns",8)); RoomReservation r=new RoomReservation(key,world.getName(),level,(idx%cols)*spacing,y,(idx/cols)*spacing); reservations.put(key,r); saveReservations(); return r; }
    private World dungeonWorld(){ String name=configs.get("dungeons.yml").getString("world","dungeons"); World w=Bukkit.getWorld(name); return w==null?Bukkit.createWorld(new WorldCreator(name)):w; }
    private void clearItem(Player player){ for(int i=0;i<player.getInventory().getSize();i++) if(isItem(player.getInventory().getItem(i))) player.getInventory().setItem(i,null); }
    private ItemStack item(){ return tagged(Material.SCULK_SHRIEKER, configs.get("dungeons.yml").getString("item.name","<gradient:#4c1d95:#a78bfa>Dungeons</gradient>")); }
    private ItemStack tagged(Material mat,String name){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm(name)); m.lore(List.of(Text.mm("<gray>Click to open dungeons.</gray>"))); m.getPersistentDataContainer().set(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING, ITEM_ID); s.setItemMeta(m); return s; }
    private boolean isItem(ItemStack i){ return i!=null&&i.hasItemMeta()&&ITEM_ID.equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,ITEM_KEY), PersistentDataType.STRING)); }
    private ItemStack pane(){ return button(Material.GRAY_STAINED_GLASS_PANE," ",List.of()); }
    private ItemStack button(Material mat,String name,List<String> lore){ ItemStack s=new ItemStack(mat); ItemMeta m=s.getItemMeta(); m.displayName(Text.mm(name)); m.lore(lore.stream().map(Text::mm).toList()); s.setItemMeta(m); return s; }
    private Material parseMaterial(String in){ try{return Material.valueOf(in.toUpperCase(Locale.ROOT));}catch(Exception e){return Material.STONE;} }
    private List<String> levelIds(){ var sec=configs.get("dungeons.yml").getConfigurationSection("levels"); return sec==null?List.of("jungle"):List.copyOf(sec.getKeys(false)); }
    private void loadReservations(){ var sec=configs.get("dungeon_rooms.yml").getConfigurationSection("rooms"); if(sec==null)return; for(String k:sec.getKeys(false)) reservations.put(k,new RoomReservation(k,sec.getString(k+".world"),sec.getString(k+".level"),sec.getInt(k+".x"),sec.getInt(k+".y"),sec.getInt(k+".z"))); }
    private void saveReservations(){ var y=configs.get("dungeon_rooms.yml"); y.set("rooms",null); for(RoomReservation r:reservations.values()){String p="rooms."+r.key(); y.set(p+".world",r.world()); y.set(p+".level",r.level()); y.set(p+".x",r.centerX()); y.set(p+".y",r.y()); y.set(p+".z",r.centerZ());} try{y.save(new File(plugin.getDataFolder(),"dungeon_rooms.yml"));}catch(Exception ignored){} }
    private record Marker(String id){}
    private record RoomReservation(String key,String world,String level,int centerX,int y,int centerZ){}
    private static final class DungeonHolder implements InventoryHolder { @Override public Inventory getInventory(){ return null; } }
}
