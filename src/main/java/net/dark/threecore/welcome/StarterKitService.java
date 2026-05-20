package net.dark.threecore.welcome;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.rtp.RtpManager;
import net.dark.threecore.text.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class StarterKitService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final RtpManager rtpManager;

    public StarterKitService(JavaPlugin plugin, ConfigFiles configs, RtpManager rtpManager) {
        this.plugin = plugin;
        this.configs = configs;
        this.rtpManager = rtpManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;
        if (!configs.get("core/welcome.yml").getBoolean("starter-kit.enabled", true)) return;
        long delay = Math.max(1L, configs.get("core/welcome.yml").getLong("starter-kit.delay-ticks", 30L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> give(player), delay);
    }

    private void give(Player player) {
        if (player == null || !player.isOnline()) return;
        var yaml = configs.get("core/welcome.yml");
        if (yaml.getBoolean("starter-kit.rtp-compass-enabled", false)) {
            int compassSlot = Math.max(0, Math.min(8, yaml.getInt("starter-kit.rtp-compass-slot", 0)));
            rtpManager.giveStarterCompass(player, compassSlot);
        }
        for (String entry : yaml.getStringList("starter-kit.items")) {
            StarterItem item = parse(entry);
            if (item == null || item.material().isAir()) continue;
            ItemStack stack = new ItemStack(item.material(), item.amount());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && !item.name().isBlank()) {
                meta.displayName(Text.mm(item.name()));
                stack.setItemMeta(meta);
            }
            if (item.slot() >= 0 && item.slot() <= 35) player.getInventory().setItem(item.slot(), stack);
            else player.getInventory().addItem(stack).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.updateInventory();
        String message = yaml.getString("starter-kit.message", "<green>Starter kit received.</green>");
        if (!message.isBlank()) Text.send(player, message);
    }

    private StarterItem parse(String entry) {
        if (entry == null || entry.isBlank()) return null;
        String[] parts = entry.split(";", -1);
        Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
        if (material == null) return null;
        int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
        int slot = parts.length > 2 ? parseInt(parts[2], -1) : -1;
        String name = parts.length > 3 ? parts[3].trim() : "";
        return new StarterItem(material, Math.max(1, Math.min(64, amount)), slot, name);
    }

    private int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record StarterItem(Material material, int amount, int slot, String name) {}
}
