package net.dark.threecore.essentials;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BackpackService implements Listener {
    private static final String DATA_FILE = "player/backpacks.yml";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public BackpackService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void handle(CommandSender sender) {
        handle(sender, new String[0]);
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only.</red>");
            return;
        }
        if (!player.hasPermission("3smpcore.backpack.use")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (!isAllowedWorld(player.getWorld()) && !hasBypass(player)) {
            Text.send(player, configs.get("admin/permissions.yml").getString("utility.backpack.deny-world-message", "<red>Backpacks are not available in this world.</red>"));
            return;
        }
        int index = parseIndex(args);
        int maxOpenable = maxOpenableBackpack(player);
        if (index < 1 || index > maxOpenable) {
            Text.send(player, "<red>You can open " + backpackLimitMessage(maxOpenable) + ".</red>");
            return;
        }
        player.openInventory(loadInventory(player.getUniqueId(), index));
    }

    public List<String> complete(String[] args) {
        return args.length <= 1 ? List.of("1", "2", "3") : List.of();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        saveInventory(player.getUniqueId(), holder.index(), event.getInventory());
        Text.actionBar(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Backpack saved.</gradient>");
    }

    private Inventory loadInventory(UUID uuid, int index) {
        int size = normalizedSize(configs.get("admin/permissions.yml").getInt("utility.backpack.size", 54));
        String title = configs.get("admin/permissions.yml").getString("utility.backpack.title", "Backpack {index}").replace("{index}", String.valueOf(index));
        Inventory inventory = Bukkit.createInventory(new BackpackHolder(uuid, index), size, title);
        YamlConfiguration yaml = configs.get(DATA_FILE);
        String base = "backpacks." + uuid + "." + index;
        boolean newFormat = yaml.isConfigurationSection(base);
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = yaml.getItemStack(base + "." + slot);
            if (item == null && index == 1 && !newFormat) item = yaml.getItemStack("backpacks." + uuid + "." + slot);
            if (item != null && item.getType() != Material.AIR) inventory.setItem(slot, item);
        }
        return inventory;
    }

    private void saveInventory(UUID uuid, int index, Inventory inventory) {
        YamlConfiguration yaml = configs.get(DATA_FILE);
        String base = "backpacks." + uuid + "." + index;
        yaml.set(base, null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) yaml.set(base + "." + slot, item);
        }
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            yaml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save backpack data: " + ex.getMessage());
        }
    }

    private int normalizedSize(int configured) {
        int clamped = Math.max(9, Math.min(54, configured));
        return Math.max(9, (clamped / 9) * 9);
    }

    private int parseIndex(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) return 1;
        try {
            return Math.max(1, Integer.parseInt(args[0]));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private int maxOpenableBackpack(Player player) {
        int limit = backpackLimit(player);
        if (limit < 0) return Math.max(1, configs.get("admin/permissions.yml").getInt("utility.backpack.unlimited-max-openable", 54));
        return Math.max(0, limit);
    }

    private int backpackLimit(Player player) {
        if (player.hasPermission("3smpcore.backpack.limit.unlimited")) return -1;
        int limit = configs.get("admin/permissions.yml").getInt("utility.backpack.default-limit", 1);
        for (int value : List.of(10, 3, 2, 1)) {
            if (player.hasPermission("3smpcore.backpack.limit." + value)) limit = Math.max(limit, value);
        }
        String group = primaryGroup(player);
        if (group.equals("h318") || group.equals("owner")) return -1;
        if (group.equals("admin") || group.equals("sr-admin") || group.equals("sradmin")) limit = Math.max(limit, 10);
        else if (group.equals("ultra")) limit = Math.max(limit, 3);
        else if (group.equals("mvp")) limit = Math.max(limit, 2);
        else if (group.equals("pro") || group.equals("3")) limit = Math.max(limit, 1);
        return limit;
    }

    private String backpackLimitMessage(int maxOpenable) {
        if (maxOpenable == 1) return "1 backpack";
        return maxOpenable + " backpacks";
    }

    private String primaryGroup(Player player) {
        try {
            org.bukkit.plugin.Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (luckPerms == null || !luckPerms.isEnabled()) return "";
            Object api = luckPerms.getClass().getMethod("getApi").invoke(luckPerms);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return "";
            Object group = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return group == null ? "" : group.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean isAllowedWorld(World world) {
        if (configs.get("admin/permissions.yml").getBoolean("utility.backpack.all-worlds", true)) return true;
        return isSurvivalLike(world);
    }

    private boolean isSurvivalLike(World world) {
        if (world == null) return false;
        String name = world.getName().toLowerCase(Locale.ROOT);
        String base = configs.get("world/survival.yml").getString("world", "world").toLowerCase(Locale.ROOT);
        String market = configs.get("world/market.yml").getString("world.name", "market").toLowerCase(Locale.ROOT);
        boolean end = name.equals(base + "_the_end") || name.equals("world_the_end");
        return name.equals(base) || name.equals(base + "_nether") || name.equals(market) || (end && !endLocked());
    }

    private boolean endLocked() {
        return configs.get("world/survival.yml").getBoolean("end-lock.enabled", true);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("3smpcore.command.bypass") || player.hasPermission("3smpcore.staff.sradmin") || player.hasPermission("3smpcore.admin");
    }

    private record BackpackHolder(UUID owner, int index) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
