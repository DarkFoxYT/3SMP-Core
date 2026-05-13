package net.dark.threecore.gems;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.ConfigurationSection;

public final class GemService implements Listener {
    private static final String GEM_KEY = "3smpcore_gem_id";
    private static final String GEM_SOCKETS_KEY = "3smpcore_gem_sockets";
    private static final String EXTRACTOR_KEY = "3smpcore_gem_extractor";

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MenuService menuService;
    private final SeasonalGemRegistry registry;
    private final Map<String, GemDefinition> gems = new LinkedHashMap<>();
    private final Map<UUID, GemUiState> uiState = new HashMap<>();
    private final Map<UUID, Map<String, Long>> procCooldowns = new HashMap<>();

    public GemService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService, SeasonalGemRegistry registry) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
        this.registry = registry;
        reload();
    }

    public void reload() {
        gems.clear();
        var section = configs.get("gems/gems.yml").getConfigurationSection("gems.definitions");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                var gem = section.getConfigurationSection(id);
                if (gem == null) continue;
                gems.put(id.toLowerCase(Locale.ROOT), new GemDefinition(
                        id.toLowerCase(Locale.ROOT),
                        gem.getString("display-name", id),
                        parseMaterial(gem.getString("icon", "AMETHYST_SHARD")),
                        gem.getStringList("lore"),
                        gem.getString("effect", ""),
                        gem.getInt("value", 1),
                        gem.getDouble("proc-chance", gem.getDouble("procChance", 0.0)),
                        gem.getLong("cooldown-ticks", gem.getLong("cooldownTicks", 0L))
                ));
            }
        }
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player commandPlayer && !enabledInWorld(commandPlayer)) { Text.send(commandPlayer, "<red>Gems are disabled in this world.</red>"); return; }
        if (args.length == 0) {
            if (sender instanceof Player player) openMenu(player);
            else Text.send(sender, "<red>Players only.</red>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { reload(); Text.send(sender, "<green>Gem module reloaded.</green>"); }
            case "info" -> Text.send(sender, "<gray>Gem system supports seasonal registries, proc chances, cooldowns, and an in-game combine/extract UI.</gray>");
            case "stats" -> { if (sender instanceof Player player) openStatsMenu(player, player.getInventory().getItemInMainHand()); else Text.send(sender, "<red>Players only.</red>"); }
            case "giveextractor" -> { if (!sender.hasPermission("3smpcore.gems.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 3) { Text.send(sender, "<red>Usage: /gems giveextractor <player> <amount></red>"); return; } Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { Text.send(sender, "<red>Player not found.</red>"); return; } giveExtractor(target, parseInt(args[2])); Text.send(sender, "<green>Gave extractor(s) to " + target.getName() + ".</green>"); }
            case "givecapsule" -> { if (!sender.hasPermission("3smpcore.gems.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 4) { Text.send(sender, "<red>Usage: /gems givecapsule <player> <tier> <amount></red>"); return; } Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { Text.send(sender, "<red>Player not found.</red>"); return; } giveCapsules(target, args[2], parseInt(args[3])); Text.send(sender, "<green>Gave capsule(s) to " + target.getName() + ".</green>"); }
            case "givegem" -> { if (!sender.hasPermission("3smpcore.gems.admin")) { Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 3) { Text.send(sender, "<red>Usage: /gems givegem <player> <gemId></red>"); return; } Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { Text.send(sender, "<red>Player not found.</red>"); return; } giveGem(target, args[2]); Text.send(sender, "<green>Given gem.</green>"); }
            case "extract" -> { if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; } if (player.getInventory().getItemInMainHand().isEmpty()) { Text.send(player, "<red>Hold a socketed item first.</red>"); return; } if (!tryExtract(player, player.getInventory().getItemInMainHand(), false)) Text.send(player, "<red>No gem found on that item.</red>"); }
            case "combine" -> { if (sender instanceof Player player) openCombineMenu(player); else Text.send(sender, "<red>Players only.</red>"); }
            case "browse" -> { if (sender instanceof Player player) openGemBrowse(player); else Text.send(sender, "<red>Players only.</red>"); }
            case "capsules", "capsule" -> { if (sender instanceof Player player) openCapsuleShop(player); else Text.send(sender, "<red>Players only.</red>"); }
            default -> { if (sender instanceof Player player) openMenu(player); else Text.send(sender, "<gray>Use /gems info, /gems reload, /gems stats, /gems giveextractor <player> <amount>, /gems givegem <player> <gemId>, /gems combine, /gems browse, /gems capsules, or /gems extract.</gray>"); }
        }
    }

    public void openMenu(Player player) { menuService.open(player, buildMenu()); }

    public Inventory buildMenu() {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.GEMS_MAIN, "main"), 27, "3SMP Gems");
        fill(inv, pane());
        inv.setItem(10, button(Material.DIAMOND_SWORD, "<gradient:#f4cd2a:#eda323:#d28d0d>Combine</gradient>", List.of("<gray>Socket a gem into a supported item.</gray>", "<gray>Choose a gem and a base item.</gray>")));
        inv.setItem(13, button(Material.ANVIL, "<gradient:#f4cd2a:#eda323:#d28d0d>Extractor</gradient>", List.of("<gray>Remove a gem safely if possible.</gray>", "<gray>Shift-right click a socketed item.</gray>")));
        inv.setItem(16, button(Material.BOOK, "<gradient:#f4cd2a:#eda323:#d28d0d>Gem Types</gradient>", List.of("<gray>Browse seasonal gems.</gray>")));
        inv.setItem(19, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d>Gem Stats</gradient>", List.of("<gray>Inspect your socketed gems.</gray>", "<gray>Shows effects, tiers, proc chance, and cooldowns.</gray>")));
        inv.setItem(22, button(Material.ENDER_CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Gem Capsules</gradient>", List.of("<gray>Spend sapphires on capsule rolls.</gray>", "<gray>Prismatic gems are excluded.</gray>")));
        return inv;
    }

    public void handleMenuClick(Player player, int slot) {
        if (slot == 10) openCombineMenu(player);
        else if (slot == 13) Text.send(player, "<gray>Shift-right click a socketed item with a Gem Extractor to remove its gem.</gray>");
        else if (slot == 16) openGemBrowse(player);
        else if (slot == 19) openStatsMenu(player, player.getInventory().getItemInMainHand());
        else if (slot == 22) openCapsuleShop(player);
    }

    public Inventory buildCombineMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.GEMS_MAIN, "combine"), 27, "3SMP Gem Combine");
        fill(inv, pane());
        GemUiState state = uiState.computeIfAbsent(player.getUniqueId(), k -> new GemUiState());
        inv.setItem(11, state.baseItem == null ? button(Material.DIAMOND_SWORD, "<gradient:#f4cd2a:#eda323:#d28d0d>Base Item</gradient>", List.of("<gray>Click to use the item in your main hand.</gray>")) : state.baseItem.clone());
        inv.setItem(15, state.selectedGem == null ? button(Material.AMETHYST_SHARD, "<gradient:#f4cd2a:#eda323:#d28d0d>Select Gem</gradient>", List.of("<gray>Click a gem type in your inventory, or browse the gem list.</gray>")) : state.selectedGem.clone());
        inv.setItem(22, button(Material.LIME_DYE, "<green>Combine</green>", List.of("<gray>Apply the selected gem to the base item.</gray>")));
        inv.setItem(26, button(Material.BOOK, "<gradient:#f4cd2a:#eda323:#d28d0d>Browse Gems</gradient>", List.of("<gray>Pick a gem from the configured registry.</gray>")));
        return inv;
    }

    public void handleCombineClick(Player player, int slot) {
        GemUiState state = uiState.computeIfAbsent(player.getUniqueId(), k -> new GemUiState());
        if (slot == 11) {
            state.baseItem = player.getInventory().getItemInMainHand() == null ? null : player.getInventory().getItemInMainHand().clone();
            Text.send(player, "<green>Base item set.</green>");
            menuService.open(player, buildCombineMenu(player));
        } else if (slot == 15) {
            state.selectedGem = firstGem(player);
            Text.send(player, state.selectedGem == null ? "<red>No gem found in inventory.</red>" : "<green>Gem selected.</green>");
            menuService.open(player, buildCombineMenu(player));
        } else if (slot == 22) {
            combine(player, state);
        } else if (slot == 26) {
            openGemBrowse(player);
        }
    }

    public void combine(Player player, GemUiState state) {
        if (state.baseItem == null || state.baseItem.getType().isAir()) { Text.send(player, "<red>Select a base item first.</red>"); return; }
        if (state.selectedGem == null) { Text.send(player, "<red>Select a gem first.</red>"); return; }
        if (!registry.supports(state.baseItem.getType())) { Text.send(player, "<red>That item cannot be socketed.</red>"); return; }
        String gemId = selectedGemId(state.selectedGem);
        if (gemId == null) { Text.send(player, "<red>That gem item is invalid.</red>"); return; }
        List<String> sockets = new ArrayList<>(socketIds(state.baseItem));
        if (sockets.size() >= slotsPerItem()) { Text.send(player, "<red>This item has no empty gem slots.</red>"); return; }
        if (sockets.contains(gemId.toLowerCase(Locale.ROOT))) { Text.send(player, "<red>This item already has that gem.</red>"); return; }
        sockets.add(gemId.toLowerCase(Locale.ROOT));
        applySockets(state.baseItem, sockets);
        player.getInventory().setItemInMainHand(state.baseItem);
        consumeOne(player, state.selectedGem);
        Text.send(player, "<green>Gem socketed successfully.</green>");
        player.getWorld().spawnParticle(registry.particle(gemId), player.getLocation().add(0, 1, 0), 20, 0.25, 0.4, 0.25, 0.02);
        state.selectedGem = null;
        uiState.put(player.getUniqueId(), state);
    }

    public void giveExtractor(Player player, int amount) { for (int i = 0; i < amount; i++) player.getInventory().addItem(extractorItem()); }
    public void giveGem(Player player, String id) { ItemStack item = gemItem(id); if (item != null) player.getInventory().addItem(item); }
    public void giveCapsules(Player player, String tier, int amount) { for (int i = 0; i < amount; i++) player.getInventory().addItem(capsuleItem(tier)); }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabledInWorld(event.getPlayer())) return;
        if (event.getItem() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isExtractor(event.getItem())) return;
        if (!event.getPlayer().isSneaking()) return;
        ItemStack target = event.getPlayer().getInventory().getItemInMainHand();
        if (socketIds(target).isEmpty()) target = event.getPlayer().getInventory().getItemInOffHand();
        if (tryExtract(event.getPlayer(), target, true)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!enabledInWorld(player)) return;
        List<String> sockets = socketIds(player.getInventory().getItemInMainHand());
        if (sockets.isEmpty()) return;
        double bonus = 0.0;
        for (String gemId : sockets) {
            if (!isOffCooldown(player.getUniqueId(), gemId)) continue;
            GemDefinition def = gems.get(gemId.toLowerCase(Locale.ROOT));
            if (def == null) continue;
            if (def.procChance > 0.0 && Math.random() > def.procChance) continue;
            double scaled = def.value() * registry.scale(gemId);
            switch (def.effect().toLowerCase(Locale.ROOT)) {
                case "increase_damage" -> bonus += Math.max(0.0, scaled) * 0.8;
                case "lifesteal" -> healPlayer(player, 0.02 * scaled);
                case "increase_bow_power" -> bonus += Math.max(0.0, scaled) * 0.55;
                case "increase_speed", "increase_health", "increase_mining_speed" -> {
                    // passive effects are handled elsewhere
                }
                default -> { }
            }
            pushCooldown(player.getUniqueId(), gemId, def.cooldownTicks() > 0 ? def.cooldownTicks() : Math.max(20L, 20L * (long) Math.ceil(registry.scale(gemId))));
            player.getWorld().spawnParticle(registry.particle(gemId), player.getLocation().add(0, 1, 0), 14, 0.3, 0.4, 0.3, 0.02);
        }
        if (bonus > 0.0) event.setDamage(event.getDamage() + bonus);
        if (player.getAttribute(Attribute.ATTACK_DAMAGE) != null) event.setDamage(event.getDamage() + Math.max(0.0, player.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue()) * 0.02);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorMove(PlayerInteractEvent event) {
        if (event.getPlayer() == null || !enabledInWorld(event.getPlayer())) return;
        applyPassiveEffects(event.getPlayer());
    }

    private boolean enabledInWorld(Player player) {
        String world = player.getWorld().getName();
        java.util.List<String> disabled = configs.get("gems/gems.yml").getStringList("gems.disabled-worlds");
        return disabled.stream().noneMatch(w -> w.equalsIgnoreCase(world));
    }

    public boolean tryExtract(Player player, ItemStack targetItem, boolean useExtractor) {
        if (targetItem == null || !targetItem.hasItemMeta()) return false;
        ItemMeta meta = targetItem.getItemMeta();
        List<String> sockets = new ArrayList<>(socketIds(targetItem));
        if (sockets.isEmpty()) return false;
        String gemId = sockets.remove(sockets.size() - 1);
        applySockets(targetItem, sockets);
        if (useExtractor) player.getInventory().addItem(gemItem(gemId));
        Text.send(player, useExtractor ? "<green>Gem extracted and returned.</green>" : "<red>Gem destroyed.</red>");
        player.getWorld().spawnParticle(registry.particle(gemId), player.getLocation().add(0, 1, 0), 20, 0.25, 0.4, 0.25, 0.02);
        return true;
    }

    public void openGemBrowse(Player player) { menuService.open(player, buildGemBrowse(player)); }
    public void openCapsuleShop(Player player) { menuService.open(player, buildCapsuleShop(player)); }
    public Inventory buildGemBrowse(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.GEMS_MAIN, "browse"), 54, "3SMP Gem Types");
        fill(inv, pane());
        int slot = 10;
        for (GemDefinition def : gems.values()) {
            inv.setItem(slot, gemItem(def.id()));
            slot++;
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
            if (slot >= 45) break;
        }
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to gem menu.</gray>")));
        return inv;
    }

    public void handleBrowseClick(Player player, int slot) {
        if (slot == 49) { openMenu(player); return; }
        GemDefinition def = gemBySlot(slot);
        if (def == null) return;
        uiState.computeIfAbsent(player.getUniqueId(), k -> new GemUiState()).selectedGem = gemItem(def.id());
        Text.send(player, "<green>Selected gem: " + def.displayName() + "</green>");
        openCombineMenu(player);
    }

    public Inventory buildCapsuleShop(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.GEMS_MAIN, "capsules"), 54, "3SMP Gem Capsules");
        fill(inv, pane());
        inv.setItem(4, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d>Gem Capsule Shop</gradient>", List.of("<gray>Roll random gems by tier.</gray>", "<red>Prismatic gems are not inside capsules.</red>", "<gray>Balance:</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + repository.getSapphireBalance(player.getUniqueId()) + " Sapphires</gradient>")));
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        int index = 0;
        ConfigurationSection section = configs.get("gems/capsules.yml").getConfigurationSection("capsules");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (index >= slots.length) break;
                inv.setItem(slots[index++], capsuleButton(id));
            }
        }
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to gem menu.</gray>")));
        return inv;
    }

    public void handleCapsuleClick(Player player, int slot) {
        if (slot == 49) { openMenu(player); return; }
        String capsule = capsuleIdBySlot(slot);
        if (capsule != null) buyCapsule(player, capsule);
    }

    public void openCombineMenu(Player player) { menuService.open(player, buildCombineMenu(player)); }

    public void openStatsMenu(Player player, ItemStack item) { menuService.open(player, buildStatsMenu(player, item)); }

    public Inventory buildStatsMenu(Player player, ItemStack item) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.GEMS_STATS, "stats"), 54, "3SMP Gem Stats");
        fill(inv, pane());
        ItemStack showcase = item == null || item.getType().isAir() ? button(Material.BARRIER, "<red>No item selected</red>", List.of("<gray>Hold a socketed item in your main hand.</gray>")) : item.clone();
        inv.setItem(13, showcase);
        inv.setItem(4, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d>Current Season</gradient>", List.of("<gray>Season:</gray> <white>" + registry.currentSeason() + "</white>", "<gray>Registry:</gray> <white>" + registry.gemIds().size() + " base gem(s)</white>")));
        List<String> sockets = socketIds(item);
        for (int i = 0; i < 3; i++) {
            int slot = 20 + (i * 2);
            if (i < sockets.size()) {
                String gemId = sockets.get(i);
                GemDefinition def = gems.get(gemId);
                inv.setItem(slot, statGemIcon(gemId, def));
            } else {
                inv.setItem(slot, button(Material.GRAY_DYE, "<gray>Empty Socket</gray>", List.of("<gray>No gem assigned to this slot.</gray>")));
            }
        }
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to gem menu.</gray>")));
        return inv;
    }

    public void handleStatsClick(Player player, int slot) {
        if (slot == 49) openMenu(player);
        else if (slot == 13) Text.send(player, "<gray>Hold an item with gems in your main hand to inspect it.</gray>");
    }

    private ItemStack statGemIcon(String gemId, GemDefinition def) {
        ItemStack icon = gemItem(gemId);
        ItemMeta meta = icon.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Effect:</gray> <white>" + registry.effect(gemId) + "</white>");
        lore.add("<gray>Tier:</gray> <white>" + registry.tier(gemId) + "</white>");
        lore.add("<gray>Power:</gray> <white>" + registry.value(gemId) + "</white>");
        lore.add("<gray>Proc chance:</gray> <white>" + Math.round(registry.procChance(gemId) * 100.0) + "%</white>");
        lore.add("<gray>Cooldown:</gray> <white>" + (registry.cooldownTicks(gemId) / 20L) + "s</white>");
        lore.add("<gray>Particle:</gray> <white>" + registry.particle(gemId).name() + "</white>");
        lore.add("<gray>Summary:</gray> <white>" + registry.statSummary(gemId) + "</white>");
        meta.lore(lore.stream().map(Text::mm).toList());
        icon.setItemMeta(meta);
        return icon;
    }

    private void buyCapsule(Player player, String capsuleId) {
        var sec = configs.get("gems/capsules.yml").getConfigurationSection("capsules." + capsuleId);
        if (sec == null) {
            Text.send(player, "<red>That capsule is not configured.</red>");
            return;
        }
        String currency = sec.getString("currency", "sapphires");
        long price = sec.getLong("price", 0L);
        if (!takeCurrency(player.getUniqueId(), currency, price)) {
            Text.send(player, "<red>You cannot afford that capsule.</red>");
            return;
        }
        String rewardTier = sec.getString("tier", capsuleId);
        String gemId = rollGemByTier(rewardTier);
        if (gemId == null) {
            Text.send(player, "<red>No gems are available for that capsule.</red>");
            return;
        }
        giveGem(player, gemId);
        Text.send(player, "<green>You received a <white>" + registry.displayName(gemId) + "</white> gem.</green>");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.getWorld().spawnParticle(registry.particle(gemId), player.getLocation().add(0, 1, 0), 18, 0.3, 0.4, 0.3, 0.02);
    }

    private String rollGemByTier(String capsuleTier) {
        String tier = capsuleTier == null ? "" : capsuleTier.toLowerCase(Locale.ROOT);
        List<String> candidates = registry.statsSnapshot().entrySet().stream()
                .filter(entry -> tier.equals("mystery") || entry.getValue().tier().equalsIgnoreCase(tier))
                .filter(entry -> !entry.getValue().tier().equalsIgnoreCase("prismatic"))
                .map(Map.Entry::getKey)
                .toList();
        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean takeCurrency(UUID uuid, String currency, long amount) {
        if (currency.equalsIgnoreCase("money")) {
            double current = repository.getMoneyBalance(uuid);
            if (current < amount) return false;
            repository.setMoneyBalance(uuid, current - amount);
            return true;
        }
        long current = repository.getSapphireBalance(uuid);
        if (current < amount) return false;
        repository.setSapphireBalance(uuid, current - amount);
        return true;
    }

    private ItemStack firstGem(Player player) { for (ItemStack item : player.getInventory().getContents()) { String id = selectedGemId(item); if (id != null) return item.clone(); } return null; }
    private String selectedGemId(ItemStack item) { if (item == null || !item.hasItemMeta()) return null; var pdc = item.getItemMeta().getPersistentDataContainer(); String legacy = pdc.get(keyGem(), PersistentDataType.STRING); if (legacy != null && !legacy.isBlank()) return legacy.toLowerCase(Locale.ROOT); List<String> sockets = parseSockets(pdc.get(keySockets(), PersistentDataType.STRING)); return sockets.isEmpty() ? null : sockets.get(sockets.size() - 1); }
    private List<String> socketIds(ItemStack item) { if (item == null || !item.hasItemMeta()) return List.of(); var pdc = item.getItemMeta().getPersistentDataContainer(); List<String> sockets = parseSockets(pdc.get(keySockets(), PersistentDataType.STRING)); if (!sockets.isEmpty()) return sockets; String legacy = pdc.get(keyGem(), PersistentDataType.STRING); if (legacy == null || legacy.isBlank()) return List.of(); return List.of(legacy.toLowerCase(Locale.ROOT)); }
    private void applySockets(ItemStack item, List<String> sockets) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore().stream().map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)).toList());
        lore.removeIf(line -> line.startsWith("Gem: "));
        for (String socket : sockets) lore.add("Gem: " + socket);
        meta.lore(lore.stream().map(Text::mm).toList());
        var pdc = meta.getPersistentDataContainer();
        pdc.remove(keyGem());
        if (sockets.isEmpty()) pdc.remove(keySockets()); else pdc.set(keySockets(), PersistentDataType.STRING, String.join(",", sockets));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
    }
    private void consumeOne(Player player, ItemStack item) { if (item == null) return; int amount = item.getAmount(); if (amount <= 1) player.getInventory().removeItem(item); else item.setAmount(amount - 1); }
    private ItemStack gemItem(String id) { GemDefinition def = gems.get(id.toLowerCase(Locale.ROOT)); if (def == null) return null; ItemStack item = new ItemStack(def.material()); ItemMeta meta = item.getItemMeta(); meta.displayName(Text.mm(def.displayName())); List<String> lore = new ArrayList<>(def.lore()); lore.add("<gray>Effect:</gray> <white>" + def.effect() + "</white>"); lore.add("<gray>Tier:</gray> <white>" + registry.tier(def.id()) + "</white>"); lore.add("<gray>Proc chance:</gray> <white>" + Math.round(def.procChance() * 100.0) + "%</white>"); lore.add("<gray>Cooldown:</gray> <white>" + (def.cooldownTicks() / 20L) + "s</white>"); meta.lore(lore.stream().map(Text::mm).toList()); meta.getPersistentDataContainer().set(keyGem(), PersistentDataType.STRING, def.id()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item; }
    private ItemStack capsuleButton(String id) { var sec = configs.get("gems/capsules.yml").getConfigurationSection("capsules." + id); Material material = parseMaterial(sec == null ? "ENDER_CHEST" : sec.getString("icon.material", "ENDER_CHEST")); String name = sec == null ? id : sec.getString("display-name", id); ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(Text.mm(name)); List<String> lines = new ArrayList<>(); String tier = sec == null ? id : sec.getString("tier", id); lines.add("<gray>Tier:</gray> <white>" + tier + "</white>"); lines.add("<gray>Reward:</gray> <white>Random non-prismatic gem</white>"); if (sec != null) { lines.add("<gray>Currency:</gray> <white>" + sec.getString("currency", "sapphires") + "</white>"); lines.add("<gray>Price:</gray> <gradient:#22d3ee:#a78bfa>" + sec.getLong("price", 0L) + "</gradient>"); } lines.add("<yellow>Click to purchase.</yellow>"); meta.lore(lines.stream().map(Text::mm).toList()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item; }
    private String capsuleIdBySlot(int slot) { int[] slots = {10, 12, 14, 16, 28, 30, 32, 34}; ConfigurationSection section = configs.get("gems/capsules.yml").getConfigurationSection("capsules"); if (section == null) return null; int index = 0; for (String id : section.getKeys(false)) { if (index >= slots.length) return null; if (slots[index] == slot) return id; index++; } return null; }
    private ItemStack capsuleItem(String tier) { ItemStack item = new ItemStack(Material.ENDER_CHEST); ItemMeta meta = item.getItemMeta(); meta.displayName(Text.mm("<gradient:#06b6d4:#8b5cf6>" + tier.substring(0, 1).toUpperCase(Locale.ROOT) + tier.substring(1).toLowerCase(Locale.ROOT) + " Gem Capsule</gradient>")); meta.lore(List.of(Text.mm("<gray>Opens to a random gem of the selected tier.</gray>"), Text.mm("<gray>Prismatic gems are not in capsules.</gray>"))); meta.getPersistentDataContainer().set(keyGem(), PersistentDataType.STRING, "capsule:" + tier.toLowerCase(Locale.ROOT)); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item; }
    private double damageBonus(Player player) { double bonus = 0.0; for (ItemStack item : player.getInventory().getContents()) { for (String gemId : socketIds(item)) { GemDefinition def = gems.get(gemId.toLowerCase(Locale.ROOT)); if (def == null) continue; double scaled = def.value() * registry.scale(gemId); if (def.effect().equalsIgnoreCase("increase_damage")) bonus += Math.max(0.0, scaled) * 0.8; } } return bonus; }
    private void applyPassiveEffects(Player player) { int speedLevel = 0; int healthBoost = 0; int bowPower = 0; int miningSpeed = 0; for (ItemStack item : player.getInventory().getArmorContents()) { for (String gemId : socketIds(item)) { GemDefinition def = gems.get(gemId.toLowerCase(Locale.ROOT)); if (def == null) continue; double scaled = def.value() * registry.scale(gemId); if (def.effect().equalsIgnoreCase("increase_speed")) speedLevel = Math.max(speedLevel, (int) Math.ceil(scaled)); if (def.effect().equalsIgnoreCase("increase_health")) healthBoost = Math.max(healthBoost, (int) Math.ceil(scaled)); if (def.effect().equalsIgnoreCase("increase_bow_power")) bowPower = Math.max(bowPower, (int) Math.ceil(scaled)); if (def.effect().equalsIgnoreCase("increase_mining_speed")) miningSpeed = Math.max(miningSpeed, (int) Math.ceil(scaled)); } } if (speedLevel > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, Math.max(0, speedLevel - 1), true, false, false)); if (bowPower > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, Math.max(0, bowPower - 1), true, false, false)); if (miningSpeed > 0) player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, Math.max(0, miningSpeed - 1), true, false, false)); if (healthBoost > 0 && player.getAttribute(Attribute.MAX_HEALTH) != null) { double max = 20.0 + (healthBoost * 2.0); player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(max); player.setHealth(Math.min(player.getHealth(), max)); } }
    private void healPlayer(Player player, double amount) { if (amount <= 0.0) return; double max = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : player.getAttribute(Attribute.MAX_HEALTH).getValue(); player.setHealth(Math.min(max, player.getHealth() + amount)); }
    private boolean isOffCooldown(UUID uuid, String gemId) { long now = System.currentTimeMillis(); return procCooldowns.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(gemId, 0L) <= now; }
    private void pushCooldown(UUID uuid, String gemId, long ticks) { long until = System.currentTimeMillis() + (Math.max(0L, ticks) * 50L); procCooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(gemId, until); }
    private ItemStack extractorItem() { ItemStack item = new ItemStack(Material.PRISMARINE_SHARD); ItemMeta meta = item.getItemMeta(); meta.displayName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>Gem Extractor</gradient>")); meta.lore(List.of(Text.mm("<gray>Shift-right click a socketed item to extract its gem.</gray>"))); meta.getPersistentDataContainer().set(keyExtractor(), PersistentDataType.BYTE, (byte)1); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item; }
    private NamespacedKey keyGem() { return new NamespacedKey(plugin, GEM_KEY); }
    private NamespacedKey keySockets() { return new NamespacedKey(plugin, GEM_SOCKETS_KEY); }
    private NamespacedKey keyExtractor() { return new NamespacedKey(plugin, EXTRACTOR_KEY); }
    private ItemStack pane() { return button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()); }
    private boolean isExtractor(ItemStack item) { return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(keyExtractor(), PersistentDataType.BYTE); }
    private void fill(Inventory inv, ItemStack item) { for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, item); }
    private List<String> parseSockets(String raw) { if (raw == null || raw.isBlank()) return List.of(); return Arrays.stream(raw.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank()).toList(); }
    private GemDefinition gemBySlot(int slot) { int index = 10; for (GemDefinition def : gems.values()) { if (index == slot) return def; index++; if (index == 17 || index == 26 || index == 35 || index == 44) index++; } return null; }
    private int slotsPerItem() { return configs.get("gems/gems.yml").getInt("gems.slots-per-item", 3); }
    private ItemStack button(Material material, String name, List<String> lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(Text.mm(name)); meta.lore(lore.stream().map(Text::mm).toList()); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item; }
    private Material parseMaterial(String value) { try { return Material.valueOf(value.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return Material.AMETHYST_SHARD; } }
    private int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception ex) { return 1; } }
    private record GemDefinition(String id, String displayName, Material material, List<String> lore, String effect, int value, double procChance, long cooldownTicks) {}
    public static final class GemUiState { private ItemStack baseItem; private ItemStack selectedGem; }
}



