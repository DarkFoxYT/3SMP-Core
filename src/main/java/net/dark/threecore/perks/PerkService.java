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
            case "setprefix", "settag", "setbadge", "settrim", "setmessagecolor", "setparticle", "setcosmetic", "seteffect" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 3) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks " + sub + " <player> <perkId></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); setActive(target.getUniqueId(), sub.substring(3), args[2]); net.dark.threecore.text.Text.send(sender, "<green>Selection updated for " + target.getName() + ".</green>"); }
            case "clearparticle" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks clearparticle <player></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); data(target.getUniqueId()).activeParticle(""); save(target.getUniqueId()); if (particleManager != null && target.isOnline()) particleManager.set(target.getUniqueId(), ""); net.dark.threecore.text.Text.send(sender, "<green>Particle cosmetic cleared for " + target.getName() + ".</green>"); }
            case "list" -> { if (!sender.hasPermission("3smpcore.perks.admin")) { net.dark.threecore.text.Text.send(sender, "<red>No permission.</red>"); return; } if (args.length < 2) { net.dark.threecore.text.Text.send(sender, "<red>Usage: /perks list <player></red>"); return; } OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); net.dark.threecore.text.Text.send(sender, "<gray>Unlocked perks for " + target.getName() + ": " + String.join(", ", data(target.getUniqueId()).unlockedPerks()) + "</gray>"); }
            default -> { if (sender instanceof Player player) openMainMenu(player); else net.dark.threecore.text.Text.send(sender, "<gray>Use /perks reload, /perks unlock, /perks remove, /perks list, /perks setprefix, /perks settag, /perks settrim, or /perks setmessagecolor.</gray>"); }
        }
    }

    public void openMainMenu(Player player) { menuService.open(player, buildMainMenu()); }

    public Inventory buildMainMenu() {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_MAIN, "main"), 54, "3SMP Perks");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        inv.setItem(7, icon(org.bukkit.Material.BOOK, "<gradient:#1A2A4A:#D6E8F7>Summary</gradient>", List.of("<gray>Review your active selections.</gray>")));
        inv.setItem(10, icon(org.bukkit.Material.NAME_TAG, "<gradient:#60a5fa:#c084fc>Prefixes</gradient>", List.of("<gray>Choose a chat prefix.</gray>")));
        inv.setItem(12, icon(org.bukkit.Material.PAPER, "<gradient:#34d399:#22c55e>Tags</gradient>", List.of("<gray>Choose a chat tag.</gray>")));
        inv.setItem(13, icon(org.bukkit.Material.NETHER_STAR, "<gradient:#f59e0b:#D6E8F7>Badges</gradient>", List.of("<gray>Show a badge before your prefix in chat.</gray>", "<gray>ItemsAdder glyphs are supported.</gray>")));
        inv.setItem(14, icon(org.bukkit.Material.AMETHYST_SHARD, "<gradient:#f59e0b:#f97316>Trims</gradient>", List.of("<gray>Choose a rank-based armor trim.</gray>", "<gray>Some trims require a configured rank.</gray>")));
        inv.setItem(16, icon(org.bukkit.Material.BOOK, "<gradient:#a78bfa:#f472b6>Message Colors</gradient>", List.of("<gray>Choose your chat message color.</gray>")));
        inv.setItem(18, icon(org.bukkit.Material.FIREWORK_ROCKET, "<gradient:#d6e8f7:#1A2A4A>Particles</gradient>", List.of("<gray>Choose a particle cosmetic.</gray>", "<gray>Adds extra visuals to your style.</gray>")));
        inv.setItem(20, icon(org.bukkit.Material.GLOWSTONE_DUST, "<gradient:#f59e0b:#fbbf24>Effects</gradient>", List.of("<gray>Choose a light cosmetic potion effect.</gray>")));
        inv.setItem(24, icon(org.bukkit.Material.ENDER_EYE, "<gradient:#60a5fa:#34d399>Cosmetics</gradient>", List.of("<gray>Choose a general cosmetic style.</gray>")));
        inv.setItem(22, icon(org.bukkit.Material.CLOCK, "<gradient:#a78bfa:#f472b6>Selections</gradient>", List.of("<gray>Review your current choices.</gray>")));
        return inv;
    }

    public void handleMenuClick(Player player, int slot) {
        if (slot == 7 || slot == 22) menuService.open(player, buildSummaryMenu(player));
        else if (slot == 10) menuService.open(player, buildCategoryMenu("prefixes", "Prefixes"));
        else if (slot == 12) menuService.open(player, buildCategoryMenu("tags", "Tags"));
        else if (slot == 13) menuService.open(player, buildCategoryMenu("badges", "Badges"));
        else if (slot == 14) menuService.open(player, buildCategoryMenu("trims", "Trims"));
        else if (slot == 16) menuService.open(player, buildCategoryMenu("colors", "Message Colors"));
        else if (slot == 18) menuService.open(player, buildCategoryMenu("particles", "Particles"));
        else if (slot == 20) menuService.open(player, buildCategoryMenu("effects", "Effects"));
        else if (slot == 24) menuService.open(player, buildCategoryMenu("cosmetics", "Cosmetics"));
    }

    public Inventory buildSummaryMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_MAIN, "summary"), 27, "3SMP Perks Summary");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        PlayerProgressionData data = data(player.getUniqueId());
        inv.setItem(11, icon(org.bukkit.Material.NAME_TAG, "<gradient:#60a5fa:#c084fc>Chat Style</gradient>", List.of(
                "<gray>Prefix:</gray> <white>" + data.activePrefix() + "</white>",
                "<gray>Tag:</gray> <white>" + data.activeTag() + "</white>",
                "<gray>Badge:</gray> <white>" + data.activeBadge() + "</white>",
                "<gray>Message color:</gray> <white>" + data.activeMessageColor() + "</white>"
        )));
        inv.setItem(13, icon(org.bukkit.Material.AMETHYST_SHARD, "<gradient:#f59e0b:#f97316>Appearance</gradient>", List.of(
                "<gray>Trim:</gray> <white>" + data.activeTrim() + "</white>",
                "<gray>Particle cosmetic:</gray> <white>" + (data.activeParticle().isBlank() ? "none" : data.activeParticle()) + "</white>",
                "<gray>Cosmetic:</gray> <white>" + (data.activeCosmetic().isBlank() ? "none" : data.activeCosmetic()) + "</white>",
                "<gray>Effect:</gray> <white>" + (data.activeEffect().isBlank() ? "none" : data.activeEffect()) + "</white>"
        )));
        inv.setItem(15, icon(org.bukkit.Material.CLOCK, "<gradient:#34d399:#22c55e>Unlocked</gradient>", List.of(
                "<gray>Unlocked perks:</gray> <white>" + data.unlockedPerks().size() + "</white>"
        )));
        inv.setItem(22, icon(org.bukkit.Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to perks.</gray>")));
        return inv;
    }

    public Inventory buildCategoryMenu(String category, String title) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.PERKS_CATEGORY, category), 54, "3SMP " + title);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
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
                if (category.equalsIgnoreCase("prefixes")) setActive(player.getUniqueId(), "prefix", def.id);
                else if (category.equalsIgnoreCase("tags")) setActive(player.getUniqueId(), "tag", def.id);
                else if (category.equalsIgnoreCase("badges")) setActive(player.getUniqueId(), "badge", def.id);
                else if (category.equalsIgnoreCase("trims")) setActive(player.getUniqueId(), "trim", def.id);
                else if (category.equalsIgnoreCase("colors")) setActive(player.getUniqueId(), "messagecolor", def.id);
                else if (category.equalsIgnoreCase("particles")) setActive(player.getUniqueId(), "particle", def.id);
                else if (category.equalsIgnoreCase("effects")) setActive(player.getUniqueId(), "effect", def.id);
                else if (category.equalsIgnoreCase("cosmetics")) setActive(player.getUniqueId(), "cosmetic", def.id);
                net.dark.threecore.text.Text.send(player, "<green>Selected " + def.displayName + "</green>");
                return;
            }
            index++;
            if (index == 17 || index == 26 || index == 35 || index == 44) index++;
        }
    }

    public void unlock(UUID uuid, String perkId) { data(uuid).unlockedPerks().add(perkId.toLowerCase(Locale.ROOT)); save(uuid); }
    public void remove(UUID uuid, String perkId) { data(uuid).unlockedPerks().remove(perkId.toLowerCase(Locale.ROOT)); save(uuid); }

    public void setActive(UUID uuid, String type, String perkId) {
        PlayerProgressionData data = data(uuid);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "prefix" -> data.activePrefix(perkId);
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
            case "prefix" -> data.activePrefix("");
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
        return "<gray>Prefix: " + data.activePrefix() + " | Tag: " + data.activeTag() + " | Badge: " + data.activeBadge() + " | Trim: " + data.activeTrim() + " | Msg: " + data.activeMessageColor() + " | Particles: " + (data.activeParticle().isBlank() ? "none" : data.activeParticle()) + "</gray>";
    }

    public boolean hasUnlocked(UUID uuid, String perkId) { return data(uuid).unlockedPerks().contains(perkId.toLowerCase(Locale.ROOT)); }
    public PlayerProgressionData data(UUID uuid) { return cache.computeIfAbsent(uuid, repository::load); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
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

    private void save(UUID uuid) { repository.save(data(uuid)); }

    private boolean canUse(Player player, PerkDefinition def) {
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
        ConfigurationSection section = configs.get("effects.yml").getConfigurationSection("effects." + id.toLowerCase(Locale.ROOT));
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
        loadSection("prefixes.yml", "prefixes", org.bukkit.Material.NAME_TAG);
        loadSection("tags.yml", "tags", org.bukkit.Material.PAPER);
        loadSection("badges.yml", "badges", org.bukkit.Material.NETHER_STAR);
        loadSection("colors.yml", "colors", org.bukkit.Material.BLUE_DYE);
        loadSection("trims.yml", "trims", org.bukkit.Material.AMETHYST_SHARD);
        loadSection("particles.yml", "particles", org.bukkit.Material.FIREWORK_ROCKET);
        loadSection("effects.yml", "effects", org.bukkit.Material.GLOWSTONE_DUST);
        loadSection("cosmetics.yml", "cosmetics", org.bukkit.Material.ENDER_EYE);
    }

    private void loadSection(String file, String root, org.bukkit.Material fallbackMaterial) {
        var section = configs.get(file).getConfigurationSection(root);
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            definitions.put(id.toLowerCase(Locale.ROOT), new PerkDefinition(id.toLowerCase(Locale.ROOT), root, parseMaterial(item.getString("icon", fallbackMaterial.name())), item.getString("display-name", id), item.getString("permission", item.getString("required-permission", "")), item.getBoolean("default-unlocked", false), item.getStringList("lore")));
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
    private record PerkDefinition(String id, String category, org.bukkit.Material material, String displayName, String permission, boolean defaultUnlocked, List<String> lore) {}
    private record TrimDefinition(String id, String requiredPermission, String requiredRank) {}
}
