package net.dark.threecore.items;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class VeilcutterService implements Listener {
    private static final String ITEM_ID = "veilcutter";
    private static final String ITEM_NAMESPACED_ID = "threesmp:veilcutter";
    private static final long EDGE_COOLDOWN_MILLIS = 30_000L;
    private static final long VEIL_COOLDOWN_MILLIS = 60_000L;
    private static final int VEIL_DURATION_TICKS = 7 * 20;
    private static final double EDGE_CHANCE = 0.05D;
    private static final Color SAPPHIRE = Color.fromRGB(45, 165, 255);

    private final JavaPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> edgeCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> veilCooldowns = new ConcurrentHashMap<>();

    public VeilcutterService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "veilcutter");
    }

    public void handle(CommandContext context) {
        CommandSender sender = context.sender();
        if (context.args().length == 0) {
            if (sender instanceof Player player && sender.hasPermission("3smpcore.veilcutter.admin")) {
                give(player);
                Text.send(player, "<gradient:#38bdf8:#2563eb>Veilcutter</gradient> <gray>added to your inventory.</gray>");
                return;
            }
            Text.send(sender, "<yellow>Use /veilcutter give <player>.</yellow>");
            return;
        }
        if (!sender.hasPermission("3smpcore.veilcutter.admin")) {
            Text.send(sender, "<red>No permission.</red>");
            return;
        }
        if (!context.arg(0).equalsIgnoreCase("give") || context.args().length < 2) {
            Text.send(sender, "<yellow>Use /veilcutter give <player>.</yellow>");
            return;
        }
        Player target = Bukkit.getPlayerExact(context.arg(1));
        if (target == null) {
            Text.send(sender, "<red>That player must be online.</red>");
            return;
        }
        give(target);
        Text.send(sender, "<green>Gave Veilcutter to " + target.getName() + ".</green>");
    }

    public List<String> complete(CommandContext context) {
        if (context.args().length <= 1) return List.of("give");
        if (context.args().length == 2 && context.arg(0).equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof ArmorStand stand && (stand.isMarker() || !stand.isVisible())) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isVeilcutter(weapon)) return;
        enchantVeilcutter(weapon);

        long now = System.currentTimeMillis();
        if (edgeCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return;
        if (ThreadLocalRandom.current().nextDouble() >= EDGE_CHANCE) return;

        event.setDamage(event.getDamage() * 2.0D);
        edgeCooldowns.put(player.getUniqueId(), now + EDGE_COOLDOWN_MILLIS);
        sapphireFlash(target);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 1.55F);
        Text.actionBar(player, "<gradient:#38bdf8:#2563eb>Prophecy's Edge</gradient> <gray>cut through the veil.</gray>");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        ItemStack item = hand == EquipmentSlot.HAND ? event.getPlayer().getInventory().getItemInMainHand() : event.getPlayer().getInventory().getItemInOffHand();
        if (!isVeilcutter(item)) return;
        enchantVeilcutter(item);
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long readyAt = veilCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            Text.actionBar(player, "<gradient:#38bdf8:#2563eb>Veil of the Forsaken</gradient> <gray>ready in</gray> <white>" + secondsLeft(readyAt, now) + "s</white><gray>.</gray>");
            return;
        }

        veilCooldowns.put(player.getUniqueId(), now + VEIL_COOLDOWN_MILLIS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, VEIL_DURATION_TICKS, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, VEIL_DURATION_TICKS, 0, true, false, true));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.45F);
        Text.actionBar(player, "<gradient:#38bdf8:#2563eb>Veil of the Forsaken</gradient> <gray>wraps around you.</gray>");
        startAura(player);
    }

    private void startAura(Player player) {
        UUID uuid = player.getUniqueId();
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                Player current = Bukkit.getPlayer(uuid);
                if (current == null || !current.isOnline() || current.isDead() || ticks > VEIL_DURATION_TICKS) {
                    cancel();
                    return;
                }
                sapphireAura(current, ticks);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void sapphireFlash(LivingEntity target) {
        World world = target.getWorld();
        double height = Math.max(0.9D, target.getHeight());
        Location center = target.getLocation().add(0.0D, height * 0.55D, 0.0D);
        Particle.DustOptions dust = new Particle.DustOptions(SAPPHIRE, 1.55F);
        world.spawnParticle(Particle.DUST, center, 42, 0.38D, height * 0.3D, 0.38D, 0.0D, dust);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 16, 0.32D, height * 0.25D, 0.32D, 0.02D);
    }

    private void sapphireAura(Player player, int tick) {
        World world = player.getWorld();
        Location base = player.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(SAPPHIRE, 1.05F);
        double phase = tick * 0.22D;
        for (int i = 0; i < 14; i++) {
            double angle = phase + (Math.PI * 2.0D * i / 14.0D);
            double radius = 0.62D + Math.sin(phase + i) * 0.06D;
            double y = 0.18D + (i % 7) * 0.27D;
            Location point = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0.0D, 0.0D, 0.0D, dust);
        }
        if (tick % 8 == 0) {
            world.spawnParticle(Particle.DUST, base.clone().add(0.0D, 1.0D, 0.0D), 10, 0.32D, 0.55D, 0.32D, 0.0D, new Particle.DustOptions(SAPPHIRE, 0.85F));
        }
    }

    public void give(Player player) {
        ItemStack item = itemsAdderItem();
        mark(item);
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.updateInventory();
    }

    private ItemStack itemsAdderItem() {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = customStack.getMethod("getInstance", String.class).invoke(null, ITEM_NAMESPACED_ID);
                if (stack != null) {
                    Object item = stack.getClass().getMethod("getItemStack").invoke(stack);
                    if (item instanceof ItemStack itemStack) return itemStack.clone();
                }
            } catch (Throwable ignored) {
            }
        }
        return enchantVeilcutter(fallbackItem());
    }

    private ItemStack fallbackItem() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<gradient:#38bdf8:#2563eb><bold>Veilcutter</bold></gradient>"));
        meta.lore(List.of(
                Text.mm("<gray>Prophecy's Edge:</gray> <white>5% chance to deal double damage.</white>"),
                Text.mm("<dark_gray>30s cooldown between procs.</dark_gray>"),
                Text.mm("<gray>Veil of the Forsaken:</gray> <white>Right-click for 7s of Regeneration II and Resistance I.</white>"),
                Text.mm("<dark_gray>60s cooldown.</dark_gray>")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void mark(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        enchantVeilcutter(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
    }

    private ItemStack enchantVeilcutter(ItemStack item) {
        addEnchant(item, "sharpness", 5);
        addEnchant(item, "unbreaking", 3);
        addEnchant(item, "mending", 1);
        addEnchant(item, "looting", 3);
        addEnchant(item, "fire_aspect", 2);
        addEnchant(item, "knockback", 1);
        addEnchant(item, "sweeping_edge", 3);
        return item;
    }

    private void addEnchant(ItemStack item, String key, int level) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (enchantment != null) item.addUnsafeEnchantment(enchantment, level);
    }

    private boolean isVeilcutter(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return true;
        String itemsAdderId = itemsAdderId(item);
        if (ITEM_NAMESPACED_ID.equalsIgnoreCase(itemsAdderId) || ITEM_ID.equalsIgnoreCase(itemsAdderId)) return true;
        if (!item.getType().name().endsWith("_SWORD")) return false;
        if (!meta.hasDisplayName()) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).toLowerCase(Locale.ROOT);
        return name.contains("veilcutter");
    }

    private String itemsAdderId(ItemStack item) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return "";
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = customStack.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            if (stack == null) return "";
            for (String method : List.of("getNamespacedID", "getNamespacedId", "getId", "getName")) {
                try {
                    Object value = stack.getClass().getMethod(method).invoke(stack);
                    if (value != null) return value.toString();
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private long secondsLeft(long readyAt, long now) {
        return Math.max(1L, (readyAt - now + 999L) / 1000L);
    }
}
