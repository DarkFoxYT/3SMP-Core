package net.dark.threecore.sell;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SellService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MoneyService moneyService;

    public SellService(JavaPlugin plugin, ConfigFiles configs, MoneyService moneyService) {
        this.plugin = plugin;
        this.configs = configs;
        this.moneyService = moneyService;
    }

    public void open(Player player) {
        int size = Math.max(9, Math.min(54, configs.get("sell.yml").getInt("menu.size", 54)));
        Inventory inv = Bukkit.createInventory(new SellHolder(), size, configs.get("sell.yml").getString("menu.title", "3SMP Sell Bin"));
        player.openInventory(inv);
        Text.send(player, "<gray>Drop sellable items into the chest, then close it to sell.</gray>");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        double total = 0.0D;
        Map<Integer, ItemStack> unsold = new HashMap<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            double price = price(item.getType());
            if (price <= 0.0D) unsold.put(unsold.size(), item.clone());
            else total += price * item.getAmount();
        }
        event.getInventory().clear();
        for (ItemStack item : unsold.values()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (total > 0.0D) {
            moneyService.give(player.getUniqueId(), total);
            Text.send(player, "<green>Sold items for " + moneyService.format(total) + ".</green>");
        } else if (!unsold.isEmpty()) Text.send(player, "<yellow>No configured sell prices matched those items.</yellow>");
    }

    private double price(Material material) {
        return configs.get("sell.yml").getDouble("prices." + material.name().toLowerCase(Locale.ROOT), 0.0D);
    }

    private static final class SellHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
