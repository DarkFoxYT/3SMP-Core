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
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only.</red>");
            return;
        }
        if (!player.hasPermission("3smpcore.backpack.use")) {
            Text.send(player, "<red>No permission.</red>");
            return;
        }
        if (!isSurvivalLike(player.getWorld()) && !hasBypass(player)) {
            Text.send(player, "<red>Backpacks can only be opened in survival or market worlds.</red>");
            return;
        }
        player.openInventory(loadInventory(player.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        saveInventory(player.getUniqueId(), event.getInventory());
        Text.actionBar(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Backpack saved.</gradient>");
    }

    private Inventory loadInventory(UUID uuid) {
        int size = normalizedSize(configs.get("admin/permissions.yml").getInt("utility.backpack.size", 27));
        Inventory inventory = Bukkit.createInventory(new BackpackHolder(uuid), size, configs.get("admin/permissions.yml").getString("utility.backpack.title", "Backpack"));
        YamlConfiguration yaml = configs.get(DATA_FILE);
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = yaml.getItemStack("backpacks." + uuid + "." + slot);
            if (item != null && item.getType() != Material.AIR) inventory.setItem(slot, item);
        }
        return inventory;
    }

    private void saveInventory(UUID uuid, Inventory inventory) {
        YamlConfiguration yaml = configs.get(DATA_FILE);
        yaml.set("backpacks." + uuid, null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) yaml.set("backpacks." + uuid + "." + slot, item);
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

    private boolean isSurvivalLike(World world) {
        if (world == null) return false;
        String name = world.getName().toLowerCase(Locale.ROOT);
        String base = configs.get("world/survival.yml").getString("world", "world").toLowerCase(Locale.ROOT);
        String market = configs.get("world/market.yml").getString("world.name", "market").toLowerCase(Locale.ROOT);
        return name.equals(base) || name.equals(base + "_nether") || name.equals(base + "_the_end") || name.equals(market);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("3smpcore.command.bypass") || player.hasPermission("3smpcore.staff.sradmin") || player.hasPermission("3smpcore.admin");
    }

    private record BackpackHolder(UUID owner) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
