package net.dark.threecore.warp;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.Database;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class WarpManager {
    private static final String ITEM_KEY = "3smpcore_warp_icon";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Database database;
    public WarpManager(JavaPlugin plugin, ConfigFiles configs, Database database) { this.plugin = plugin; this.configs = configs; this.database = database; }
    public void reload() { }
    public void open(Player player) { player.openInventory(buildMenu(player)); }
    public org.bukkit.inventory.Inventory buildMenu(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.DUEL_DEV, "warps"), 54, "3SMP Warps");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane());
        int slot = 10;
        for (Warp warp : listWarps()) {
            inv.setItem(slot++, icon(warp, player));
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
        }
        return inv;
    }
    public void handleClick(Player player, int slot) {
        int index = slotIndex(slot);
        List<Warp> warps = listWarps();
        if (index >= 0 && index < warps.size()) teleport(player, warps.get(index).id());
    }
    public void setWarp(CommandSender sender, String id, Location location) {
        YamlConfiguration yaml = configs.get("world/warps.yml");
        String path = "warps." + id.toLowerCase(Locale.ROOT);
        yaml.set(path + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
        yaml.set(path + ".x", location.getX()); yaml.set(path + ".y", location.getY()); yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw()); yaml.set(path + ".pitch", location.getPitch());
        yaml.set(path + ".display-name", yaml.getString(path + ".display-name", id));
        yaml.set(path + ".icon", yaml.getString(path + ".icon", "ENDER_PEARL"));
        try { yaml.save(new java.io.File(plugin.getDataFolder(), "world/warps.yml")); } catch (Exception ignored) {}
        Text.send(sender, "<green>Warp saved.</green>");
    }
    public boolean teleport(Player player, String id) { Warp warp = warp(id); if (warp == null || warp.location() == null) { Text.send(player, "<red>Warp not found.</red>"); return false; } player.teleport(warp.location()); return true; }
    public List<String> ids() { List<String> ids = new ArrayList<>(); for (Warp warp : listWarps()) ids.add(warp.id()); return ids; }
    public Warp warp(String id) { for (Warp warp : listWarps()) if (warp.id().equalsIgnoreCase(id)) return warp; return null; }
    public List<Warp> listWarps() { List<Warp> warps = new ArrayList<>(); YamlConfiguration yaml = configs.get("world/warps.yml"); ConfigurationSection root = yaml.getConfigurationSection("warps"); if (root == null) return warps; for (String id : root.getKeys(false)) { ConfigurationSection sec = root.getConfigurationSection(id); if (sec == null) continue; warps.add(new Warp(id, sec.getString("display-name", id), parseMaterial(sec.getString("icon", "ENDER_PEARL")), readLocation(sec))); } return warps; }
    private ItemStack icon(Warp warp, Player player) { ItemStack stack = warp.icon() != null ? warp.icon() : new ItemStack(Material.ENDER_PEARL); ItemMeta meta = stack.getItemMeta(); meta.displayName(Text.mm(warp.displayName())); meta.lore(List.of(Text.mm("<gray>Click to warp.</gray>"))); stack.setItemMeta(meta); return stack; }
    private ItemStack pane() { ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta meta = stack.getItemMeta(); meta.displayName(Text.mm(" ")); stack.setItemMeta(meta); return stack; }
    private int slotIndex(int slot) { if (slot < 10 || slot > 44) return -1; int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43}; for (int i=0;i<slots.length;i++) if (slots[i]==slot) return i; return -1; }
    private Material parseMaterial(String input) { try { return Material.valueOf(input.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return Material.ENDER_PEARL; } }
    private Location readLocation(ConfigurationSection sec) { if (sec == null) return null; var world = Bukkit.getWorld(sec.getString("world", "world")); if (world == null) return null; return new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"), (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch")); }
    private record Warp(String id, String displayName, Material iconMaterial, Location location) { ItemStack icon() { return new ItemStack(iconMaterial); } }
}
