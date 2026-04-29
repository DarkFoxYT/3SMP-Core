package net.dark.threecore.fishing;

import net.dark.threecore.afk.VaultEconomyHook;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingRewardManager {
    private static final String FISH_KEY = "3smpcore_fishing_fish";
    private static final String SESSION_KEY = "3smpcore_fishing_session";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MoneyService moneyService;
    private final MenuService menuService;
    private final VaultEconomyHook vaultHook;
    private final FishingStatsStorage storage;
    private final FishingGui gui;
    private final Map<UUID, FishingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, FishRarity> rarities = new ConcurrentHashMap<>();
    private BukkitTask task;

    public FishingRewardManager(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MoneyService moneyService, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.moneyService = moneyService;
        this.menuService = menuService;
        this.vaultHook = new VaultEconomyHook(plugin);
        this.storage = new FishingStatsStorage(((net.dark.threecore.ThreeSMPCorePlugin) plugin).getDatabase());
        this.gui = new FishingGui(this);
        reload();
        startTask();
    }

    public void reload() {
        rarities.clear();
        ConfigurationSection section = config().getConfigurationSection("rarities");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection rarity = section.getConfigurationSection(key);
                if (rarity == null) continue;
                rarities.put(key.toLowerCase(Locale.ROOT), new FishRarity(
                        key.toLowerCase(Locale.ROOT),
                        rarity.getDouble("weight", 1.0D),
                        rarity.getLong("min-wait-seconds", 0L),
                        rarity.getLong("max-wait-seconds", 120L),
                        rarity.getLong("coins-min", 0L),
                        rarity.getLong("coins-max", 0L),
                        rarity.getStringList("messages")
                ));
            }
        }
        if (rarities.isEmpty()) {
            rarities.put("common", new FishRarity("common", 60.0D, 5L, 45L, 25L, 100L, List.of()));
            rarities.put("uncommon", new FishRarity("uncommon", 25.0D, 10L, 60L, 100L, 250L, List.of()));
            rarities.put("rare", new FishRarity("rare", 10.0D, 20L, 90L, 250L, 750L, List.of()));
            rarities.put("legendary", new FishRarity("legendary", 5.0D, 30L, 120L, 1000L, 2500L, List.of()));
        }
        if (task == null || task.isCancelled()) startTask();
    }

    public org.bukkit.configuration.file.YamlConfiguration config() {
        return configs.get("fishing/fishing.yml");
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public void open(Player player) {
        FishingSession session = createSession(player, true);
        sessions.put(player.getUniqueId(), session);
        menuService.open(player, gui.build(session));
        gui.renderFishing(session);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.35f);
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean isFishingRod(ItemStack item) {
        if (item == null) return false;
        Material material = item.getType();
        return material == Material.FISHING_ROD || material == Material.CARROT_ON_A_STICK || material == Material.WARPED_FUNGUS_ON_A_STICK;
    }

    public void close(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean handleClick(Player player, int rawSlot) {
        FishingSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            if (rawSlot == 20) { open(player); return true; }
            if (rawSlot == 22) { sellFish(player); return true; }
            if (rawSlot == 24) { sendStats(player); return true; }
            return true;
        }
        if (rawSlot == 22) {
            close(player);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            return true;
        }
        if (!session.ready(System.currentTimeMillis()) && rawSlot == session.currentSlot()) return true;
        if (session.ready(System.currentTimeMillis()) && rawSlot == session.currentSlot()) {
            session.click(player, gui);
            if (session.caught()) {
                finishCatch(player, session);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.3f);
            }
            return true;
        }
        return true;
    }

    public void startSession(UUID uuid) {
        FishingSession session = sessions.get(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (session == null || player == null || !player.isOnline()) return;
        gui.renderWaiting(session);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (FishingSession session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                sessions.remove(session.playerId());
                continue;
            }
            if (session.caught()) continue;
            if (session.expired(now)) {
                playEscapeAnimation(player, session);
                continue;
            }
            if (session.ready(now)) {
                session.animationFrame(session.animationFrame() + 1);
                gui.renderFishing(session);
                player.sendActionBar(Text.mm("<yellow>Fish now! 3 clicks. Rarity: <white>" + session.rarity() + "</white></yellow>"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.12f, 1.6f);
            } else {
                session.animationFrame(session.animationFrame() + 1);
                long remaining = Math.max(0L, (session.readyAt() - now) / 1000L);
                gui.renderWaiting(session);
                player.sendActionBar(Text.mm("<gray>Waiting for fish: <white>" + remaining + "s</white></gray>"));
                if (session.animationFrame() % 20 == 0) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.08f, 1.8f);
            }
        }
    }

    public void finishCatch(Player player, FishingSession session) {
        session.animationFrame(0);
        new BukkitRunnable() {
            int frame = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !sessions.containsKey(player.getUniqueId())) { cancel(); return; }
                if (frame >= 12) {
                    FishingStats stats = storage.load(player.getUniqueId());
                    stats = stats.addFish(1);
                    if (isRare(session.rarity())) stats = stats.addRare(1);
                    long points = pointsFor(session.rarity());
                    stats = stats.addPoints(points);
                    storage.save(player.getUniqueId(), stats);
                    giveReward(player, session.rarity(), points);
                    sessions.remove(player.getUniqueId());
                    Text.send(player, "<green>You caught a <white>" + session.rarity() + "</white> fish.</green>");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.15f);
                    player.closeInventory();
                    cancel();
                    return;
                }
                session.animationFrame(frame);
                gui.renderCaught(session);
                if (frame % 2 == 0) player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.35f, 1.4f);
                frame++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void playEscapeAnimation(Player player, FishingSession session) {
        session.animationFrame(0);
        new BukkitRunnable() {
            int frame = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !sessions.containsKey(player.getUniqueId())) { cancel(); return; }
                if (frame >= 8) {
                    Text.send(player, "<red>The fish escaped.</red>");
                    player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 0.8f);
                    sessions.remove(player.getUniqueId());
                    player.closeInventory();
                    cancel();
                    return;
                }
                session.animationFrame(frame);
                gui.renderEscaped(session);
                if (frame % 2 == 0) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.7f);
                frame++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void shutdown() {
        sessions.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void debugStatus(Player player) {
        FishingSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            Text.send(player, "<gray>Fishing debug:</gray> <white>no active session</white>");
            return;
        }
        Text.send(player, "<gradient:#1A2A4A:#D6E8F7>Fishing Debug</gradient>");
        Text.send(player, "<gray>Rarity:</gray> <white>" + session.rarity() + "</white>");
        Text.send(player, "<gray>Clicks:</gray> <white>" + session.clicks() + "/3</white>");
        Text.send(player, "<gray>Current slot:</gray> <white>" + session.currentSlot() + "</white>");
        Text.send(player, "<gray>Ready in:</gray> <white>" + remainingCatchSeconds(session) + "s</white>");
        Text.send(player, "<gray>Fish count:</gray> <white>" + session.fishCount() + "</white>");
    }

    public void handleCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Players only.</red>");
            return;
        }
        if (args.length == 0) {
            openHub(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sellFish(player);
            case "stats" -> sendStats(player);
            case "play", "start" -> open(player);
            default -> openHub(player);
        }
    }

    public List<String> complete(String[] args) {
        return args.length <= 1 ? List.of("sell", "stats", "play") : List.of();
    }

    public void openHub(Player player) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.FISHING_MAIN, "hub"), 45, config().getString("hub.title", "3SMP Fishing"));
        ItemStack frame = frameItem();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, frame);
        FishingStats stats = storage.load(player.getUniqueId());
        inv.setItem(4, menuItem(Material.FISHING_ROD, "<gradient:#38bdf8:#22c55e>Fishing Dock</gradient>", List.of("<gray>Animated fishing, sellable catches, treasure rolls.</gray>", "<gray>Points:</gray> <white>" + stats.fishingPoints() + "</white>")));
        inv.setItem(20, menuItem(Material.COD, "<gradient:#38bdf8:#F8FBFF>Start Fishing</gradient>", List.of("<gray>Open the animated catch minigame.</gray>", "<yellow>Click to play.</yellow>")));
        inv.setItem(22, menuItem(Material.EMERALD, "<gradient:#22c55e:#facc15>Sell Fish</gradient>", List.of("<gray>Sell all tagged custom fish in your inventory.</gray>", "<yellow>Click to cash out.</yellow>")));
        inv.setItem(24, menuItem(Material.BOOK, "<gradient:#5B8DD9:#F8FBFF>Your Stats</gradient>", List.of("<gray>Fish caught:</gray> <white>" + stats.fishCaught() + "</white>", "<gray>Rare catches:</gray> <white>" + stats.rareCatches() + "</white>", "<gray>Fishing points:</gray> <white>" + stats.fishingPoints() + "</white>")));
        menuService.open(player, inv);
    }

    public void sendStats(Player player) {
        FishingStats stats = storage.load(player.getUniqueId());
        Text.send(player, "<gradient:#38bdf8:#22c55e>Fishing Stats</gradient>");
        Text.send(player, "<gray>Fish caught:</gray> <white>" + stats.fishCaught() + "</white>");
        Text.send(player, "<gray>Rare catches:</gray> <white>" + stats.rareCatches() + "</white>");
        Text.send(player, "<gray>Fishing points:</gray> <white>" + stats.fishingPoints() + "</white>");
    }

    public void sellFish(Player player) {
        long total = 0L;
        int sold = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            long each = sellValue(item);
            if (each <= 0L) continue;
            int amount = item.getAmount();
            total += each * amount;
            sold += amount;
            contents[i] = null;
        }
        player.getInventory().setContents(contents);
        if (total <= 0L) {
            Text.send(player, "<red>You do not have any sellable custom fish.</red>");
            return;
        }
        moneyService.give(player.getUniqueId(), total);
        Text.send(player, "<green>Sold <white>" + sold + "</white> fish for <gold>$" + total + "</gold>.</green>");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.35f);
    }

    public FishingStats stats(UUID uuid) {
        return storage.load(uuid);
    }

    public org.bukkit.configuration.file.YamlConfiguration configFile() { return config(); }

    public int defaultFishSlot() {
        return config().getInt("menu.fish-slot", 13);
    }

    public int randomFishSlot(int previous) {
        List<Integer> slots = config().getIntegerList("menu.fish-slots");
        if (slots.isEmpty()) return defaultFishSlot();
        if (slots.size() == 1) return slots.getFirst();
        int next = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
        if (next == previous) next = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
        return next;
    }

    public Material frame() {
        Material material = Material.matchMaterial(config().getString("menu.frame.material", "BLUE_STAINED_GLASS_PANE"));
        return material == null ? Material.BLUE_STAINED_GLASS_PANE : material;
    }

    public ItemStack frameItem() {
        return new ItemStack(frame());
    }

    public String title() {
        return config().getString("menu.title", "3SMP Fishing");
    }

    public long remainingCatchSeconds(FishingSession session) {
        return Math.max(0L, (session.catchDeadline() - System.currentTimeMillis()) / 1000L);
    }

    public ItemStack fishIcon(String rarity) {
        String icon = config().getString("rarities." + rarity.toLowerCase(Locale.ROOT) + ".icon.itemsadder", "");
        Material fallback = parseMaterial(config().getString("rarities." + rarity.toLowerCase(Locale.ROOT) + ".icon.material", "COD"));
        ItemStack item = icon.isBlank() ? new ItemStack(fallback) : customItem(icon, fallback);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(config().getString("rarities." + rarity.toLowerCase(Locale.ROOT) + ".display-name", "<white>" + rarity + " Fish</white>")));
            meta.lore(List.of(Text.mm("<gray>Click 3 times to catch.</gray>")));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Material frameMaterial() { return frame(); }

    public long pointsFor(String rarity) {
        FishRarity data = rarities.get(rarity.toLowerCase(Locale.ROOT));
        return data == null ? 1L : Math.max(1L, config().getLong("rarities." + rarity.toLowerCase(Locale.ROOT) + ".fishing-points", data.points()));
    }

    public boolean isRare(String rarity) {
        String r = rarity.toLowerCase(Locale.ROOT);
        return r.equals("rare") || r.equals("legendary");
    }

    private FishingSession createSession(Player player) {
        return createSession(player, false);
    }

    private FishingSession createSession(Player player, boolean quickStart) {
        String rarity = rarityFor(player);
        long waitSeconds = quickStart ? Math.max(0L, config().getLong("session.instant-ready-delay-seconds", 1L)) : sessionWaitSeconds(player);
        long now = System.currentTimeMillis();
        int fishCount = fishCountFor(player);
        long catchWindow = Math.max(2L, config().getLong("session.catch-window-seconds", 5L));
        return new FishingSession(player.getUniqueId(), now, now + waitSeconds * 1000L, now + waitSeconds * 1000L + catchWindow * 1000L, fishCount, rarity, this);
    }

    private void startTask() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private long sessionWaitSeconds(Player player) {
        long min = Math.max(0L, config().getLong("session.wait-seconds.min", 0L));
        long max = Math.max(min, config().getLong("session.wait-seconds.max", 120L));
        long base = ThreadLocalRandom.current().nextLong(min, max + 1L);
        int lure = enchantLevel(player, "LURE");
        int luck = enchantLevel(player, "LUCK_OF_THE_SEA");
        base = Math.max(0L, base - (lure * Math.max(2L, config().getLong("enchantments.lure.reduce-seconds-per-level", 8L))));
        if (luck > 0 && ThreadLocalRandom.current().nextDouble() < 0.05D * luck) base = Math.max(0L, base - 10L);
        return Math.min(max, base);
    }

    private int fishCountFor(Player player) {
        int count = Math.max(1, config().getInt("session.base-fish-per-cast", 1));
        int trawler = enchantLevel(player, "TRAWLER");
        if (trawler <= 0) return count;
        double chance = config().getDouble("enchantments.trawler.multi-catch-chance-per-level", 0.10D) * trawler;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            int max = Math.max(1, config().getInt("enchantments.trawler.max-fish", trawler >= 3 ? 10 : trawler >= 2 ? 5 : 2));
            count = ThreadLocalRandom.current().nextInt(2, max + 1);
        }
        return count;
    }

    private String rarityFor(Player player) {
        double luck = enchantLevel(player, "LUCK_OF_THE_SEA");
        double rankBoost = config().getDouble("rarity-rank-bonus." + highestRank(player).toLowerCase(Locale.ROOT), 0.0D);
        double weightTotal = 0.0D;
        for (FishRarity rarity : rarities.values()) weightTotal += Math.max(0.01D, rarity.weight() + rankBoost + (luck * rarityLuckBonus(rarity.name(), luck)));
        double roll = ThreadLocalRandom.current().nextDouble(weightTotal);
        for (FishRarity rarity : rarities.values()) {
            roll -= Math.max(0.01D, rarity.weight() + rankBoost + (luck * rarityLuckBonus(rarity.name(), luck)));
            if (roll <= 0.0D) return rarity.name();
        }
        return "common";
    }

    private double rarityLuckBonus(String rarity, double luckLevel) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "legendary" -> luckLevel * 0.75D;
            case "rare" -> luckLevel * 0.5D;
            case "uncommon" -> luckLevel * 0.25D;
            default -> luckLevel * 0.05D;
        };
    }

    private String highestRank(Player player) {
        String[] groups = new String[] {"owner", "sr-admin", "admin", "sr-mod", "mod", "jr-mod", "patron", "ultra", "elite", "mvp", "pro", "vip", "default"};
        for (String group : groups) {
            if (player.hasPermission("group." + group) || player.hasPermission("3smpcore.rank." + group)) return group;
        }
        return "default";
    }

    private int enchantLevel(Player player, String enchant) {
        int level = 0;
        for (ItemStack item : player.getInventory().getContents()) level = Math.max(level, enchantLevel(item, enchant));
        return level;
    }

    private int enchantLevel(ItemStack item, String enchant) {
        if (item == null || !item.hasItemMeta()) return 0;
        String path = "enchantments." + enchant.toLowerCase(Locale.ROOT) + ".pdc-key";
        String key = config().getString(path, "");
        if (key.isBlank()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, key), PersistentDataType.INTEGER, 0);
    }

    private void giveReward(Player player, String rarity, long points) {
        ConfigurationSection sec = config().getConfigurationSection("rewards." + rarity.toLowerCase(Locale.ROOT));
        if (sec == null) return;
        long coins = ThreadLocalRandom.current().nextLong(sec.getLong("coins-min", 0L), sec.getLong("coins-max", 0L) + 1L);
        if (coins > 0L) {
            if (vaultHook.available()) vaultHook.deposit(player, coins);
            else moneyService.give(player.getUniqueId(), coins);
        }
        for (String command : sec.getStringList("commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()).replace("{amount}", String.valueOf(coins)));
        }
        for (String itemId : sec.getStringList("itemsadder")) {
            ItemStack custom = customItem(itemId, Material.COD);
            player.getInventory().addItem(custom);
        }
        player.getInventory().addItem(caughtFishItem(rarity));
        for (String materialName : sec.getStringList("items")) {
            Material material = parseMaterial(materialName);
            player.getInventory().addItem(new ItemStack(material));
        }
        rollBonusLoot(player, rarity);
        player.sendActionBar(Text.mm("<green>Fishing reward: <white>" + rarity + "</white> (+<white>" + points + "</white> points)</green>"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    private void rollBonusLoot(Player player, String rarity) {
        ConfigurationSection section = config().getConfigurationSection("bonus-loot");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection loot = section.getConfigurationSection(id);
            if (loot == null) continue;
            List<String> allowed = loot.getStringList("rarities").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
            if (!allowed.isEmpty() && !allowed.contains(rarity.toLowerCase(Locale.ROOT))) continue;
            double chance = loot.getDouble("chance", 0.0D);
            if (ThreadLocalRandom.current().nextDouble() > chance) continue;
            ItemStack item = loot.getString("itemsadder", "").isBlank()
                    ? new ItemStack(parseMaterial(loot.getString("material", "SADDLE")), Math.max(1, loot.getInt("amount", 1)))
                    : customItem(loot.getString("itemsadder", ""), parseMaterial(loot.getString("material", "SADDLE")));
            ItemMeta meta = item.getItemMeta();
            if (meta != null && !loot.getString("display-name", "").isBlank()) {
                meta.displayName(Text.mm(loot.getString("display-name")));
                meta.lore(loot.getStringList("lore").stream().map(Text::mm).toList());
                item.setItemMeta(meta);
            }
            player.getInventory().addItem(item);
            String message = loot.getString("message", "<gradient:#f59e0b:#fb7185>Bonus catch!</gradient>");
            Text.send(player, message);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    private ItemStack caughtFishItem(String rarity) {
        ConfigurationSection sec = randomFishItemSection(rarity);
        Material fallback = parseMaterial(sec == null ? "COD" : sec.getString("material", "COD"));
        ItemStack item = sec == null || sec.getString("itemsadder", "").isBlank() ? new ItemStack(fallback) : customItem(sec.getString("itemsadder", ""), fallback);
        ItemMeta meta = item.getItemMeta();
        long value = sec == null ? pointsFor(rarity) * 25L : sec.getLong("sell-value", pointsFor(rarity) * 25L);
        if (meta != null) {
            meta.displayName(Text.mm(sec == null ? "<white>" + rarity + " Fish</white>" : sec.getString("display-name", "<white>" + rarity + " Fish</white>")));
            List<String> lore = new ArrayList<>(sec == null ? List.of() : sec.getStringList("lore"));
            lore.add("<gray>Sell value:</gray> <gold>$" + value + "</gold>");
            lore.add("<dark_gray>Use /fishing sell to cash out.</dark_gray>");
            meta.lore(lore.stream().map(Text::mm).toList());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, FISH_KEY), PersistentDataType.STRING, rarity.toLowerCase(Locale.ROOT));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, FISH_KEY + "_value"), PersistentDataType.LONG, value);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ConfigurationSection randomFishItemSection(String rarity) {
        String normalized = rarity.toLowerCase(Locale.ROOT);
        ConfigurationSection catalog = config().getConfigurationSection("fish-catalog." + normalized);
        if (catalog != null && !catalog.getKeys(false).isEmpty()) {
            List<String> keys = new ArrayList<>(catalog.getKeys(false));
            return catalog.getConfigurationSection(keys.get(ThreadLocalRandom.current().nextInt(keys.size())));
        }
        return config().getConfigurationSection("fish-items." + normalized);
    }

    private long sellValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0L;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, FISH_KEY + "_value"), PersistentDataType.LONG, 0L);
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack customItem(String id, Material fallback) {
        String raw = id == null ? "" : id.trim();
        if (!raw.isBlank() && Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Method getInstance = customStack.getMethod("getInstance", String.class);
                Object stack = getInstance.invoke(null, raw);
                if (stack != null) {
                    Method getItemStack = stack.getClass().getMethod("getItemStack");
                    Object item = getItemStack.invoke(stack);
                    if (item instanceof ItemStack itemStack) return itemStack;
                }
            } catch (Exception ignored) {
            }
        }
        return new ItemStack(fallback);
    }

    private Material parseMaterial(String name) {
        Material material = Material.matchMaterial(name == null ? "" : name.toUpperCase(Locale.ROOT));
        return material == null ? Material.COD : material;
    }

    private record FishRarity(String name, double weight, long minWait, long maxWait, long coinsMin, long coinsMax, List<String> messages) {
        long points() { return Math.max(1L, (long) weight); }
    }
}
