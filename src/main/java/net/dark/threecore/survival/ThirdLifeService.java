package net.dark.threecore.survival;

import net.dark.threecore.text.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ThirdLifeService implements Listener {
    private static final int MAX_LIVES = 3;
    private final JavaPlugin plugin;
    private final NamespacedKey livesKey;

    public ThirdLifeService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.livesKey = new NamespacedKey(plugin, "third_life_lives");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThirdLifePop(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        PlayerInventory inventory = player.getInventory();
        ItemStack held = hand == EquipmentSlot.HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
        if (!isThirdLife(held)) return;
        pop(player, hand, held, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getHealth() - event.getFinalDamage() > 0) return;

        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        ItemStack off = inventory.getItemInOffHand();
        EquipmentSlot hand = null;
        ItemStack held = null;
        if (isThirdLife(main) && main.getType() != Material.TOTEM_OF_UNDYING) {
            hand = EquipmentSlot.HAND;
            held = main;
        } else if (isThirdLife(off) && off.getType() != Material.TOTEM_OF_UNDYING) {
            hand = EquipmentSlot.OFF_HAND;
            held = off;
        }
        if (hand == null) return;

        event.setCancelled(true);
        player.setHealth(Math.min(player.getMaxHealth(), 1.0));
        player.setFireTicks(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.0, 0), 45, 0.45, 0.7, 0.45, 0.08);
        pop(player, hand, held, false);
    }

    private void pop(Player player, EquipmentSlot hand, ItemStack held, boolean vanillaConsumed) {
        PlayerInventory inventory = player.getInventory();
        ItemStack next = held.clone();
        int livesBefore = lives(next);
        int livesAfter = livesBefore - 1;
        if (livesAfter <= 0) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!vanillaConsumed && hand == EquipmentSlot.HAND && isThirdLife(inventory.getItemInMainHand())) inventory.setItemInMainHand(null);
                else if (!vanillaConsumed && hand == EquipmentSlot.OFF_HAND && isThirdLife(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
                Text.actionBar(player, "<gold>The Third Life</gold> <gray>burned out.</gray>");
            });
            return;
        }

        next.setAmount(1);
        applyLives(next, livesAfter);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (hand == EquipmentSlot.HAND) inventory.setItemInMainHand(next);
            else inventory.setItemInOffHand(next);
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.15f);
            Text.actionBar(player, "<gold>The Third Life</gold> <gray>saved you.</gray> <yellow>" + livesAfter + " left</yellow><gray>.</gray>");
        });
    }

    private boolean isThirdLife(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(livesKey, PersistentDataType.INTEGER)) return true;
        if (item.getType() != Material.TOTEM_OF_UNDYING && item.getType() != Material.COMMAND_BLOCK) return false;

        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        if (meta.hasDisplayName()) {
            String name = plain.serialize(meta.displayName()).toLowerCase(Locale.ROOT);
            if (name.contains("third life")) return true;
        }
        if (meta.hasLore() && meta.lore() != null) {
            for (var line : meta.lore()) {
                String text = plain.serialize(line).toLowerCase(Locale.ROOT);
                if (text.contains("uses remaining") || text.contains("lives remaining") || text.contains("third life")) return true;
            }
        }
        return false;
    }

    private int lives(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return MAX_LIVES;
        ItemMeta meta = item.getItemMeta();
        Integer stored = meta.getPersistentDataContainer().get(livesKey, PersistentDataType.INTEGER);
        if (stored != null) return Math.max(1, Math.min(MAX_LIVES, stored));
        if (meta.hasLore() && meta.lore() != null) {
            PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
            for (var line : meta.lore()) {
                String text = plain.serialize(line).toLowerCase(Locale.ROOT);
                if (text.contains("1")) return 1;
                if (text.contains("2")) return 2;
                if (text.contains("3")) return 3;
            }
        }
        return MAX_LIVES;
    }

    private void applyLives(ItemStack item, int lives) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(livesKey, PersistentDataType.INTEGER, lives);
        meta.displayName(Text.mm("<gradient:#ff7a18:#ffd166><bold>The Third Life</bold></gradient>"));
        meta.lore(lore(lives));
        meta.setMaxStackSize(1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }

    private List<net.kyori.adventure.text.Component> lore(int lives) {
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Text.mm("<gray>The prophecy does not let its chosen fall.</gray>"));
        lore.add(Text.mm("<gray>Not once. Not twice. Not three times.</gray>"));
        lore.add(Text.mm(""));
        lore.add(Text.mm("<yellow>Lives Remaining: " + lives + "</yellow>"));
        lore.add(Text.mm(""));
        lore.add(Text.mm("<dark_gray>Hold in either hand while dying.</dark_gray>"));
        lore.add(Text.mm("<dark_gray>Returns with one fewer life.</dark_gray>"));
        return lore;
    }
}
