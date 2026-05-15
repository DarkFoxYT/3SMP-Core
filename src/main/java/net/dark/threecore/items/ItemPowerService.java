package net.dark.threecore.items;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ItemPowerService implements Listener {
    private static final String PANDORA_ID = "pandoras_pick";
    private static final String LAZURITE_ID = "lazurite";
    private static final String FORSAKEN_ELYTRA_ID = "forsaken_elytra";
    private static final String PANDORA_NS = "threesmp:" + PANDORA_ID;
    private static final String LAZURITE_NS = "threesmp:" + LAZURITE_ID;
    private static final String FORSAKEN_ELYTRA_NS = "threesmp:" + FORSAKEN_ELYTRA_ID;
    private static final int MAX_VEIN_BLOCKS = 96;
    private static final long SPECIAL_DROP_COOLDOWN_MILLIS = 10L * 60L * 1000L;
    private static final long LAZURITE_COOLDOWN_MILLIS = 30_000L;
    private static final long ELYTRA_LAUNCH_COOLDOWN_MILLIS = 10_000L;
    private static final Color SAPPHIRE = Color.fromRGB(65, 105, 225);
    private static final Color LAZURITE_BLUE = Color.fromRGB(48, 170, 255);
    private static final Color GOLD = Color.fromRGB(250, 204, 21);

    private final JavaPlugin plugin;
    private final NamespacedKey pandoraKey;
    private final NamespacedKey pandoraSilkKey;
    private final NamespacedKey lazuriteKey;
    private final NamespacedKey forsakenElytraKey;
    private final NamespacedKey lazuriteProjectileKey;
    private final Map<UUID, Long> specialDropCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lazuriteCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> elytraLaunchCooldowns = new ConcurrentHashMap<>();

    public ItemPowerService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pandoraKey = new NamespacedKey(plugin, "pandoras_pick");
        this.pandoraSilkKey = new NamespacedKey(plugin, "pandoras_pick_silk");
        this.lazuriteKey = new NamespacedKey(plugin, "lazurite");
        this.forsakenElytraKey = new NamespacedKey(plugin, "forsaken_elytra");
        this.lazuriteProjectileKey = new NamespacedKey(plugin, "lazurite_projectile");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickElytraTrails, 2L, 2L);
    }

    public void handle(CommandContext context) {
        if (context.args().length == 0) {
            if (!(context.sender() instanceof Player player)) {
                Text.send(context.sender(), "<red>Players only.</red>");
                return;
            }
            togglePandoraSilk(player);
            return;
        }

        CommandSender sender = context.sender();
        if (!sender.hasPermission("3smpcore.itempower.admin")) {
            Text.send(sender, "<red>No permission.</red>");
            return;
        }
        if (!context.arg(0).equalsIgnoreCase("give") || context.args().length < 3) {
            Text.send(sender, "<yellow>Use /itempower give <player> <pandoras_pick|lazurite|forsaken_elytra>.</yellow>");
            return;
        }
        Player target = Bukkit.getPlayerExact(context.arg(1));
        if (target == null) {
            Text.send(sender, "<red>That player must be online.</red>");
            return;
        }
        String id = normalizeId(context.arg(2));
        ItemStack item = customItem(id);
        if (item == null) {
            Text.send(sender, "<red>Unknown item power.</red>");
            return;
        }
        var leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        target.updateInventory();
        Text.send(sender, "<green>Gave " + displayId(id) + " to " + target.getName() + ".</green>");
    }

    public List<String> complete(CommandContext context) {
        if (context.args().length <= 1) return List.of("give");
        if (context.args().length == 2 && context.arg(0).equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (context.args().length == 3 && context.arg(0).equalsIgnoreCase("give")) {
            return List.of(PANDORA_ID, LAZURITE_ID, FORSAKEN_ELYTRA_ID);
        }
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPandoraBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isPandora(tool)) return;
        applyPandoraEnchantments(tool);

        trySpecialDrop(player, event.getBlock().getLocation().add(0.5D, 0.65D, 0.5D));
        if (!isOre(event.getBlock().getType())) return;

        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);

        List<Block> vein = collectVein(event.getBlock());
        boolean silk = isPandoraSilk(tool);
        ItemStack dropTool = tool.clone();
        if (silk) {
            dropTool.removeEnchantment(Enchantment.FORTUNE);
            dropTool.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        } else {
            dropTool.removeEnchantment(Enchantment.SILK_TOUCH);
            dropTool.addUnsafeEnchantment(Enchantment.FORTUNE, 4);
        }

        for (Block block : vein) {
            if (block.getType() == Material.AIR) continue;
            Material before = block.getType();
            Collection<ItemStack> drops = block.getDrops(dropTool, player);
            Location dropAt = block.getLocation().add(0.5D, 0.5D, 0.5D);
            drops.forEach(drop -> block.getWorld().dropItemNaturally(dropAt, drop));
            block.getWorld().spawnParticle(Particle.BLOCK, dropAt, 8, 0.22D, 0.22D, 0.22D, before.createBlockData());
            block.setType(Material.AIR, true);
            if (!damageTool(player, tool)) break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLazuriteUse(PlayerInteractEvent event) {
        if (event.getHand() == null) return;
        if (!event.getAction().isRightClick()) return;
        Player player = event.getPlayer();
        ItemStack item = event.getHand() == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (!isLazurite(item)) return;
        applyLazuriteEnchantments(item);
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        long now = System.currentTimeMillis();
        long readyAt = lazuriteCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            Text.actionBar(player, "<gradient:#38bdf8:#4169e1>Lazurite</gradient> <gray>ready in</gray> <white>" + secondsLeft(readyAt, now) + "s</white><gray>.</gray>");
            return;
        }

        lazuriteCooldowns.put(player.getUniqueId(), now + LAZURITE_COOLDOWN_MILLIS);
        launchLazurite(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLazuriteHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectile.getPersistentDataContainer().has(lazuriteProjectileKey, PersistentDataType.BYTE)) return;
        Location impact = projectile.getLocation();
        if (event.getHitEntity() instanceof LivingEntity target) {
            if (projectile.getShooter() instanceof Player shooter && target.getUniqueId().equals(shooter.getUniqueId())) return;
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 7 * 20, 2, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 7 * 20, 1, true, true, true));
            impact = target.getLocation().add(0.0D, Math.max(0.8D, target.getHeight() * 0.55D), 0.0D);
        }
        lazuriteImpact(impact);
        projectile.remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForsakenLaunch(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || player.isGliding()) return;
        ItemStack chestplate = player.getInventory().getChestplate();
        if (!isForsakenElytra(chestplate)) return;
        applyForsakenElytraEnchantments(chestplate);
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getY() - from.getY() < 0.16D) return;
        if (!from.clone().subtract(0.0D, 0.08D, 0.0D).getBlock().getType().isSolid()) return;

        long now = System.currentTimeMillis();
        long readyAt = elytraLaunchCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) return;

        elytraLaunchCooldowns.put(player.getUniqueId(), now + ELYTRA_LAUNCH_COOLDOWN_MILLIS);
        Location loc = player.getLocation();
        player.setVelocity(loc.getDirection().setY(0.0D).normalize().multiply(0.55D).setY(1.15D));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.isOnGround() && isForsakenElytra(player.getInventory().getChestplate())) {
                player.setGliding(true);
            }
        }, 5L);
        launchBurst(player);
    }

    private void togglePandoraSilk(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isPandora(item)) {
            Text.send(player, "<red>Hold Pandora's Pick to toggle Silk Touch.</red>");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        boolean enabled = !isPandoraSilk(item);
        if (enabled) {
            meta.getPersistentDataContainer().set(pandoraSilkKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.getPersistentDataContainer().remove(pandoraSilkKey);
        }
        item.setItemMeta(meta);
        if (enabled) item.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        else item.removeEnchantment(Enchantment.SILK_TOUCH);
        applyPandoraEnchantments(item);
        Text.send(player, enabled ? "<aqua>Silk Touch Enabled</aqua>" : "<gray>Silk Touch Disabled</gray>");
    }

    private List<Block> collectVein(Block start) {
        String family = oreFamily(start.getType());
        List<Block> result = new ArrayList<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(start);
        seen.add(blockKey(start));
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty() && result.size() < MAX_VEIN_BLOCKS) {
            Block block = queue.removeFirst();
            if (!family.equals(oreFamily(block.getType()))) continue;
            result.add(block);
            for (int[] direction : directions) {
                Block next = block.getRelative(direction[0], direction[1], direction[2]);
                String key = blockKey(next);
                if (seen.add(key) && family.equals(oreFamily(next.getType()))) queue.add(next);
            }
        }
        return result;
    }

    private boolean damageTool(Player player, ItemStack tool) {
        if (player.getGameMode() == GameMode.CREATIVE) return true;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) return true;
        int max = tool.getType().getMaxDurability();
        if (max <= 0) return true;
        damageable.setDamage(damageable.getDamage() + 1);
        if (damageable.getDamage() >= max) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8F, 1.0F);
            return false;
        }
        tool.setItemMeta(meta);
        player.getInventory().setItemInMainHand(tool);
        return true;
    }

    private void trySpecialDrop(Player player, Location location) {
        long now = System.currentTimeMillis();
        if (specialDropCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return;

        double roll = ThreadLocalRandom.current().nextDouble(100.0D);
        ItemStack reward;
        if (roll < 2.0D) reward = randomEnchantedBook();
        else if (roll < 7.0D) reward = new ItemStack(Material.GOLDEN_APPLE);
        else if (roll < 17.0D) reward = new ItemStack(Material.GOLD_NUGGET);
        else return;

        specialDropCooldowns.put(player.getUniqueId(), now + SPECIAL_DROP_COOLDOWN_MILLIS);
        location.getWorld().dropItemNaturally(location, reward);
        location.getWorld().spawnParticle(Particle.DUST, location, 22, 0.25D, 0.25D, 0.25D, 0.0D, new Particle.DustOptions(SAPPHIRE, 1.1F));
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.65F, 1.3F);
    }

    private ItemStack randomEnchantedBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        List<String> ids = List.of("sharpness", "efficiency", "protection", "unbreaking", "fortune", "power", "looting", "fire_aspect");
        Enchantment enchantment = null;
        for (int attempts = 0; attempts < 8 && enchantment == null; attempts++) {
            enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(ids.get(ThreadLocalRandom.current().nextInt(ids.size()))));
        }
        if (enchantment == null) enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        if (enchantment != null) meta.addStoredEnchant(enchantment, ThreadLocalRandom.current().nextInt(1, 4), true);
        book.setItemMeta(meta);
        return book;
    }

    private void launchLazurite(Player player) {
        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.setGravity(false);
        projectile.setVelocity(player.getLocation().getDirection().normalize().multiply(1.35D));
        projectile.getPersistentDataContainer().set(lazuriteProjectileKey, PersistentDataType.BYTE, (byte) 1);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75F, 1.45F);
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead() || ticks > 60) {
                    projectile.remove();
                    cancel();
                    return;
                }
                Location loc = projectile.getLocation();
                loc.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.06D, 0.06D, 0.06D, 0.0D, new Particle.DustOptions(LAZURITE_BLUE, 1.0F));
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0.03D, 0.03D, 0.03D, 0.01D);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void lazuriteImpact(Location location) {
        World world = location.getWorld();
        world.spawnParticle(Particle.DUST, location, 55, 0.55D, 0.55D, 0.55D, 0.0D, new Particle.DustOptions(LAZURITE_BLUE, 1.35F));
        world.spawnParticle(Particle.ELECTRIC_SPARK, location, 18, 0.35D, 0.35D, 0.35D, 0.04D);
        world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7F, 1.8F);
    }

    private void tickElytraTrails() {
        Particle.DustOptions dust = new Particle.DustOptions(SAPPHIRE, 0.85F);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isGliding() || !isForsakenElytra(player.getInventory().getChestplate())) continue;
            Location base = player.getLocation().add(0.0D, 0.35D, 0.0D);
            Location behind = base.subtract(player.getLocation().getDirection().normalize().multiply(0.7D));
            player.getWorld().spawnParticle(Particle.DUST, behind, 7, 0.18D, 0.18D, 0.18D, 0.0D, dust);
        }
    }

    private void launchBurst(Player player) {
        Location location = player.getLocation().add(0.0D, 0.35D, 0.0D);
        World world = player.getWorld();
        world.spawnParticle(Particle.DUST, location, 24, 0.32D, 0.18D, 0.32D, 0.0D, new Particle.DustOptions(SAPPHIRE, 1.05F));
        world.spawnParticle(Particle.DUST, location, 18, 0.26D, 0.16D, 0.26D, 0.0D, new Particle.DustOptions(GOLD, 0.9F));
        world.playSound(location, Sound.ITEM_FIRECHARGE_USE, 0.65F, 1.35F);
    }

    private boolean isPandora(ItemStack item) {
        return isCustomItem(item, pandoraKey, PANDORA_NS, "pandora's pick", "_PICKAXE");
    }

    private boolean isLazurite(ItemStack item) {
        return isCustomItem(item, lazuriteKey, LAZURITE_NS, "lazurite", "");
    }

    private boolean isForsakenElytra(ItemStack item) {
        return isCustomItem(item, forsakenElytraKey, FORSAKEN_ELYTRA_NS, "forsaken elytra", "ELYTRA");
    }

    private boolean isCustomItem(ItemStack item, NamespacedKey key, String itemsAdderId, String displayName, String materialSuffix) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return true;
        String id = itemsAdderId(item);
        if (itemsAdderId.equalsIgnoreCase(id) || itemsAdderId.substring(itemsAdderId.indexOf(':') + 1).equalsIgnoreCase(id)) return true;
        if (!materialSuffix.isBlank() && !item.getType().name().endsWith(materialSuffix)) return false;
        if (!meta.hasDisplayName()) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).toLowerCase(Locale.ROOT);
        return name.contains(displayName);
    }

    private boolean isPandoraSilk(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(pandoraSilkKey, PersistentDataType.BYTE);
    }

    private ItemStack customItem(String id) {
        return switch (normalizeId(id)) {
            case PANDORA_ID -> mark(applyPandoraEnchantments(itemsAdderItem(PANDORA_NS, fallbackPandora())), pandoraKey);
            case LAZURITE_ID -> mark(applyLazuriteEnchantments(itemsAdderItem(LAZURITE_NS, fallbackLazurite())), lazuriteKey);
            case FORSAKEN_ELYTRA_ID -> mark(applyForsakenElytraEnchantments(itemsAdderItem(FORSAKEN_ELYTRA_NS, fallbackForsakenElytra())), forsakenElytraKey);
            default -> null;
        };
    }

    private ItemStack itemsAdderItem(String id, ItemStack fallback) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = customStack.getMethod("getInstance", String.class).invoke(null, id);
                if (stack != null) {
                    Object item = stack.getClass().getMethod("getItemStack").invoke(stack);
                    if (item instanceof ItemStack itemStack) return itemStack.clone();
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }

    private ItemStack mark(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack applyPandoraEnchantments(ItemStack item) {
        addEnchant(item, "efficiency", 6);
        addEnchant(item, "fortune", 4);
        addEnchant(item, "unbreaking", 3);
        addEnchant(item, "mending", 1);
        return item;
    }

    private ItemStack applyLazuriteEnchantments(ItemStack item) {
        addEnchant(item, "unbreaking", 3);
        addEnchant(item, "mending", 1);
        addEnchant(item, "knockback", 2);
        return item;
    }

    private ItemStack applyForsakenElytraEnchantments(ItemStack item) {
        addEnchant(item, "unbreaking", 3);
        addEnchant(item, "mending", 1);
        return item;
    }

    private void addEnchant(ItemStack item, String key, int level) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (enchantment != null) item.addUnsafeEnchantment(enchantment, level);
    }

    private ItemStack fallbackPandora() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<gradient:#f4cd2a:#38bdf8><bold>Pandora's Pick</bold></gradient>"));
        meta.lore(List.of(
                Text.mm("<gray>Vein mines connected ore blocks.</gray>"),
                Text.mm("<gray>Vein drops use Fortune IV unless Silk Touch is toggled.</gray>"),
                Text.mm("<dark_gray>/itempower toggles Silk Touch.</dark_gray>")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack fallbackLazurite() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<gradient:#38bdf8:#4169e1><bold>Lazurite</bold></gradient>"));
        meta.lore(List.of(Text.mm("<gray>Right-click to fire a blue aura projectile.</gray>"), Text.mm("<dark_gray>30s cooldown.</dark_gray>")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack fallbackForsakenElytra() {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm("<gradient:#4169e1:#f4cd2a><bold>Forsaken Elytra</bold></gradient>"));
        meta.lore(List.of(Text.mm("<gray>Leaves a sapphire trail while gliding.</gray>"), Text.mm("<gray>Sneak + jump to launch.</gray>")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
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

    private boolean isOre(Material material) {
        return !oreFamily(material).isBlank();
    }

    private String oreFamily(Material material) {
        if (material == Material.ANCIENT_DEBRIS) return "ANCIENT_DEBRIS";
        String name = material.name();
        if (!name.endsWith("_ORE")) return "";
        return name.replace("DEEPSLATE_", "").replace("NETHER_", "");
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String normalizeId(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace("'", "").replace("-", "_");
    }

    private String displayId(String id) {
        return switch (id) {
            case PANDORA_ID -> "Pandora's Pick";
            case LAZURITE_ID -> "Lazurite";
            case FORSAKEN_ELYTRA_ID -> "Forsaken Elytra";
            default -> id;
        };
    }

    private long secondsLeft(long readyAt, long now) {
        return Math.max(1L, (readyAt - now + 999L) / 1000L);
    }
}
