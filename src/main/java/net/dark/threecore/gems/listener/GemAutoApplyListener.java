package net.dark.threecore.gems.listener;

import net.dark.threecore.duels.DuelService;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gems.SeasonalGemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class GemAutoApplyListener implements Listener {
    private static final String AUTO_KEY = "3smpcore_auto_gemmed";
    private final JavaPlugin plugin;
    private final SeasonalGemRegistry registry;
    private final DuelService duelService;
    private final ConfigFiles configs;
    private final NamespacedKey autoKey;

    public GemAutoApplyListener(JavaPlugin plugin, SeasonalGemRegistry registry, DuelService duelService, ConfigFiles configs) {
        this.plugin = plugin;
        this.registry = registry;
        this.duelService = duelService;
        this.configs = configs;
        this.autoKey = new NamespacedKey(plugin, AUTO_KEY);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        schedule(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) schedule(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        schedule(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        schedule(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        schedule(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) schedule(player);
    }

    public void schedule(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> autoApply(player));
    }

    public void autoApply(Player player) {
        if (player == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (duelService != null && duelService.isPlayerInDuel(player.getUniqueId())) return;
        if (configs.get("gems/gems.yml").getStringList("gems.disabled-worlds").stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()))) return;
        applyIfNeeded(player.getInventory().getItemInMainHand());
        applyIfNeeded(player.getInventory().getItemInOffHand());
        for (ItemStack armor : player.getInventory().getArmorContents()) applyIfNeeded(armor);
        for (ItemStack item : player.getInventory().getContents()) applyIfNeeded(item);
    }

    private void applyIfNeeded(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        if (!registry.supports(item.getType())) return;
        if (registry.isDuelRestricted(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.getPersistentDataContainer().has(autoKey, PersistentDataType.BYTE)) return;
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        meta.lore(lore);
        meta.getPersistentDataContainer().set(autoKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }
}
