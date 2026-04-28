package net.dark.threecore.perks;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.model.PlayerProgressionData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import net.dark.threecore.particle.ParticleManager;

public final class PerkService implements Listener {
    private static final String COSMETICS_ITEM_ID = "cosmetics_menu";
    private static final String ITEM_KEY = "3smpcore_cosmetics_item";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MenuService menuService;
    private final ParticleManager particleManager;
    private final Map<UUID, PlayerProgressionData> cache = new HashMap<>();
    private final Map<String, PerkDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, TrimDefinition> trims = new LinkedHashMap<>();

    public PerkService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService, ParticleManager particleManager) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
        this.particleManager = particleManager;
        reload();
    }

    public void reload() {
        definitions.clear();
        trims.clear();
        loadDefinitions();
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) openMainMenu(player);
            else net.dark.threecore.text.Text.send(sender, "<red>Players only.</red>");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } reload(); net.dark.threecore.text.Text.send(sender, "<green>Perks reloaded.</green>"); }
            case "unlock", "remove" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 3) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks " + sub + " <player> <perkId></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); if (sub.equals("unlock")) unlock(target.getUniqueId(), args[2]); else remove(target.getUniqueId(), args[2]); net.dark.threecore.text.Text.send(sender, "<green>Perk updated for " + target.getName() + ".</green>"); }
            case "settag", "setbadge", "settrim", "setmessagecolor", "setparticle", "setcosmetic", "seteffect" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 3) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks " + sub + " <player> <perkId></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); setActive(target.getUniqueId(), sub.substring(3), args[2]); net.dark.threecore.text.Text.send(sender, "<green>Selection updated for " + target.getName() + ".</green>"); }
            case "clearparticle" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks clearparticle <player></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); data(target.getUniqueId()).activeParticle(""); save(target.getUniqueId()); if (particleManager != null && target.isOnline()) particleManager.set(target.getUniqueId(), ""); net.dark.threecore.text.Text.send(sender, "<green>Particle cosmetic cleared for " + target.getName() + ".</green>"); }
            case "list" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks list <player></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); net.dark.threecore.text.Text.send(sender, "<gray>Unlocked perks for " + target.getName() + ": " + String.join(", ", data(target.getUniqueId()).unlockedPerks()) + "</gray>"); }
            default -> { if (sender instanceof Player player) openMainMenu(player); else net.dark.threecore.text.Text.send(sender, "<gray>Use /perks reload, /perks unlock, /perks remove, /perks list, /perks settag, /perks settrim, or /perks setmessagecolor.</gray>"); }
        }
    }

    public void openMainMenu(Player player) { menuService.open(player, buildMainMenu()); }
    public void giveCosmeticsItem(Player player) {
        if (!isSpawnWorld(player)) {
            clearCosmeticsItem(player);
            return;
        }
        if (net.dark.threecore.zonepvp.ZonePvpService.isZonePlayer(player) || net.dark.threecore.duels.DuelService.isDuelPlayer(player)) {
            clearCosmeticsItem(player);
            return;
        }
        if (!configs.get("cosmetics/cosmetics.yml").getBoolean("hotbar.enabled", true)) return;
        int slot = Math.max(0, Math.min(8, configs.get("cosmetics/cosmetics.yml").getInt("hotbar.slot", 4)));
        clearCosmeticsItem(player);
        player.getInventory().setItem(slot, taggedIcon(org.bukkit.Material.NETHER_STAR, configs.get("cosmetics/cosmetics.yml").getString("hotbar.name", "<gradient:#7c4a03:#f59e0b>Cosmetics</gradient>")));
    }

    public Inventory buildMainMenu() {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_MAIN, "main"), 54, "3SMP Cosmetics");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(4, icon(org.bukkit.Material.NETHER_STAR, "<gradient:#7c4a03:#f59e0b>Cosmetics Hub</gradient>", List.of("<gray>Chat colors, particles, kill effects, weapon cosmetics, and dungeon cosmetics.</gray>")));
        inv.setItem(19, icon(org.bukkit.Material.BLUE_DYE, "<gradient:#1A2A4A:#D6E8F7>Chat Colors</gradient>", List.of("<gray>Choose how your sent messages look.</gray>")));
        inv.setItem(21, icon(org.bukkit.Material.NETHER_STAR, "<gradient:#f59e0b:#fff7ad>Badges</gradient>", List.of("<gray>Optional cosmetic badges for supported views.</gray>")));
        inv.setItem(23, icon(org.bukkit.Material.FIREWORK_ROCKET, "<gradient:#facc15:#7c4a03>Particles</gradient>", List.of("<gray>Cosmetic particles. Default is none.</gray>")));
        inv.setItem(25, icon(org.bukkit.Material.ENCHANTED_BOOK, "<gradient:#facc15:#f59e0b>Kill Effects</gradient>", List.of("<gray>Particles and sounds that play when you win a duel.</gray>")));
        inv.setItem(29, icon(org.bukkit.Material.GLOWSTONE_DUST, "<gradient:#f59e0b:#fbbf24>Effects</gradient>", List.of("<gray>Light cosmetic effects.</gray>")));
        inv.setItem(31, icon(org.bukkit.Material.TRIDENT, "<gradient:#b45309:#f59e0b>Weapon Cosmetics</gradient>", List.of("<gray>Framework tab for future models and skins.</gray>")));
        inv.setItem(33, icon(org.bukkit.Material.ECHO_SHARD, "<gradient:#7c2d12:#facc15>Dungeon Cosmetics</gradient>", List.of("<gray>Dungeon-ready cosmetic categories.</gray>")));
        inv.setItem(49, icon(org.bukkit.Material.BOOK, "<gradient:#1A2A4A:#D6E8F7>Summary</gradient>", List.of("<gray>Review active cosmetics.</gray>")));
        return inv;
    }

    public void handleMenuClick(Player player, int slot) {
        if (slot == 49) menuService.open(player, buildSummaryMenu(player));
        else if (slot == 19) menuService.open(player, buildCategoryMenu("colors", "Chat Colors"));
        else if (slot == 21) menuService.open(player, buildCategoryMenu("badges", "Badges"));
        else if (slot == 23) menuService.open(player, buildCategoryMenu("particles", "Particles"));
        else if (slot == 25) menuService.open(player, buildCategoryMenu("kill_effects", "Kill Effects"));
        else if (slot == 29) menuService.open(player, buildCategoryMenu("effects", "Effects"));
        else if (slot == 31) menuService.open(player, buildCategoryMenu("weapon_cosmetics", "Weapon Cosmetics"));
        else if (slot == 33) menuService.open(player, buildCategoryMenu("dungeon_cosmetics", "Dungeon Cosmetics"));
    }

    public void handleSummaryMenuClick(Player player, int slot) {
        if (slot == 22) openMainMenu(player);
        else if (slot == 11) menuService.open(player, buildCategoryMenu("colors", "Chat Colors"));
        else if (slot == 13) menuService.open(player, buildCategoryMenu("particles", "Particles"));
        else if (slot == 15) menuService.open(player, buildCategoryMenu("kill_effects", "Kill Effects"));
    }

    public Inventory buildSummaryMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_MAIN, "summary"), 27, "3SMP Perks Summary");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        PlayerProgressionData data = data(player.getUniqueId());
        inv.setItem(11, icon(org.bukkit.Material.NAME_TAG, "<gradient:#7c4a03:#f59e0b>Chat Style</gradient>", List.of(
                "<gray>LuckPerms prefix:</gray> <white>always active</white>",
                "<gray>Selected chat color:</gray> <white>" + (data.activeMessageColor().isBlank() ? "default" : data.activeMessageColor()) + "</white>",
                "<gray>Badge:</gray> <white>" + (data.activeBadge().isBlank() ? "none" : data.activeBadge()) + "</white>",
                "<gray>Click to edit chat colors.</gray>"
        )));
        inv.setItem(13, icon(org.bukkit.Material.AMETHYST_SHARD, "<gradient:#f59e0b:#f97316>Appearance</gradient>", List.of(
                "<gray>Trim:</gray> <white>" + data.activeTrim() + "</white>",
                "<gray>Particle cosmetic:</gray> <white>" + (data.activeParticle().isBlank() ? "none" : data.activeParticle()) + "</white>",
                "<gray>Cosmetic:</gray> <white>" + (data.activeCosmetic().isBlank() ? "none" : data.activeCosmetic()) + "</white>",
                "<gray>Effect:</gray> <white>" + (data.activeEffect().isBlank() ? "none" : data.activeEffect()) + "</white>"
        )));
        inv.setItem(15, icon(org.bukkit.Material.CLOCK, "<gradient:#f59e0b:#fbbf24>Unlocked</gradient>", List.of(
                "<gray>Unlocked perks:</gray> <white>" + data.unlockedPerks().size() + "</white>"
        )));
        inv.setItem(22, icon(org.bukkit.Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to perks.</gray>")));
        return inv;
    }

    public Inventory buildCategoryMenu(String category, String title) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_CATEGORY, category), 54, "3SMP " + title);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(4, icon(org.bukkit.Material.BARRIER, "<red>Disable This Category</red>", List.of("<gray>Clear the selected cosmetic for this tab.</gray>")));
        int slot = 10;
        for (PerkDefinition def : definitions.values()) {
            if (!def.category.equalsIgnoreCase(category)) continue;
            inv.setItem(slot++, icon(def.material, def.displayName, def.lore));
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
        }
        inv.setItem(49, icon(org.bukkit.Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to perks.</gray>")));
        return inv;
    }

    public void handleCategoryClick(Player player, String category, int slot) {
        if (slot == 49) { openMainMenu(player); return; }
        if (slot == 4) { clearCategory(player, category); return; }
        int index = 10;
        for (PerkDefinition def : definitions.values()) {
            if (!def.category.equalsIgnoreCase(category)) continue;
            if (index == slot) {
                if (category.equalsIgnoreCase("trims") && !canUseTrim(player, def.id)) {
                    net.dark.threecore.text.Text.send(player, "<red>You do not meet the requirement for that trim.</red>");
                    return;
                }
                if (!canUse(player, def)) {
                    net.dark.threecore.text.Text.send(player, "<red>You do not have access to that perk.</red>");
                    return;
                }
                if (category.equalsIgnoreCase("tags")) setActive(player.getUniqueId(), "tag", def.id);
                else if (category.equalsIgnoreCase("badges")) setActive(player.getUniqueId(), "badge", def.id);
                else if (category.equalsIgnoreCase("trims")) setActive(player.getUniqueId(), "trim", def.id);
                else if (category.equalsIgnoreCase("colors")) setActive(player.getUniqueId(), "messagecolor", def.id);
                else if (category.equalsIgnoreCase("particles")) setActive(player.getUniqueId(), "particle", def.id);
                else if (category.equalsIgnoreCase("effects")) setActive(player.getUniqueId(), "effect", def.id);
                else if (category.equalsIgnoreCase("cosmetics") || category.equalsIgnoreCase("weapon_cosmetics") || category.equalsIgnoreCase("dungeon_cosmetics") || category.equalsIgnoreCase("kill_effects")) setActive(player.getUniqueId(), "cosmetic", def.id);
                net.dark.threecore.text.Text.send(player, "<green>Selected " + def.displayName + "</green>");
                return;
            }
            index++;
            if (index == 17 || index == 26 || index == 35 || index == 44) index++;
        }
    }

    private void clearCategory(Player player, String category) {
        if (category.equalsIgnoreCase("badges")) clearActive(player.getUniqueId(), "badge");
        else if (category.equalsIgnoreCase("particles")) clearActive(player.getUniqueId(), "particle");
        else if (category.equalsIgnoreCase("effects")) clearActive(player.getUniqueId(), "effect");
        else if (category.equalsIgnoreCase("trims")) clearActive(player.getUniqueId(), "trim");
        else if (category.equalsIgnoreCase("colors")) clearActive(player.getUniqueId(), "messagecolor");
        else clearActive(player.getUniqueId(), "cosmetic");
        net.dark.threecore.text.Text.send(player, "<gray>Disabled cosmetic for this category.</gray>");
        menuService.open(player, buildCategoryMenu(category, "Cosmetics"));
    }

    public void unlock(UUID uuid, String perkId) { data(uuid).unlockedPerks().add(perkId.toLowerCase(Locale.ROOT)); save(uuid); }
    public void remove(UUID uuid, String perkId) { data(uuid).unlockedPerks().remove(perkId.toLowerCase(Locale.ROOT)); save(uuid); }

    public void setActive(UUID uuid, String type, String perkId) {
        PlayerProgressionData data = data(uuid);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "tag" -> data.activeTag(perkId);
            case "badge" -> data.activeBadge(perkId);
            case "trim" -> data.activeTrim(perkId);
            case "messagecolor" -> data.activeMessageColor(perkId);
            case "cosmetic" -> data.activeCosmetic(perkId);
            case "particle" -> data.activeParticle(perkId);
            case "effect" -> data.activeEffect(perkId);
        }
        unlock(uuid, perkId);
        save(uuid);
        if ("particle".equalsIgnoreCase(type) && particleManager != null) particleManager.set(uuid, perkId);
    }

    public void clearActive(UUID uuid, String type) {
        PlayerProgressionData data = data(uuid);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "tag" -> data.activeTag("");
            case "badge" -> data.activeBadge("");
            case "trim" -> data.activeTrim("");
            case "messagecolor" -> data.activeMessageColor("");
            case "cosmetic" -> data.activeCosmetic("");
            case "particle" -> { data.activeParticle(""); if (particleManager != null) particleManager.set(uuid, ""); }
            case "effect" -> data.activeEffect("");
        }
        save(uuid);
    }

    public String summary(UUID uuid) {
        PlayerProgressionData data = data(uuid);
        return "<gray>LuckPerms Prefix: automatic | Tag: " + data.activeTag() + " | Badge: " + data.activeBadge() + " | Trim: " + data.activeTrim() + " | Msg: " + data.activeMessageColor() + " | Particles: " + (data.activeParticle().isBlank() ? "none" : data.activeParticle()) + "</gray>";
    }

    public boolean hasUnlocked(UUID uuid, String perkId) { return data(uuid).unlockedPerks().contains(perkId.toLowerCase(Locale.ROOT)); }
    public PlayerProgressionData data(UUID uuid) { return cache.computeIfAbsent(uuid, repository::load); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveCosmeticsItem(event.getPlayer());
        syncLiveCosmetics(event.getPlayer());
        applyActiveEffect(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        applyActiveEffect(event.getPlayer());
    }

    public void syncLiveCosmetics(Player player) {
        if (particleManager == null) return;
        PlayerProgressionData data = data(player.getUniqueId());
        String active = data.activeParticle();
        if (active == null || active.isBlank() || !isUnlockedOrDefault(player.getUniqueId(), active)) {
            data.activeParticle("");
            save(player.getUniqueId());
            particleManager.set(player.getUniqueId(), "");
            return;
        }
        particleManager.set(player.getUniqueId(), active);
    }

    @EventHandler
    public void onCosmeticItemClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isCosmeticsItem(event.getCurrentItem())) {
            event.setCancelled(true);
            event.setCursor(null);
            if (event.getClickedInventory() == player.getInventory()) {
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    player.setItemOnCursor(null);
                    player.updateInventory();
                    openMainMenu(player);
                }, 20L);
            }
        }
    }

    @EventHandler
    public void onCosmeticItemInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isCosmeticsItem(event.getItem())) return;
        event.setCancelled(true);
        openMainMenu(event.getPlayer());
    }

    @EventHandler
    public void onCosmeticItemDrop(PlayerDropItemEvent event) {
        if (isCosmeticsItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    private boolean isCosmeticsItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return COSMETICS_ITEM_ID.equals(item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, ITEM_KEY), PersistentDataType.STRING));
    }

    private ItemStack taggedIcon(org.bukkit.Material material, String name) {
        ItemStack item = icon(material, name, List.of("<gray>Click to open cosmetics.</gray>"));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, ITEM_KEY), PersistentDataType.STRING, COSMETICS_ITEM_ID);
        item.setItemMeta(meta);
        return item;
    }

    private void save(UUID uuid) { repository.save(data(uuid)); }
    private void clearCosmeticsItem(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isCosmeticsItem(player.getInventory().getItem(i))) player.getInventory().setItem(i, null);
        }
    }
    private boolean isSpawnWorld(Player player) {
        String configured = configs.get("core/config.yml").getString("spawn.world", "spawn");
        return player.getWorld() != null && (player.getWorld().getName().equalsIgnoreCase(configured) || player.getWorld().getName().equalsIgnoreCase("spawn"));
    }

    private boolean canUse(Player player, PerkDefinition def) {
        if (def.requiredRank != null && !def.requiredRank.isBlank() && !matchesRank(player, def.requiredRank)) return false;
        if (def.defaultUnlocked || hasUnlocked(player.getUniqueId(), def.id)) return true;
        return def.permission == null || def.permission.isBlank() || player.hasPermission(def.permission);
    }

    private boolean isUnlockedOrDefault(UUID uuid, String id) {
        PerkDefinition def = definitions.get(id.toLowerCase(Locale.ROOT));
        return def != null && (def.defaultUnlocked || hasUnlocked(uuid, id));
    }

    private void applyActiveEffect(Player player) {
        String id = data(player.getUniqueId()).activeEffect();
        if (id == null || id.isBlank()) return;
        if (!isUnlockedOrDefault(player.getUniqueId(), id)) return;
        ConfigurationSection section = configs.get("cosmetics/effects.yml").getConfigurationSection("effects." + id.toLowerCase(Locale.ROOT));
        if (section == null) return;
        try {
            PotionEffectType type = PotionEffectType.getByName(section.getString("effect", ""));
            if (type == null) return;
            int amplifier = Math.max(0, section.getInt("amplifier", 0));
            player.addPotionEffect(new PotionEffect(type, 140, amplifier, true, false, false));
        } catch (Exception ignored) {
        }
    }

    private void loadDefinitions() {
        loadSection("cosmetics/tags.yml", "tags", org.bukkit.Material.PAPER);
        loadSection("cosmetics/badges.yml", "badges", org.bukkit.Material.NETHER_STAR);
        loadSection("cosmetics/colors.yml", "colors", org.bukkit.Material.BLUE_DYE);
        loadSection("cosmetics/trims.yml", "trims", org.bukkit.Material.AMETHYST_SHARD);
        loadSection("cosmetics/particles.yml", "particles", org.bukkit.Material.FIREWORK_ROCKET);
        loadSection("cosmetics/effects.yml", "effects", org.bukkit.Material.GLOWSTONE_DUST);
        loadSection("cosmetics/cosmetics.yml", "cosmetics", org.bukkit.Material.ENDER_EYE);
        loadSection("cosmetics/cosmetics.yml", "weapon_cosmetics", org.bukkit.Material.TRIDENT);
        loadSection("cosmetics/cosmetics.yml", "dungeon_cosmetics", org.bukkit.Material.ECHO_SHARD);
        loadSection("cosmetics/cosmetics.yml", "kill_effects", org.bukkit.Material.ENCHANTED_BOOK);
    }

    private void loadSection(String file, String root, org.bukkit.Material fallbackMaterial) {
        var section = configs.get(file).getConfigurationSection(root);
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            definitions.put(id.toLowerCase(Locale.ROOT), new PerkDefinition(id.toLowerCase(Locale.ROOT), root, parseMaterial(item.getString("icon", fallbackMaterial.name())), item.getString("display-name", id), item.getString("permission", item.getString("required-permission", "")), item.getString("required-rank", ""), item.getBoolean("default-unlocked", false), item.getStringList("lore")));
            if ("trims".equalsIgnoreCase(root)) trims.put(id.toLowerCase(Locale.ROOT), new TrimDefinition(id.toLowerCase(Locale.ROOT), item.getString("required-permission", ""), item.getString("required-rank", "")));
        }
    }

    private boolean canUseTrim(Player player, String trimId) {
        TrimDefinition def = trims.get(trimId.toLowerCase(Locale.ROOT));
        if (def == null) return true;
        if (def.requiredPermission != null && !def.requiredPermission.isBlank() && !player.hasPermission(def.requiredPermission)) return false;
        if (def.requiredRank == null || def.requiredRank.isBlank()) return true;
        String currentRank = luckPermsPrimaryGroup(player);
        return currentRank != null && currentRank.equalsIgnoreCase(def.requiredRank);
    }

    private boolean matchesRank(Player player, String requiredRank) {
        if (requiredRank == null || requiredRank.isBlank()) return true;
        String currentRank = luckPermsPrimaryGroup(player);
        return currentRank != null && currentRank.equalsIgnoreCase(requiredRank);
    }

    private String luckPermsPrimaryGroup(Player player) {
        try {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
            if (plugin == null || !plugin.isEnabled()) return null;
            Object api = plugin.getClass().getMethod("getApi").invoke(plugin);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return null;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object group = metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
            return group == null ? null : group.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack pane() { return icon(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); }

    private ItemStack icon(org.bukkit.Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(lore.stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private org.bukkit.Material parseMaterial(String name) { try { return org.bukkit.Material.valueOf(name.toUpperCase(Locale.ROOT)); } catch (Exception ex) { return org.bukkit.Material.PAPER; } }
    private record PerkDefinition(String id, String category, org.bukkit.Material material, String displayName, String permission, String requiredRank, boolean defaultUnlocked, List<String> lore) {}
    private record TrimDefinition(String id, String requiredPermission, String requiredRank) {}
}



