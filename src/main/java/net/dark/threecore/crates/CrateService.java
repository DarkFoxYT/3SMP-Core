package net.dark.threecore.crates;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.dungeons.integration.ItemsAdderHook;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateService implements Listener {
    private static final String CONFIG_PATH = "crates/crates.yml";
    private static final String ENTITY_TAG = "3smp_crate";
    private static final String HOLOGRAM_PREFIX = "3smp_crate_";
    private static final int PREVIEW_SIZE = 54;
    private static final int OPENING_SIZE = 27;
    private static final int[] REWARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int[] ROLL_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int WINNING_SLOT = 13;

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final ItemsAdderHook itemsAdder;
    private final NamespacedKey crateEntityKey;
    private final NamespacedKey crateItemKey;
    private final NamespacedKey keyItemKey;
    private final NamespacedKey previewRewardKey;
    private final NamespacedKey selectionCrateKey;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private final Map<String, CrateDefinition> crates = new HashMap<>();
    private final Map<UUID, OpeningState> openings = new HashMap<>();
    private final Set<String> activeHolograms = new HashSet<>();
    private BukkitTask idleTask;

    public CrateService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
        this.itemsAdder = new ItemsAdderHook(plugin);
        this.crateEntityKey = new NamespacedKey(plugin, "crate_id");
        this.crateItemKey = new NamespacedKey(plugin, "crate_item_id");
        this.keyItemKey = new NamespacedKey(plugin, "crate_key_id");
        this.previewRewardKey = new NamespacedKey(plugin, "crate_preview_reward");
        this.selectionCrateKey = new NamespacedKey(plugin, "crate_selection_id");
    }

    public void start() {
        reload();
        spawnConfiguredCrates();
        idleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::idleEffects, 20L, 24L);
    }

    public void shutdown() {
        if (idleTask != null) idleTask.cancel();
        openings.values().forEach(state -> {
            if (state.task != null) state.task.cancel();
        });
        openings.clear();
        removeGeneratedEntities();
        removeGeneratedHolograms();
    }

    public void reload() {
        configs.reload(CONFIG_PATH);
        crates.clear();
        YamlConfiguration config = configs.get(CONFIG_PATH);
        ConfigurationSection root = config.getConfigurationSection("crates");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            CrateDefinition definition = loadDefinition(config, id.toLowerCase(Locale.ROOT));
            if (definition != null && !definition.rewards().isEmpty()) crates.put(definition.id(), definition);
        }
    }

    public void refresh() {
        reload();
        spawnConfiguredCrates();
    }

    public void handle(CommandContext context) {
        CommandSender sender = context.sender();
        if (context.args().length == 0) {
            if (sender instanceof Player player) openSelection(player);
            else Text.send(sender, "<yellow>/crate givekey <player|all> [amount] [crate] | give <player|all> <crate> [amount] | place <crate> | preview <crate> | reload</yellow>");
            return;
        }
        String sub = context.arg(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "preview", "rewards" -> {
                if (!(sender instanceof Player player)) {
                    Text.send(sender, "<red>Players only.</red>");
                    return;
                }
                openPreview(player, context.args().length >= 2 ? context.arg(1) : defaultCrateId());
            }
            case "givekey" -> giveKeyCommand(context);
            case "key" -> {
                if (context.args().length >= 2 && context.arg(1).equalsIgnoreCase("give")) giveLegacyKeyCommand(context);
                else giveKeyCommand(context);
            }
            case "give" -> giveCrateItemCommand(context);
            case "circulation", "circulate" -> circulationCommand(sender);
            case "place" -> placeCommand(context);
            case "remove", "delete" -> removeCommand(context);
            case "list" -> Text.send(sender, "<gray>Crates:</gray> <white>" + String.join(", ", crates.keySet()) + "</white>");
            case "reload" -> {
                if (!sender.hasPermission("3smpcore.crates.admin")) {
                    Text.send(sender, "<red>No permission.</red>");
                    return;
                }
                reload();
                spawnConfiguredCrates();
                Text.send(sender, "<green>Crates reloaded.</green>");
            }
            default -> Text.send(sender, "<yellow>/crate preview [crate] | givekey <player> [amount] | give <player> <crate> [amount] | circulation | place <crate> | remove <nearest|id> | reload</yellow>");
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("preview", "givekey", "key", "give", "circulation", "place", "remove", "list", "reload");
        if (args.length == 2 && (args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("place"))) return crateIds();
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givekey"))) {
            List<String> players = new ArrayList<>();
            players.add("all");
            players.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return players;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("key")) return List.of("give");
        if (args.length == 3 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 4 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) return crateIds();
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return crateIds();
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return List.of("nearest");
        return List.of();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        String crateId = entity.getPersistentDataContainer().get(crateEntityKey, PersistentDataType.STRING);
        if (crateId == null || crateId.isBlank()) return;
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            openPreview(event.getPlayer(), crateId);
            return;
        }
        openCrate(event.getPlayer(), crateId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) return;
        PlacedCrate placed = placedAt(event.getClickedBlock());
        if (placed == null) {
            CrateDefinition crate = crateFromBlock(event.getClickedBlock());
            if (crate == null) return;
            consumeCrateClick(event);
            if (event.getPlayer().isSneaking()) {
                openPreview(event.getPlayer(), crate.id());
                return;
            }
            openCrate(event.getPlayer(), crate.id());
            return;
        }
        consumeCrateClick(event);
        if (event.getPlayer().isSneaking()) {
            openPreview(event.getPlayer(), placed.crateId());
            return;
        }
        openCrate(event.getPlayer(), placed.crateId());
    }

    private void consumeCrateClick(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        PlacedCrate placed = placedAt(event.getBlock());
        CrateDefinition crate = placed == null ? crateFromBlock(event.getBlock()) : crates.get(placed.crateId());
        if (crate == null || !crate.protectBlock()) return;
        if (event.getPlayer().hasPermission("3smpcore.crates.admin") && event.getPlayer().isSneaking()) {
            if (placed != null) {
                removePlaced(placed.id());
                removeHologram(placed.id());
                Text.send(event.getPlayer(), "<green>Removed crate placement:</green> <white>" + placed.id() + "</white>");
            }
            return;
        }
        event.setCancelled(true);
        Text.send(event.getPlayer(), "<red>This crate is protected.</red> <gray>Sneak-break as an admin to remove it.</gray>");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        CrateDefinition crate = crateFromItem(item);
        if (crate == null) return;
        if (!event.getPlayer().hasPermission("3smpcore.crates.admin")) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>Only admins can place crate blocks.</red>");
            return;
        }
        String id = "crate_" + System.currentTimeMillis();
        Location loc = event.getBlockPlaced().getLocation();
        savePlaced(id, crate.id(), loc);
        Bukkit.getScheduler().runTask(plugin, () -> spawnPlaced(new PlacedCrate(id, crate.id(), loc)));
        Text.send(event.getPlayer(), "<green>Registered " + crate.displayName() + " <green>at this block.</green>");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrateHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (holder.mode() == CrateHolder.Mode.SELECTION) {
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String crateId = clicked.getItemMeta().getPersistentDataContainer().get(selectionCrateKey, PersistentDataType.STRING);
            if (crateId != null && !crateId.isBlank()) openPreview(player, crateId);
            return;
        }
        if (holder.mode() != CrateHolder.Mode.PREVIEW) return;
        if (event.getRawSlot() == 49) openCrate(player, holder.crateId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrateHolder holder)) return;
        if (holder.mode() != CrateHolder.Mode.OPENING) return;
        OpeningState state = openings.get(event.getPlayer().getUniqueId());
        if (state != null && !state.finished && event.getPlayer() instanceof Player player) completeOpening(player, state, false);
    }

    private void giveKeyCommand(CommandContext context) {
        if (!context.sender().hasPermission("3smpcore.crates.admin")) {
            Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        if (context.args().length < 2) {
            Text.send(context.sender(), "<red>Usage: /crate givekey <player> [amount] [crate]</red>");
            return;
        }
        boolean all = context.arg(1).equalsIgnoreCase("all") || context.arg(1).equalsIgnoreCase("*");
        Player target = all ? null : Bukkit.getPlayerExact(context.arg(1));
        if (!all && target == null) {
            Text.send(context.sender(), "<red>That player must be online.</red>");
            return;
        }
        String crateId = defaultCrateId();
        int amount = 1;
        if (context.args().length >= 4) {
            if (isWholeNumber(context.arg(2))) {
                amount = parseAmount(context.arg(2));
                crateId = context.arg(3).toLowerCase(Locale.ROOT);
            } else {
                crateId = context.arg(2).toLowerCase(Locale.ROOT);
                amount = parseAmount(context.arg(3));
            }
        } else if (context.args().length >= 3) {
            if (isWholeNumber(context.arg(2))) amount = parseAmount(context.arg(2));
            else crateId = context.arg(2).toLowerCase(Locale.ROOT);
        }
        if (all) giveKeyAll(context.sender(), crateId, amount);
        else giveKey(context.sender(), target, crateId, amount);
    }

    private void giveLegacyKeyCommand(CommandContext context) {
        if (!context.sender().hasPermission("3smpcore.crates.admin")) {
            Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        if (context.args().length < 4) {
            Text.send(context.sender(), "<red>Usage: /crate key give <player> <crate> [amount]</red>");
            return;
        }
        boolean all = context.arg(2).equalsIgnoreCase("all") || context.arg(2).equalsIgnoreCase("*");
        Player target = all ? null : Bukkit.getPlayerExact(context.arg(2));
        if (!all && target == null) {
            Text.send(context.sender(), "<red>That player must be online.</red>");
            return;
        }
        String crateId = defaultCrateId();
        int amount = 1;
        if (context.args().length >= 5) {
            if (isWholeNumber(context.arg(3))) {
                amount = parseAmount(context.arg(3));
                crateId = context.arg(4).toLowerCase(Locale.ROOT);
            } else {
                crateId = context.arg(3).toLowerCase(Locale.ROOT);
                amount = parseAmount(context.arg(4));
            }
        } else if (isWholeNumber(context.arg(3))) {
            amount = parseAmount(context.arg(3));
        } else {
            crateId = context.arg(3).toLowerCase(Locale.ROOT);
        }
        if (all) giveKeyAll(context.sender(), crateId, amount);
        else giveKey(context.sender(), target, crateId, amount);
    }

    private void giveKey(CommandSender sender, Player target, String crateId, int amount) {
        CrateDefinition crate = crates.get(crateId);
        if (crate == null) {
            Text.send(sender, "<red>Unknown crate.</red>");
            return;
        }
        give(target, keyItem(crate, amount));
        Text.send(sender, "<green>Gave " + amount + " " + plain(crate.displayName()) + " key(s) to " + target.getName() + ".</green>");
    }

    private void giveKeyAll(CommandSender sender, String crateId, int amount) {
        CrateDefinition crate = crates.get(crateId);
        if (crate == null) {
            Text.send(sender, "<red>Unknown crate.</red>");
            return;
        }
        int count = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            give(target, keyItem(crate, amount));
            Text.send(target, "<gradient:#f4cd2a:#7c3aed>Crate key received:</gradient> <white>" + amount + "x " + plain(crate.displayName()) + "</white>");
            count++;
        }
        Text.send(sender, "<green>Gave " + amount + " " + plain(crate.displayName()) + " key(s) to " + count + " online player(s).</green>");
    }

    private void giveCrateItemCommand(CommandContext context) {
        if (!context.sender().hasPermission("3smpcore.crates.admin")) {
            Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        if (context.args().length < 2) {
            Text.send(context.sender(), "<red>Usage: /crate give <player|all> [crate] [amount]</red>");
            return;
        }
        boolean all = context.arg(1).equalsIgnoreCase("all") || context.arg(1).equalsIgnoreCase("*");
        Player target = all ? null : Bukkit.getPlayerExact(context.arg(1));
        String crateId = defaultCrateId();
        int amount = 1;
        if (context.args().length >= 3) {
            if (isWholeNumber(context.arg(2))) amount = parseAmount(context.arg(2));
            else crateId = context.arg(2).toLowerCase(Locale.ROOT);
        }
        if (context.args().length >= 4) amount = parseAmount(context.arg(3));
        CrateDefinition crate = crates.get(crateId);
        if ((!all && target == null) || crate == null) {
            Text.send(context.sender(), "<red>Player or crate not found.</red>");
            return;
        }
        if (all) {
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                give(online, crateItem(crate, amount));
                count++;
            }
            Text.send(context.sender(), "<green>Gave crate placer to " + count + " online player(s).</green>");
        } else {
            give(target, crateItem(crate, amount));
            Text.send(context.sender(), "<green>Gave crate placer to " + target.getName() + ".</green>");
        }
    }

    private void placeCommand(CommandContext context) {
        if (!context.sender().hasPermission("3smpcore.crates.admin")) {
            Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        CrateDefinition crate = crates.get(context.args().length >= 2 ? context.arg(1).toLowerCase(Locale.ROOT) : defaultCrateId());
        if (crate == null) {
            Text.send(player, "<red>Unknown crate.</red>");
            return;
        }
        Location location = player.getLocation().getBlock().getLocation();
        location.setYaw(player.getLocation().getYaw());
        String id = "crate_" + System.currentTimeMillis();
        savePlaced(id, crate.id(), location);
        spawnPlaced(new PlacedCrate(id, crate.id(), location));
        Text.send(player, "<green>Placed " + crate.displayName() + "<green>.</green>");
    }

    private void removeCommand(CommandContext context) {
        if (!context.sender().hasPermission("3smpcore.crates.admin")) {
            Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        PlacedCrate nearest = placedCrates().stream()
                .filter(placed -> placed.location().getWorld() != null && placed.location().getWorld().equals(player.getWorld()))
                .filter(placed -> placed.location().distanceSquared(player.getLocation()) <= 36.0D)
                .min(Comparator.comparingDouble(placed -> placed.location().distanceSquared(player.getLocation())))
                .orElse(null);
        if (nearest == null) {
            Text.send(player, "<red>No crate block nearby.</red>");
            return;
        }
        removePlaced(nearest.id());
        removeHologram(nearest.id());
        removeCustomBlock(nearest.location());
        nearest.location().getBlock().setType(Material.AIR, false);
        spawnConfiguredCrates();
        Text.send(player, "<green>Removed nearest crate.</green>");
    }

    private void openPreview(Player player, String crateId) {
        CrateDefinition crate = crates.get(crateId.toLowerCase(Locale.ROOT));
        if (crate == null) {
            Text.send(player, "<red>Unknown crate.</red>");
            return;
        }
        Inventory inv = Bukkit.createInventory(new CrateHolder(crate.id(), CrateHolder.Mode.PREVIEW), PREVIEW_SIZE, inventoryTitle(crate, "preview-title", crate.displayName() + " <dark_gray>Rewards</dark_gray>"));
        for (int i = 0; i < Math.min(crate.rewards().size(), REWARD_SLOTS.length); i++) {
            inv.setItem(REWARD_SLOTS[i], rewardIcon(crate.rewards().get(i)));
        }
        inv.setItem(49, actionItem(Material.TRIPWIRE_HOOK, "<gradient:#f4cd2a:#eda323><bold>Open Crate</bold></gradient>", List.of(
                "<gray>Uses one crate key.</gray>",
                "<dark_gray>Click to roll the rewards.</dark_gray>"
        )));
        inv.setItem(45, tierItem(Material.NETHER_STAR, "<#facc15>Legendary</#facc15>", crate, "legendary"));
        inv.setItem(46, tierItem(Material.DIAMOND_SWORD, "<#38bdf8>Epic</#38bdf8>", crate, "epic"));
        inv.setItem(47, tierItem(Material.AMETHYST_SHARD, "<#c084fc>Rare</#c084fc>", crate, "rare"));
        inv.setItem(48, tierItem(Material.IRON_INGOT, "<#d1d5db>Common</#d1d5db>", crate, "common"));
        player.openInventory(inv);
    }

    private void openSelection(Player player) {
        Inventory inv = Bukkit.createInventory(new CrateHolder(defaultCrateId(), CrateHolder.Mode.SELECTION), PREVIEW_SIZE, inventoryTitle("selection-title", "<gradient:#f4cd2a:#7c3aed>Crates</gradient>"));
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        List<CrateDefinition> sorted = crates.values().stream()
                .sorted(Comparator.comparing(CrateDefinition::id))
                .toList();
        for (int i = 0; i < Math.min(sorted.size(), slots.length); i++) {
            inv.setItem(slots[i], selectionIcon(sorted.get(i)));
        }
        player.openInventory(inv);
    }

    private void openCrate(Player player, String crateId) {
        CrateDefinition crate = crates.get(crateId);
        if (crate == null) {
            Text.send(player, "<red>Unknown crate.</red>");
            return;
        }
        if (openings.containsKey(player.getUniqueId())) {
            Text.send(player, "<yellow>Your crate is already opening.</yellow>");
            return;
        }
        if (!takeKey(player, crate)) {
            Text.send(player, "<red>You need a " + plain(crate.displayName()) + " key.</red>");
            player.playSound(player.getLocation(), Sound.BLOCK_VAULT_REJECT_REWARDED_PLAYER, 0.8F, 1.1F);
            return;
        }
        CrateReward reward = roll(crate);
        Inventory inv = Bukkit.createInventory(new CrateHolder(crate.id(), CrateHolder.Mode.OPENING), OPENING_SIZE, inventoryTitle(crate, "opening-title", "<gradient:#f4cd2a:#7c3aed>Prophecy is choosing...</gradient>"));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.8F, 1.25F);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.1D, 0.0D), 60, 0.55D, 0.45D, 0.55D, 0.02D);
        startRoll(player, crate, reward, inv);
    }

    private void startRoll(Player player, CrateDefinition crate, CrateReward reward, Inventory inv) {
        List<ItemStack> wheel = new ArrayList<>();
        for (int i = 0; i < 4; i++) crate.rewards().forEach(r -> wheel.add(rewardIcon(r)));
        OpeningState state = new OpeningState(crate, reward, inv);
        openings.put(player.getUniqueId(), state);
        state.task = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finish(false);
                    return;
                }
                for (int i = 0; i < ROLL_SLOTS.length; i++) {
                    inv.setItem(ROLL_SLOTS[i], wheel.get((tick + i) % wheel.size()));
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.35F, 1.0F + Math.min(1.0F, tick * 0.02F));
                if (++tick >= openingTicks()) finish(true);
            }

            private void finish(boolean award) {
                cancel();
                if (award) completeOpening(player, state, true);
                else {
                    state.finished = true;
                    openings.remove(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 0L, openingIntervalTicks());
    }

    private void completeOpening(Player player, OpeningState state, boolean closeLater) {
        if (state == null || state.finished) return;
        state.finished = true;
        openings.remove(player.getUniqueId());
        if (state.task != null) state.task.cancel();
        state.inventory.setItem(WINNING_SLOT, rewardIcon(state.reward));
        giveReward(player, state.reward);
        announce(player, state.crate, state.reward);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0F, 0.75F);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.25F);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0.0D, 1.2D, 0.0D), 65, 0.65D, 0.65D, 0.65D, 0.08D);
        if (closeLater) Bukkit.getScheduler().runTaskLater(plugin, () -> player.closeInventory(), 45L);
    }

    private CrateReward roll(CrateDefinition crate) {
        double total = crate.rewards().stream().mapToDouble(CrateReward::chance).sum();
        double needle = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0D;
        for (CrateReward reward : crate.rewards()) {
            cursor += reward.chance();
            if (needle <= cursor) return reward;
        }
        return crate.rewards().get(crate.rewards().size() - 1);
    }

    private void giveReward(Player player, CrateReward reward) {
        for (String command : reward.commands()) {
            String resolved = command
                    .replace("%player%", player.getName())
                    .replace("{player}", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("%amount%", String.valueOf(Math.max(1, reward.amount())))
                    .replace("{amount}", String.valueOf(Math.max(1, reward.amount())));
            try {
                boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                if (!accepted) plugin.getLogger().warning("Crate reward command was not accepted: " + resolved);
            } catch (org.bukkit.command.CommandException ex) {
                plugin.getLogger().warning("Crate reward command failed: " + resolved + " (" + ex.getMessage() + ")");
            }
        }
        if (reward.giveItem() && reward.material() != Material.AIR) {
            ItemStack item = reward.itemsAdderId().isBlank() ? new ItemStack(reward.material(), Math.max(1, reward.amount())) : itemsAdder.item(reward.itemsAdderId(), reward.material());
            item.setAmount(Math.max(1, reward.amount()));
            applyEnchantments(item, reward.enchantments());
            if (reward.itemsAdderId().isBlank()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Text.mm(reward.display()));
                    if (!reward.lore().isEmpty()) meta.lore(reward.lore().stream().map(Text::mm).toList());
                    item.setItemMeta(meta);
                }
            }
            give(player, item);
        }
    }

    private void announce(Player player, CrateDefinition crate, CrateReward reward) {
        if (isBroadcastTier(reward.tier())) {
            String message = configs.get(CONFIG_PATH).getString("settings.broadcast-message",
                    "<dark_gray>[</dark_gray>{crate}<dark_gray>]</dark_gray> <gradient:#f4cd2a:#ffffff>{player}</gradient> <gray>opened destiny and found</gray> {reward} <dark_gray>(</dark_gray>{tier}<dark_gray>)</dark_gray>");
            Bukkit.getOnlinePlayers().forEach(target -> Text.raw(target, placeholders(message, player, crate, reward)));
        } else {
            Text.send(player, "<gray>You won</gray> " + reward.display() + " <gray>from " + crate.displayName() + "<gray>.</gray>");
        }
    }

    private String placeholders(String message, Player player, CrateDefinition crate, CrateReward reward) {
        return (message == null ? "" : message)
                .replace("{crate}", crate.displayName())
                .replace("%crate%", crate.displayName())
                .replace("{player}", player.getName())
                .replace("%player%", player.getName())
                .replace("{reward}", reward.display())
                .replace("%reward%", reward.display())
                .replace("{tier}", tierDisplay(reward.tier()))
                .replace("%tier%", tierDisplay(reward.tier()));
    }

    private boolean isBroadcastTier(String tier) {
        List<String> configured = configs.get(CONFIG_PATH).getStringList("settings.broadcast-tiers");
        if (configured.isEmpty()) configured = List.of("legendary", "epic");
        for (String value : configured) {
            if (value.equalsIgnoreCase(tier)) return true;
        }
        return false;
    }

    private boolean takeKey(Player player, CrateDefinition crate) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isKey(item, crate)) continue;
            int amount = item.getAmount();
            if (amount <= 1) player.getInventory().setItem(slot, null);
            else item.setAmount(amount - 1);
            player.updateInventory();
            return true;
        }
        return false;
    }

    private boolean isKey(ItemStack item, CrateDefinition crate) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        String marked = meta.getPersistentDataContainer().get(keyItemKey, PersistentDataType.STRING);
        if (crate.id().equalsIgnoreCase(marked)) return true;
        String itemsAdderId = itemsAdderId(item);
        if (matchesId(crate.keyItem(), itemsAdderId)) return true;
        if (!meta.hasDisplayName()) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).toLowerCase(Locale.ROOT);
        return name.contains("prophecy") && name.contains("key");
    }

    private CrateDefinition crateFromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String marked = meta.getPersistentDataContainer().get(crateItemKey, PersistentDataType.STRING);
        if (marked != null && !marked.isBlank()) return crates.get(marked.toLowerCase(Locale.ROOT));
        String itemsAdderId = itemsAdderId(item);
        if (itemsAdderId.isBlank()) return null;
        for (CrateDefinition crate : crates.values()) {
            if (matchesId(crate.blockItem(), itemsAdderId)) return crate;
        }
        return null;
    }

    private CrateDefinition crateFromBlock(Block block) {
        String blockId = itemsAdderBlockId(block);
        if (blockId.isBlank()) return null;
        for (CrateDefinition crate : crates.values()) {
            if (matchesId(crate.blockItem(), blockId)) return crate;
        }
        return null;
    }

    private ItemStack keyItem(CrateDefinition crate, int amount) {
        ItemStack item = itemsAdder.item(crate.keyItem(), Material.TRIPWIRE_HOOK).clone();
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        setFallbackName(meta, "<gradient:#f4cd2a:#7c3aed><bold>Prophecy Key</bold></gradient>");
        appendLore(meta, List.of(
                "<gray>Unlocks " + crate.displayName() + "<gray>.</gray>",
                "<dark_gray>Right-click a prophecy crate to open.</dark_gray>"
        ));
        meta.getPersistentDataContainer().set(keyItemKey, PersistentDataType.STRING, crate.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack crateItem(CrateDefinition crate, int amount) {
        ItemStack item = itemsAdder.item(crate.blockItem(), crate.fallbackBlock()).clone();
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        setFallbackName(meta, crate.displayName());
        appendLore(meta, List.of(
                "<gray>Admin crate block.</gray>",
                "<gray>Block item:</gray> <white>" + crate.blockItem() + "</white>",
                "<dark_gray>Place it or use /crate place " + crate.id() + ".</dark_gray>"
        ));
        meta.getPersistentDataContainer().set(crateItemKey, PersistentDataType.STRING, crate.id());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack selectionIcon(CrateDefinition crate) {
        ItemStack item = itemsAdder.item(crate.blockItem(), crate.fallbackBlock()).clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        setFallbackName(meta, crate.displayName());
        List<String> lore = new ArrayList<>(crate.description());
        lore.add("");
        lore.add("<gray>Rewards:</gray> <white>" + crate.rewards().size() + "</white>");
        lore.add("<#f4cd2a>Click</#f4cd2a> <gray>to preview this crate.</gray>");
        appendLore(meta, lore);
        meta.getPersistentDataContainer().set(selectionCrateKey, PersistentDataType.STRING, crate.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rewardIcon(CrateReward reward) {
        Material material = reward.material() == Material.AIR ? Material.PAPER : reward.material();
        boolean itemsAdderReward = !reward.itemsAdderId().isBlank();
        ItemStack item = itemsAdderReward ? itemsAdder.item(reward.itemsAdderId(), material).clone() : new ItemStack(material, Math.max(1, reward.amount()));
        item.setAmount(Math.max(1, reward.amount()));
        ItemMeta meta = item.getItemMeta();
        if (!itemsAdderReward) meta.displayName(Text.mm(reward.display()));
        else setFallbackName(meta, reward.display());
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Tier:</gray> " + tierColor(reward.tier()) + tierDisplay(reward.tier()));
        lore.add("<gray>Chance:</gray> <white>" + formatChance(reward.chance()) + "%</white>");
        if (!reward.enchantments().isEmpty()) lore.add("<gray>Enchantments:</gray> <white>" + reward.enchantments().size() + "</white>");
        lore.addAll(reward.lore());
        if (itemsAdderReward) appendLore(meta, lore);
        else meta.lore(lore.stream().map(Text::mm).toList());
        meta.getPersistentDataContainer().set(previewRewardKey, PersistentDataType.STRING, reward.id());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void setFallbackName(ItemMeta meta, String fallback) {
        if (meta == null || meta.hasDisplayName()) return;
        meta.displayName(Text.mm(fallback));
    }

    private void appendLore(ItemMeta meta, List<String> lines) {
        if (meta == null || lines == null || lines.isEmpty()) return;
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) lore.add(Component.empty());
        lore.addAll(lines.stream().filter(line -> line != null && !line.isBlank()).map(Text::mm).toList());
        meta.lore(lore);
    }

    private ItemStack tierItem(Material material, String name, CrateDefinition crate, String tier) {
        double total = crate.rewards().stream().filter(reward -> reward.tier().equalsIgnoreCase(tier)).mapToDouble(CrateReward::chance).sum();
        return actionItem(material, name, List.of("<gray>Total chance:</gray> <white>" + formatChance(total) + "%</white>"));
    }

    private Component inventoryTitle(CrateDefinition crate, String key, String fallback) {
        YamlConfiguration config = configs.get(CONFIG_PATH);
        String raw = config.getString("crates." + crate.id() + ".gui." + key, config.getString("settings.gui." + key, fallback));
        return renderInventoryTitle(raw, fallback);
    }

    private Component inventoryTitle(String key, String fallback) {
        YamlConfiguration config = configs.get(CONFIG_PATH);
        String raw = config.getString("settings.gui." + key, fallback);
        return renderInventoryTitle(raw, fallback);
    }

    private Component renderInventoryTitle(String raw, String fallback) {
        raw = replaceGuiSymbols(raw == null || raw.isBlank() ? fallback : raw);
        Component title = raw.contains("<") && raw.contains(">") ? Text.mm(raw) : LEGACY_AMPERSAND.deserialize(raw);
        return applyItemsAdderFontImages(title);
    }

    private String replaceGuiSymbols(String input) {
        String output = applyItemsAdderFontImages(input);
        ConfigurationSection crateSymbols = configs.get(CONFIG_PATH).getConfigurationSection("settings.gui.itemsadder-font-symbols");
        if (crateSymbols != null) {
            for (String key : crateSymbols.getKeys(false)) {
                output = output.replace(":" + key + ":", decodeUnicodeEscapes(crateSymbols.getString(key, "")));
            }
        }
        ConfigurationSection symbols = configs.get("menus/sapphires.yml").getConfigurationSection("menus.itemsadder-font-symbols");
        if (symbols != null) {
            for (String key : symbols.getKeys(false)) {
                output = output.replace(":" + key + ":", decodeUnicodeEscapes(symbols.getString(key, "")));
            }
        }
        return decodeUnicodeEscapes(output);
    }

    private String applyItemsAdderFontImages(String input) {
        if (input == null || input.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return input;
        try {
            Class<?> wrapper = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            Method replace = wrapper.getMethod("replaceFontImages", String.class);
            Object result = replace.invoke(null, input);
            return result instanceof String text ? text : input;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return input;
        }
    }

    private Component applyItemsAdderFontImages(Component input) {
        if (input == null || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return input;
        try {
            Class<?> wrapper = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            Method replace = wrapper.getMethod("replaceFontImages", Component.class);
            Object result = replace.invoke(null, input);
            return result instanceof Component component ? component : input;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return input;
        }
    }

    private String decodeUnicodeEscapes(String input) {
        StringBuilder output = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '\\' && i + 5 < input.length() && input.charAt(i + 1) == 'u') {
                String hex = input.substring(i + 2, i + 6);
                try {
                    output.append((char) Integer.parseInt(hex, 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
            output.append(current);
        }
        return output.toString();
    }

    private ItemStack actionItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv, Material material, String name) {
        ItemStack filler = actionItem(material, name, List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void spawnConfiguredCrates() {
        removeGeneratedEntities();
        removeGeneratedHolograms();
        for (PlacedCrate placed : placedCrates()) spawnPlaced(placed);
    }

    private void spawnPlaced(PlacedCrate placed) {
        CrateDefinition crate = crates.get(placed.crateId());
        if (crate == null || placed.location().getWorld() == null) return;
        Location base = placed.location();
        placeCrateBlock(crate, base);
        base.getWorld().spawn(base.clone().add(0.5D, 0.75D, 0.5D), Interaction.class, entity -> {
            entity.setInteractionWidth(1.4F);
            entity.setInteractionHeight(1.6F);
            markEntity(entity, crate.id(), placed.id());
        });
        List<String> lines = hologramLines(crate);
        Location holo = base.clone().add(0.5D, crate.hologramYOffset(), 0.5D);
        if (spawnDecentHologram(placed.id(), holo, lines)) return;
        for (int i = 0; i < lines.size(); i++) {
            int index = i;
            base.getWorld().spawn(holo.clone().subtract(0.0D, index * 0.27D, 0.0D), TextDisplay.class, display -> {
                display.text(Text.mm(lines.get(index)));
                display.setBillboard(Display.Billboard.CENTER);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setSeeThrough(false);
                display.setShadowed(false);
                display.setPersistent(false);
                markEntity(display, crate.id(), placed.id());
            });
        }
    }

    private void idleEffects() {
        Set<Location> centers = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                if (!interaction.getScoreboardTags().contains(ENTITY_TAG)) continue;
                centers.add(interaction.getLocation().clone().subtract(0.0D, 0.25D, 0.0D));
            }
        }
        for (Location loc : centers) {
            loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.0D, 1.05D, 0.0D), 5, 0.45D, 0.3D, 0.45D, 0.0D, new Particle.DustOptions(Color.fromRGB(124, 58, 237), 1.0F));
            if (ThreadLocalRandom.current().nextInt(4) == 0) loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.18F, 1.8F);
        }
    }

    private void removeGeneratedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(ENTITY_TAG)) entity.remove();
            }
        }
    }

    private void placeCrateBlock(CrateDefinition crate, Location location) {
        Block block = location.getBlock();
        if (isItemsAdderBlock(block, crate.blockItem())) return;
        if (placeCustomBlock(crate.blockItem(), location)) return;
        if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR || block.getType() == Material.VOID_AIR) {
            block.setType(crate.fallbackBlock(), false);
        }
    }

    private boolean placeCustomBlock(String id, Location location) {
        if (id == null || id.isBlank() || Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return false;
        try {
            Class<?> customBlock = Class.forName("dev.lone.itemsadder.api.CustomBlock");
            Object placed = customBlock.getMethod("place", String.class, Location.class).invoke(null, id, location);
            return placed != null;
        } catch (Throwable ex) {
            plugin.getLogger().fine("ItemsAdder crate block placement failed for " + id + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean removeCustomBlock(Location location) {
        if (location == null || Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return false;
        try {
            Class<?> customBlock = Class.forName("dev.lone.itemsadder.api.CustomBlock");
            Object result = customBlock.getMethod("remove", Location.class).invoke(null, location);
            return result instanceof Boolean value && value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isItemsAdderBlock(Block block, String id) {
        return matchesId(id, itemsAdderBlockId(block));
    }

    private boolean spawnDecentHologram(String placedId, Location location, List<String> lines) {
        org.bukkit.plugin.Plugin decent = Bukkit.getPluginManager().getPlugin("DecentHolograms");
        if (decent == null || !decent.isEnabled()) return false;
        String id = HOLOGRAM_PREFIX + placedId;
        try {
            removeHologram(placedId);
            Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            List<String> rendered = legacyLines(lines);
            try {
                Method create = api.getMethod("createHologram", String.class, Location.class, boolean.class, List.class);
                create.invoke(null, id, location, false, rendered);
            } catch (NoSuchMethodException ignored) {
                Method create = api.getMethod("createHologram", String.class, Location.class, List.class);
                create.invoke(null, id, location, rendered);
            }
            try {
                api.getMethod("setHologramLines", Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram"), List.class)
                        .invoke(null, api.getMethod("getHologram", String.class).invoke(null, id), rendered);
                api.getMethod("updateHologram", String.class).invoke(null, id);
            } catch (Throwable ignored) {
            }
            activeHolograms.add(id);
            plugin.getLogger().fine("Spawned DecentHolograms crate hologram " + id + " at " + location);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("DecentHolograms crate hologram failed for " + placedId + ": " + ex.getMessage());
            return false;
        }
    }

    private void removeHologram(String placedId) {
        org.bukkit.plugin.Plugin decent = Bukkit.getPluginManager().getPlugin("DecentHolograms");
        if (placedId == null || placedId.isBlank() || decent == null || !decent.isEnabled()) return;
        String id = placedId.startsWith(HOLOGRAM_PREFIX) ? placedId : HOLOGRAM_PREFIX + placedId;
        try {
            Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            api.getMethod("removeHologram", String.class).invoke(null, id);
        } catch (Throwable ignored) {
        }
        activeHolograms.remove(id);
    }

    private void removeGeneratedHolograms() {
        for (String id : new HashSet<>(activeHolograms)) removeHologram(id);
        for (PlacedCrate placed : placedCrates()) removeHologram(placed.id());
        activeHolograms.clear();
    }

    private List<String> legacyLines(List<String> lines) {
        return lines.stream().map(line -> LEGACY.serialize(Text.mm(line))).toList();
    }

    private List<String> hologramLines(CrateDefinition crate) {
        if (!crate.hologram().isEmpty()) return crate.hologram();
        List<String> lines = new ArrayList<>();
        lines.add(crate.displayName());
        if (crate.description().isEmpty()) {
            lines.add("<gray>A custom reward crate.</gray>");
        } else {
            lines.addAll(crate.description());
        }
        lines.add("<#f4cd2a>Right-click</#f4cd2a> <gray>to open</gray>");
        lines.add("<#c084fc>Shift Right-click</#c084fc> <gray>to preview</gray>");
        return lines;
    }

    private void markEntity(Entity entity, String crateId, String placedId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(crateEntityKey, PersistentDataType.STRING, crateId);
        pdc.set(new NamespacedKey(plugin, "placed_crate_id"), PersistentDataType.STRING, placedId);
        entity.addScoreboardTag(ENTITY_TAG);
        entity.setPersistent(false);
    }

    private String placedId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, "placed_crate_id"), PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    private CrateDefinition loadDefinition(YamlConfiguration config, String id) {
        String path = "crates." + id;
        Material fallback = material(config.getString(path + ".fallback-block", "ENDER_CHEST"), Material.ENDER_CHEST);
        List<CrateReward> rewards = new ArrayList<>();
        ConfigurationSection rewardRoot = config.getConfigurationSection(path + ".rewards");
        if (rewardRoot != null) {
            for (String rewardId : rewardRoot.getKeys(false)) {
                String rewardPath = path + ".rewards." + rewardId;
                rewards.add(new CrateReward(
                        rewardId,
                        config.getString(rewardPath + ".tier", "common").toLowerCase(Locale.ROOT),
                        Math.max(0.0D, config.getDouble(rewardPath + ".chance", 1.0D)),
                        config.getString(rewardPath + ".display", "<white>" + rewardId + "</white>"),
                        material(config.getString(rewardPath + ".material", "PAPER"), Material.PAPER),
                        Math.max(1, config.getInt(rewardPath + ".amount", 1)),
                        config.getString(rewardPath + ".itemsadder", ""),
                        config.getBoolean(rewardPath + ".give-item", true),
                        config.getStringList(rewardPath + ".commands"),
                        enchantments(config.getConfigurationSection(rewardPath + ".enchantments")),
                        config.getStringList(rewardPath + ".lore")
                ));
            }
        }
        rewards.removeIf(reward -> reward.chance() <= 0.0D);
        return new CrateDefinition(
                id,
                config.getString(path + ".display-name", "<gradient:#f4cd2a:#7c3aed><bold>Prophecy Crate</bold></gradient>"),
                config.getString(path + ".key-item", "threesmp:prophecy_key"),
                config.getString(path + ".block-item", "threesmp:prophecy_crate"),
                config.getString(path + ".model-engine-model", "prophecy_crate"),
                fallback,
                config.getDouble(path + ".hologram-y-offset", 2.35D),
                config.getBoolean(path + ".protect-block", true),
                config.getStringList(path + ".description"),
                config.getStringList(path + ".hologram"),
                rewards
        );
    }

    private void applyEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        if (item == null || enchantments == null || enchantments.isEmpty()) return;
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantment = enchantment(entry.getKey());
            if (enchantment == null) {
                plugin.getLogger().warning("Unknown crate enchantment: " + entry.getKey());
                continue;
            }
            item.addUnsafeEnchantment(enchantment, Math.max(1, entry.getValue()));
        }
    }

    private Map<String, Integer> enchantments(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, Integer> out = new HashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key.toLowerCase(Locale.ROOT), Math.max(1, section.getInt(key, 1)));
        }
        return out;
    }

    private Enchantment enchantment(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String key = raw.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace('-', '_');
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    private List<PlacedCrate> placedCrates() {
        List<PlacedCrate> placed = new ArrayList<>();
        YamlConfiguration config = configs.get(CONFIG_PATH);
        ConfigurationSection root = config.getConfigurationSection("placed");
        if (root == null) return placed;
        for (String id : root.getKeys(false)) {
            Location loc = location("placed." + id + ".location");
            String crateId = config.getString("placed." + id + ".crate", "prophecy");
            if (loc != null) placed.add(new PlacedCrate(id, crateId, loc));
        }
        return placed;
    }

    private PlacedCrate placedAt(Block block) {
        if (block == null) return null;
        for (PlacedCrate placed : placedCrates()) {
            Location loc = placed.location();
            if (loc.getWorld() == null || !loc.getWorld().equals(block.getWorld())) continue;
            if (loc.getBlockX() == block.getX() && loc.getBlockY() == block.getY() && loc.getBlockZ() == block.getZ()) return placed;
        }
        return null;
    }

    private Location location(String path) {
        YamlConfiguration config = configs.get(CONFIG_PATH);
        World world = Bukkit.getWorld(config.getString(path + ".world", "world"));
        if (world == null) return null;
        return new Location(world, config.getDouble(path + ".x"), config.getDouble(path + ".y"), config.getDouble(path + ".z"), (float) config.getDouble(path + ".yaw"), (float) config.getDouble(path + ".pitch"));
    }

    private void savePlaced(String id, String crateId, Location loc) {
        YamlConfiguration config = configs.get(CONFIG_PATH);
        String path = "placed." + id;
        config.set(path + ".crate", crateId);
        config.set(path + ".location.world", loc.getWorld().getName());
        config.set(path + ".location.x", round(loc.getX()));
        config.set(path + ".location.y", round(loc.getY()));
        config.set(path + ".location.z", round(loc.getZ()));
        config.set(path + ".location.yaw", (double) loc.getYaw());
        config.set(path + ".location.pitch", (double) loc.getPitch());
        save(config);
    }

    private void removePlaced(String id) {
        if (id.isBlank()) return;
        YamlConfiguration config = configs.get(CONFIG_PATH);
        config.set("placed." + id, null);
        save(config);
    }

    private void save(YamlConfiguration config) {
        try {
            config.save(new File(plugin.getDataFolder(), CONFIG_PATH));
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save crates config: " + ex.getMessage());
        }
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.updateInventory();
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

    private String itemsAdderBlockId(Block block) {
        if (block == null || Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return "";
        try {
            Class<?> customBlock = Class.forName("dev.lone.itemsadder.api.CustomBlock");
            Object placed = customBlock.getMethod("byAlreadyPlaced", Block.class).invoke(null, block);
            if (placed == null) return "";
            for (String method : List.of("getNamespacedID", "getNamespacedId", "getId", "getName")) {
                try {
                    Object value = placed.getClass().getMethod(method).invoke(placed);
                    if (value != null) return value.toString();
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private boolean matchesId(String expected, String actual) {
        if (expected == null || expected.isBlank() || actual == null || actual.isBlank()) return false;
        String left = expected.toLowerCase(Locale.ROOT);
        String right = actual.toLowerCase(Locale.ROOT);
        return left.equals(right) || bareId(left).equals(bareId(right));
    }

    private String bareId(String id) {
        int index = id.indexOf(':');
        return index >= 0 ? id.substring(index + 1) : id;
    }

    private List<String> crateIds() {
        return new ArrayList<>(crates.keySet());
    }

    private String defaultCrateId() {
        String configured = configs.get(CONFIG_PATH).getString("settings.default-crate", "prophecy");
        return configured == null || configured.isBlank() ? "prophecy" : configured.toLowerCase(Locale.ROOT);
    }

    private int openingTicks() {
        return Math.max(12, configs.get(CONFIG_PATH).getInt("settings.opening.ticks", 45));
    }

    private long openingIntervalTicks() {
        return Math.max(1L, configs.get(CONFIG_PATH).getLong("settings.opening.interval-ticks", 2L));
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }

    private int parseAmount(String input) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(input)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private boolean isWholeNumber(String input) {
        if (input == null || input.isBlank()) return false;
        for (int i = 0; i < input.length(); i++) {
            if (!Character.isDigit(input.charAt(i))) return false;
        }
        return true;
    }

    private void circulationCommand(CommandSender sender) {
        if (!sender.hasPermission("3smpcore.crates.admin")) {
            Text.send(sender, "<red>No permission.</red>");
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            scanInventory(player.getInventory(), counts);
            scanInventory(player.getEnderChest(), counts);
        }
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) scanInventory(container.getInventory(), counts);
                }
            }
        }
        if (counts.isEmpty()) {
            Text.send(sender, "<yellow>No crate items were found in online inventories, ender chests, or loaded containers.</yellow>");
            return;
        }
        Text.send(sender, "<gradient:#f4cd2a:#7c3aed>Crate circulation</gradient> <gray>(loaded server state)</gray>");
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(25)
                .forEach(entry -> Text.send(sender, "<gray>" + entry.getKey() + ":</gray> <white>" + entry.getValue() + "</white>"));
    }

    private void scanInventory(Inventory inventory, Map<String, Integer> counts) {
        if (inventory == null) return;
        for (ItemStack item : inventory.getContents()) {
            String id = crateItemLabel(item);
            if (id.isBlank()) continue;
            counts.merge(id, item.getAmount(), Integer::sum);
        }
    }

    private String crateItemLabel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return "";
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String key = data.get(keyItemKey, PersistentDataType.STRING);
        if (key != null && !key.isBlank()) return labelForCrate(key) + " Key";
        String crate = data.get(crateItemKey, PersistentDataType.STRING);
        if (crate != null && !crate.isBlank()) return labelForCrate(crate) + " Crate Block";
        String itemsAdderId = itemsAdderId(item);
        if (!itemsAdderId.isBlank()) {
            for (CrateDefinition definition : crates.values()) {
                if (matchesId(definition.keyItem(), itemsAdderId)) return plain(definition.displayName()) + " Key";
                if (matchesId(definition.blockItem(), itemsAdderId)) return plain(definition.displayName()) + " Crate Block";
                for (CrateReward reward : definition.rewards()) {
                    if (!reward.itemsAdderId().isBlank() && matchesId(reward.itemsAdderId(), itemsAdderId)) return plain(reward.display());
                }
            }
        }
        for (CrateDefinition definition : crates.values()) {
            for (CrateReward reward : definition.rewards()) {
                if (reward.itemsAdderId().isBlank() && reward.material() == item.getType() && meta.hasDisplayName() && plain(reward.display()).equalsIgnoreCase(PlainTextComponentSerializer.plainText().serialize(meta.displayName()))) {
                    return plain(reward.display());
                }
            }
        }
        return "";
    }

    private String labelForCrate(String crateId) {
        CrateDefinition crate = crates.get(crateId.toLowerCase(Locale.ROOT));
        return crate == null ? tierDisplay(crateId) : plain(crate.displayName());
    }

    private String tierColor(String tier) {
        return switch (tier.toLowerCase(Locale.ROOT)) {
            case "legendary" -> "<#facc15>";
            case "epic" -> "<#38bdf8>";
            case "rare" -> "<#c084fc>";
            default -> "<#d1d5db>";
        };
    }

    private String tierDisplay(String tier) {
        if (tier == null || tier.isBlank()) return "Common";
        String normalized = tier.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : normalized.split(" ")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.isEmpty() ? "Common" : out.toString();
    }

    private String formatChance(double chance) {
        if (chance == Math.rint(chance)) return String.format(Locale.ROOT, "%.0f", chance);
        return String.format(Locale.ROOT, "%.1f", chance);
    }

    private String plain(String miniMessage) {
        Component component = Text.mm(miniMessage);
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static final class OpeningState {
        private final CrateDefinition crate;
        private final CrateReward reward;
        private final Inventory inventory;
        private BukkitTask task;
        private boolean finished;

        private OpeningState(CrateDefinition crate, CrateReward reward, Inventory inventory) {
            this.crate = crate;
            this.reward = reward;
            this.inventory = inventory;
        }
    }
}
