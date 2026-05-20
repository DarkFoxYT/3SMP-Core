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
import org.bukkit.event.player.PlayerQuitEvent;
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
    private static final int[] CATEGORY_SLOTS = {
        11, 12, 13, 14, 15,
        20, 21, 22, 23, 24,
        29, 30, 31, 32, 33,
        38, 39, 40, 41, 42
    };
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
            case "settag", "setbadge", "settrim", "setmessagecolor", "setparticle", "setcosmetic", "setjoinquit" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 3) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks " + sub + " <player> <perkId></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); setActive(target.getUniqueId(), sub.substring(3), args[2]); net.dark.threecore.text.Text.send(sender, "<green>Selection updated for " + target.getName() + ".</green>"); }
            case "clearparticle" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks clearparticle <player></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); data(target.getUniqueId()).activeParticle(""); save(target.getUniqueId()); if (particleManager != null && target.isOnline()) particleManager.set(target.getUniqueId(), ""); net.dark.threecore.text.Text.send(sender, "<green>Particle cosmetic cleared for " + target.getName() + ".</green>"); }
            case "list" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks list <player></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); net.dark.threecore.text.Text.send(sender, "<gray>Unlocked perks for " + target.getName() + ": " + String.join(", ", data(target.getUniqueId()).unlockedPerks()) + "</gray>"); }
            default -> { if (sender instanceof Player player) openMainMenu(player); else net.dark.threecore.text.Text.send(sender, "<gray>Use /perks reload, /perks unlock, /perks remove, /perks list, /perks settag, /perks settrim, or /perks setmessagecolor.</gray>"); }
        }
    }

    public void openMainMenu(Player player) { menuService.open(player, buildMainMenu()); }
    public void openCategory(Player player, String category) {
        menuService.open(player, buildCategoryMenu(player, category, titleForCategory(category)));
    }

    public void giveCosmeticsItem(Player player) {
        if (!configs.get("core/config.yml").getBoolean("spawn.hotbar-items.enabled", false)) {
            clearCosmeticsItem(player);
            return;
        }
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
        player.getInventory().setItem(slot, taggedIcon(org.bukkit.Material.NETHER_STAR, configs.get("cosmetics/cosmetics.yml").getString("hotbar.name", "<gradient:#f4cd2a:#eda323:#d28d0d>Cosmetics</gradient>")));
    }

    public Inventory buildMainMenu() {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_MAIN, "main"), 54, menuTitle("main", "3SMP Cosmetics"));
        inv.setItem(20, icon(org.bukkit.Material.YELLOW_DYE, "<gradient:#f4cd2a:#eda323:#d28d0d>Chat Colors</gradient>", List.of("<gray>Choose how your sent messages look.</gray>")));
        inv.setItem(22, icon(org.bukkit.Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d>Badges</gradient>", List.of("<gray>Optional cosmetic badges for supported views.</gray>")));
        inv.setItem(24, icon(org.bukkit.Material.WRITABLE_BOOK, "<gradient:#f4cd2a:#eda323:#d28d0d>Join & Quit Messages</gradient>", List.of("<gray>Choose your server join and leave lines.</gray>")));
        inv.setItem(28, icon(org.bukkit.Material.NAME_TAG, "<gradient:#f4cd2a:#eda323:#d28d0d>Tags</gradient>", List.of("<gray>Choose unlocked profile tags.</gray>")));
        inv.setItem(30, icon(org.bukkit.Material.SPYGLASS, "<gradient:#f4cd2a:#eda323:#d28d0d>Name Visuals</gradient>", List.of("<gray>Name colors, gradients, and shadows.</gray>")));
        inv.setItem(31, icon(org.bukkit.Material.FIREWORK_ROCKET, "<gradient:#f4cd2a:#eda323:#d28d0d>Particles</gradient>", List.of("<gray>Choose unlocked aura particles.</gray>")));
        inv.setItem(32, icon(org.bukkit.Material.ENCHANTED_BOOK, "<gradient:#f4cd2a:#eda323:#d28d0d>Kill Effects</gradient>", List.of("<gray>Particles and sounds that play when you win a duel.</gray>")));
        inv.setItem(40, icon(org.bukkit.Material.BOOK, "<gradient:#f4cd2a:#eda323:#d28d0d>Summary</gradient>", List.of("<gray>Review active cosmetics.</gray>")));
        return inv;
    }

    public void handleMenuClick(Player player, int slot) {
        if (slot == 40) menuService.open(player, buildSummaryMenu(player));
        else if (slot == 20) menuService.open(player, buildCategoryMenu(player, "colors", "Chat Colors"));
        else if (slot == 22) menuService.open(player, buildCategoryMenu(player, "badges", "Badges"));
        else if (slot == 24) menuService.open(player, buildCategoryMenu(player, "join_quit_messages", "Join & Quit Messages"));
        else if (slot == 28) menuService.open(player, buildCategoryMenu(player, "tags", "Tags"));
        else if (slot == 30) player.performCommand("visuals");
        else if (slot == 31) menuService.open(player, buildCategoryMenu(player, "particles", "Particles"));
        else if (slot == 32) menuService.open(player, buildCategoryMenu(player, "kill_effects", "Kill Effects"));
    }

    public void handleSummaryMenuClick(Player player, int slot) {
        if (slot == 22) openMainMenu(player);
        else if (slot == 11) menuService.open(player, buildCategoryMenu(player, "colors", "Chat Colors"));
        else if (slot == 13) menuService.open(player, buildCategoryMenu(player, "badges", "Badges"));
        else if (slot == 15) menuService.open(player, buildCategoryMenu(player, "kill_effects", "Kill Effects"));
    }

    public Inventory buildSummaryMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_MAIN, "summary"), 27, "3SMP Perks Summary");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        PlayerProgressionData data = data(player.getUniqueId());
        inv.setItem(11, icon(org.bukkit.Material.NAME_TAG, "<gradient:#f4cd2a:#eda323:#d28d0d>Chat Style</gradient>", List.of(
                "<gray>LuckPerms prefix:</gray> <white>always active</white>",
                "<gray>Selected chat color:</gray> <white>" + (data.activeMessageColor().isBlank() ? "default" : data.activeMessageColor()) + "</white>",
                "<gray>Badge:</gray> <white>" + (data.activeBadge().isBlank() ? "none" : data.activeBadge()) + "</white>",
                "<gray>Click to edit chat colors.</gray>"
        )));
        inv.setItem(13, icon(org.bukkit.Material.AMETHYST_SHARD, "<gradient:#f4cd2a:#eda323:#d28d0d>Appearance</gradient>", List.of(
                "<gray>Cosmetic:</gray> <white>" + (data.activeCosmetic().isBlank() ? "none" : data.activeCosmetic()) + "</white>",
                "<gray>Join/Quit:</gray> <white>" + (data.activeJoinQuitMessage().isBlank() ? "none" : data.activeJoinQuitMessage()) + "</white>"
        )));
        inv.setItem(15, icon(org.bukkit.Material.CLOCK, "<gradient:#f4cd2a:#eda323:#d28d0d>Unlocked</gradient>", List.of(
                "<gray>Unlocked perks:</gray> <white>" + data.unlockedPerks().size() + "</white>"
        )));
        inv.setItem(22, icon(org.bukkit.Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to perks.</gray>")));
        return inv;
    }

    public Inventory buildCategoryMenu(Player player, String category, String title) {
        return buildCategoryMenu(player, category, title, 0);
    }

    public Inventory buildCategoryMenu(Player player, String category, String title, int page) {
        List<PerkDefinition> categoryDefinitions = definitionsFor(category);
        int maxPage = Math.max(0, (categoryDefinitions.size() - 1) / CATEGORY_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_CATEGORY, category.toLowerCase(Locale.ROOT) + ":" + safePage), 54, "3SMP " + title);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(4, icon(org.bukkit.Material.BARRIER, "<red>Disable This Category</red>", List.of("<gray>Clear the selected cosmetic for this tab.</gray>")));
        int start = safePage * CATEGORY_SLOTS.length;
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            int index = start + i;
            if (index >= categoryDefinitions.size()) break;
            inv.setItem(CATEGORY_SLOTS[i], cosmeticIcon(player, categoryDefinitions.get(index)));
        }
        if (safePage > 0) inv.setItem(45, icon(org.bukkit.Material.ARROW, "<gradient:#f4cd2a:#eda323:#d28d0d>Previous Page</gradient>", List.of("<gray>Go back one page.</gray>")));
        inv.setItem(49, icon(org.bukkit.Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to perks.</gray>")));
        inv.setItem(50, icon(org.bukkit.Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>Page " + (safePage + 1) + "/" + (maxPage + 1) + "</gradient>", List.of("<gray>" + categoryDefinitions.size() + " cosmetics in this category.</gray>")));
        if ((safePage + 1) * CATEGORY_SLOTS.length < categoryDefinitions.size()) inv.setItem(53, icon(org.bukkit.Material.ARROW, "<gradient:#f4cd2a:#eda323:#d28d0d>Next Page</gradient>", List.of("<gray>See more options.</gray>")));
        return inv;
    }

    public void handleCategoryClick(Player player, String context, int slot) {
        CategoryPage categoryPage = parseCategoryPage(context);
        String category = categoryPage.category();
        int page = categoryPage.page();
        if (slot == 49) { openMainMenu(player); return; }
        if (slot == 45 && page > 0) { menuService.open(player, buildCategoryMenu(player, category, titleForCategory(category), page - 1)); return; }
        List<PerkDefinition> categoryDefinitions = definitionsFor(category);
        if (slot == 53 && (page + 1) * CATEGORY_SLOTS.length < categoryDefinitions.size()) { menuService.open(player, buildCategoryMenu(player, category, titleForCategory(category), page + 1)); return; }
        if (slot == 4) { clearCategory(player, category, page); return; }
        int slotIndex = slotIndex(slot);
        if (slotIndex < 0) return;
        int itemIndex = page * CATEGORY_SLOTS.length + slotIndex;
        if (itemIndex < 0 || itemIndex >= categoryDefinitions.size()) return;
        PerkDefinition def = categoryDefinitions.get(itemIndex);
        if (!canUse(player, def)) {
            if (purchaseLockedPerk(player, category, def, page)) return;
            net.dark.threecore.text.Text.send(player, "<red>You do not have access to that perk.</red>");
            return;
        }
        if (category.equalsIgnoreCase("tags")) setActive(player.getUniqueId(), "tag", def.id);
        else if (category.equalsIgnoreCase("badges")) setActive(player.getUniqueId(), "badge", def.id);
        else if (category.equalsIgnoreCase("trims")) setActive(player.getUniqueId(), "trim", def.id);
        else if (category.equalsIgnoreCase("colors")) setActive(player.getUniqueId(), "messagecolor", def.id);
        else if (category.equalsIgnoreCase("particles")) setActive(player.getUniqueId(), "particle", def.id);
        else if (category.equalsIgnoreCase("join_quit_messages")) setActive(player.getUniqueId(), "joinquit", def.id);
        else if (category.equalsIgnoreCase("cosmetics") || category.equalsIgnoreCase("weapon_cosmetics") || category.equalsIgnoreCase("kill_effects")) setActive(player.getUniqueId(), "cosmetic", def.id);
        net.dark.threecore.text.Text.send(player, "<green>Selected " + def.displayName + "</green>");
        menuService.open(player, buildCategoryMenu(player, category, titleForCategory(category), page));
    }

    private void clearCategory(Player player, String category, int page) {
        if (category.equalsIgnoreCase("badges")) clearActive(player.getUniqueId(), "badge");
        else if (category.equalsIgnoreCase("particles")) clearActive(player.getUniqueId(), "particle");
        else if (category.equalsIgnoreCase("join_quit_messages")) clearActive(player.getUniqueId(), "joinquit");
        else if (category.equalsIgnoreCase("trims")) clearActive(player.getUniqueId(), "trim");
        else if (category.equalsIgnoreCase("colors")) clearActive(player.getUniqueId(), "messagecolor");
        else clearActive(player.getUniqueId(), "cosmetic");
        net.dark.threecore.text.Text.send(player, "<gray>Disabled cosmetic for this category.</gray>");
        menuService.open(player, buildCategoryMenu(player, category, titleForCategory(category), page));
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
            case "joinquit" -> data.activeJoinQuitMessage(perkId);
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
            case "joinquit" -> data.activeJoinQuitMessage("");
        }
        save(uuid);
    }

    public String summary(UUID uuid) {
        PlayerProgressionData data = data(uuid);
        return "<gray>LuckPerms Prefix: automatic | Tag: " + data.activeTag() + " | Badge: " + data.activeBadge() + " | Trim: " + data.activeTrim() + " | Msg: " + data.activeMessageColor() + "</gray>";
    }

    public boolean hasUnlocked(UUID uuid, String perkId) { return data(uuid).unlockedPerks().contains(perkId.toLowerCase(Locale.ROOT)); }
    public PlayerProgressionData data(UUID uuid) { return cache.computeIfAbsent(uuid, repository::load); }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        giveCosmeticsItem(event.getPlayer());
        syncLiveCosmetics(event.getPlayer());
        applyActiveEffect(event.getPlayer());
        applyJoinQuitMessage(event.getPlayer(), true, event);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        applyJoinQuitMessage(event.getPlayer(), false, event);
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
        if (hasFullPerkBypass(player)) return true;
        if (def.permission != null && !def.permission.isBlank() && player.hasPermission(def.permission)) return true;
        if (def.requiredRank != null && !def.requiredRank.isBlank() && matchesRank(player, def.requiredRank)) return true;
        return def.defaultUnlocked || hasUnlocked(player.getUniqueId(), def.id);
    }

    private boolean hasFullPerkBypass(Player player) {
        if (player == null) return false;
        if (player.hasPermission("3smpcore.perks.all") || player.hasPermission("3smpcore.admin") || player.isOp()) return true;
        String group = luckPermsPrimaryGroup(player);
        if (group == null) return false;
        String normalized = group.toLowerCase(Locale.ROOT);
        return normalized.equals("owner") || normalized.equals("h318") || normalized.equals("sr-admin") || normalized.equals("sradmin") || normalized.equals("dev");
    }

    private boolean isUnlockedOrDefault(UUID uuid, String id) {
        PerkDefinition def = firstDefinition(id);
        return def != null && (def.defaultUnlocked || hasUnlocked(uuid, id));
    }

    private void applyActiveEffect(Player player) {
        String id = data(player.getUniqueId()).activeEffect();
        if (id == null || id.isBlank()) return;
        data(player.getUniqueId()).activeEffect("");
        save(player.getUniqueId());
    }

    private void applyJoinQuitMessage(Player player, boolean join, Object event) {
        if (player == null) return;
        String id = data(player.getUniqueId()).activeJoinQuitMessage();
        if (id == null || id.isBlank()) return;
        PerkDefinition def = definition("join_quit_messages", id);
        if (def == null || !canUse(player, def)) return;
        String path = "join_quit_messages." + id.toLowerCase(Locale.ROOT) + (join ? ".join-message" : ".quit-message");
        String fallback = join ? "<gradient:#f4cd2a:#eda323:#d28d0d>[Player] joined the server.</gradient>" : "<gradient:#f4cd2a:#eda323:#d28d0d>[Player] left the server.</gradient>";
        String message = configs.get("cosmetics/join_quit_messages.yml").getString(path, fallback).replace("[Player]", player.getName()).replace("{player}", player.getName());
        if (event instanceof PlayerJoinEvent joinEvent) joinEvent.joinMessage(net.dark.threecore.text.Text.mm(message));
        else if (event instanceof PlayerQuitEvent quitEvent) quitEvent.quitMessage(net.dark.threecore.text.Text.mm(message));
    }

    private void loadDefinitions() {
        loadSection("cosmetics/tags.yml", "tags", org.bukkit.Material.PAPER);
        loadSection("cosmetics/badges.yml", "badges", org.bukkit.Material.NETHER_STAR);
        loadSection("cosmetics/colors.yml", "colors", org.bukkit.Material.BLUE_DYE);
        loadSection("cosmetics/trims.yml", "trims", org.bukkit.Material.AMETHYST_SHARD);
        loadSection("cosmetics/cosmetics.yml", "cosmetics", org.bukkit.Material.ENDER_EYE);
        loadSection("cosmetics/cosmetics.yml", "weapon_cosmetics", org.bukkit.Material.TRIDENT);
        loadSection("cosmetics/cosmetics.yml", "kill_effects", org.bukkit.Material.ENCHANTED_BOOK);
        loadSection("cosmetics/particles.yml", "particles", org.bukkit.Material.FIREWORK_ROCKET);
        loadSection("cosmetics/join_quit_messages.yml", "join_quit_messages", org.bukkit.Material.WRITABLE_BOOK);
    }

    private void loadSection(String file, String root, org.bukkit.Material fallbackMaterial) {
        var section = configs.get(file).getConfigurationSection(root);
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            String normalized = id.toLowerCase(Locale.ROOT);
            definitions.put(root.toLowerCase(Locale.ROOT) + ":" + normalized, new PerkDefinition(normalized, root, parseMaterial(item.getString("icon", fallbackMaterial.name())), item.getString("display-name", id), item.getString("permission", item.getString("required-permission", "")), item.getString("required-rank", ""), item.getBoolean("default-unlocked", false), item.getStringList("lore")));
            if ("trims".equalsIgnoreCase(root)) trims.put(normalized, new TrimDefinition(normalized, item.getString("required-permission", ""), item.getString("required-rank", "")));
        }
    }

    private PerkDefinition definition(String category, String id) {
        if (category == null || id == null || id.isBlank()) return null;
        return definitions.get(category.toLowerCase(Locale.ROOT) + ":" + id.toLowerCase(Locale.ROOT));
    }

    private PerkDefinition firstDefinition(String id) {
        if (id == null || id.isBlank()) return null;
        String normalized = id.toLowerCase(Locale.ROOT);
        return definitions.values().stream().filter(def -> def.id.equalsIgnoreCase(normalized)).findFirst().orElse(null);
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

    private ItemStack cosmeticIcon(Player player, PerkDefinition def) {
        boolean allowed = canUse(player, def);
        boolean active = isActive(player, def.category, def.id);
        List<String> lore = new ArrayList<>(def.lore);
        lore.add("");
        if (active) lore.add("<gradient:#f4cd2a:#eda323:#d28d0d><bold>Selected</bold></gradient>");
        else if (allowed) lore.add("<gray>Click to select.</gray>");
        else if (sapphireShopItemFor(def.category) != null) {
            long price = sapphirePriceFor(def.category);
            lore.add("<dark_gray>[Locked]</dark_gray>");
            if (price > 0L) lore.add("<gray>Click to unlock for <gradient:#f4cd2a:#eda323:#d28d0d>" + formatSapphires(price) + " Sapphires</gradient>.</gray>");
            else lore.add("<gray>This unlock is coming soon.</gray>");
            return icon(def.material, def.displayName, lore);
        }
        else {
            lore.add("<dark_gray>[Locked]</dark_gray>");
            lore.add("<gray>Unlock this cosmetic before equipping it.</gray>");
        }
        return icon(allowed ? def.material : org.bukkit.Material.GRAY_DYE, allowed ? def.displayName : "<dark_gray>Locked</dark_gray> <gray>" + def.displayName + "</gray>", lore);
    }

    private boolean purchaseLockedPerk(Player player, String category, PerkDefinition def, int page) {
        String shopItem = sapphireShopItemFor(category);
        if (shopItem == null) return false;
        long price = sapphirePriceFor(category);
        if (price <= 0L) {
            net.dark.threecore.text.Text.send(player, "<yellow>That unlock is coming soon.</yellow>");
            return true;
        }
        UUID uuid = player.getUniqueId();
        long current = repository.getSapphireBalance(uuid);
        if (current < price) {
            net.dark.threecore.text.Text.send(player, "<red>You need " + formatSapphires(price) + " Sapphires to unlock that.</red>");
            return true;
        }
        repository.setSapphireBalance(uuid, current - price);
        String activeType = activeTypeFor(category);
        if (activeType == null) unlock(uuid, def.id);
        else setActive(uuid, activeType, def.id);
        net.dark.threecore.text.Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Unlocked:</gradient> " + def.displayName + " <gray>(-" + formatSapphires(price) + " Sapphires)</gray>");
        menuService.open(player, buildCategoryMenu(player, category, titleForCategory(category), page));
        return true;
    }

    private String sapphireShopItemFor(String category) {
        if (category.equalsIgnoreCase("colors")) return "cosmetics";
        if (category.equalsIgnoreCase("badges")) return "badge";
        if (category.equalsIgnoreCase("join_quit_messages")) return "join_quit_message";
        if (category.equalsIgnoreCase("cosmetics")) return "cosmetic";
        if (category.equalsIgnoreCase("weapon_cosmetics")) return "weapon_cosmetic";
        if (category.equalsIgnoreCase("kill_effects")) return "kill_effect";
        if (category.equalsIgnoreCase("tags")) return "tag";
        if (category.equalsIgnoreCase("particles")) return "particle";
        return null;
    }

    private String activeTypeFor(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "colors" -> "messagecolor";
            case "badges" -> "badge";
            case "trims" -> "trim";
            case "tags" -> "tag";
            case "particles" -> "particle";
            case "join_quit_messages" -> "joinquit";
            case "cosmetics", "weapon_cosmetics", "kill_effects" -> "cosmetic";
            default -> null;
        };
    }

    private long sapphirePriceFor(String category) {
        String item = sapphireShopItemFor(category);
        return item == null ? 0L : configs.get("economy/sapphires.yml").getLong("sapphire.shop-items." + item + ".price", 0L);
    }

    private String formatSapphires(long amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private boolean isActive(Player player, String category, String id) {
        if (player == null) return false;
        PlayerProgressionData data = data(player.getUniqueId());
        if (category.equalsIgnoreCase("badges")) return id.equalsIgnoreCase(data.activeBadge());
        if (category.equalsIgnoreCase("trims")) return id.equalsIgnoreCase(data.activeTrim());
        if (category.equalsIgnoreCase("colors")) return id.equalsIgnoreCase(data.activeMessageColor());
        if (category.equalsIgnoreCase("particles")) return id.equalsIgnoreCase(data.activeParticle());
        if (category.equalsIgnoreCase("join_quit_messages")) return id.equalsIgnoreCase(data.activeJoinQuitMessage());
        return id.equalsIgnoreCase(data.activeCosmetic());
    }

    private List<PerkDefinition> definitionsFor(String category) {
        if (category.equalsIgnoreCase("trims") || category.equalsIgnoreCase("ranks")) return List.of();
        return definitions.values().stream()
            .filter(def -> def.category.equalsIgnoreCase(category))
            .toList();
    }

    private int slotIndex(int slot) {
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            if (CATEGORY_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private CategoryPage parseCategoryPage(String context) {
        if (context == null || context.isBlank()) return new CategoryPage("", 0);
        String[] parts = context.split(":", 2);
        int page = 0;
        if (parts.length > 1) {
            try { page = Math.max(0, Integer.parseInt(parts[1])); } catch (Exception ignored) {}
        }
        return new CategoryPage(parts[0].toLowerCase(Locale.ROOT), page);
    }

    private String titleForCategory(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "colors" -> "Chat Colors";
            case "badges" -> "Badges";
            case "join_quit_messages" -> "Join & Quit Messages";
            case "weapon_cosmetics" -> "Weapon Cosmetics";
            case "kill_effects" -> "Kill Effects";
            case "trims" -> "Trims";
            case "tags" -> "Tags";
            case "particles" -> "Particles";
            default -> "Cosmetics";
        };
    }

    private String menuTitle(String key, String fallback) {
        String raw = configs.get("menus/perks.yml").getString("titles." + key, configs.get("menus/perks.yml").getString("title", fallback));
        if (raw == null || raw.isBlank()) raw = fallback;
        return replaceGuiSymbols(raw);
    }

    private String replaceGuiSymbols(String input) {
        String output = applyItemsAdderFontImages(input);
        ConfigurationSection symbols = configs.get("menus/perks.yml").getConfigurationSection("itemsadder-font-symbols");
        if (symbols != null) {
            for (String key : symbols.getKeys(false)) output = output.replace(":" + key + ":", decodeUnicodeEscapes(symbols.getString(key, "")));
        }
        return decodeUnicodeEscapes(output);
    }

    private String applyItemsAdderFontImages(String input) {
        if (input == null || input.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return input == null ? "" : input;
        try {
            Class<?> wrapper = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            java.lang.reflect.Method replace = wrapper.getMethod("replaceFontImages", String.class);
            Object result = replace.invoke(null, input);
            return result instanceof String text ? text : input;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return input;
        }
    }

    private String decodeUnicodeEscapes(String input) {
        if (input == null || input.isBlank() || !input.contains("\\u")) return input == null ? "" : input;
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            if (i + 5 < input.length() && input.charAt(i) == '\\' && input.charAt(i + 1) == 'u') {
                String hex = input.substring(i + 2, i + 6);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                    i += 5;
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
            out.append(input.charAt(i));
        }
        return out.toString();
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
    private record CategoryPage(String category, int page) {}
}



