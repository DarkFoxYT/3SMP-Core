package net.dark.threecore.daily;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.Database;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import net.dark.threecore.afk.VaultEconomyHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DailyRewardManager {
    private static final String ITEM_KEY = "3smpcore_daily_reward_id";
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final DailyRewardStorage storage;
    private final MenuService menuService;
    private final MoneyService moneyService;
    private final VaultEconomyHook vaultHook;
    private final DailyRewardGui gui;
    private final Map<UUID, DailyRewardState> cache = new HashMap<>();
    private final Map<Integer, RewardDefinition> rewards = new LinkedHashMap<>();
    private RewardDefinition weeklyBonus;
    private RewardDefinition monthlyBonus;

    public DailyRewardManager(JavaPlugin plugin, ConfigFiles configs, Database database, MenuService menuService, MoneyService moneyService) {
        this(plugin, configs, new DailyRewardStorage(database), menuService, moneyService);
    }

    public DailyRewardManager(JavaPlugin plugin, ConfigFiles configs, DailyRewardStorage storage, MenuService menuService, MoneyService moneyService) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.menuService = menuService;
        this.moneyService = moneyService;
        this.vaultHook = new VaultEconomyHook(plugin);
        this.gui = new DailyRewardGui(this);
        reload();
    }

    public void reload() {
        rewards.clear();
        weeklyBonus = null;
        monthlyBonus = null;
        ConfigurationSection section = config().getConfigurationSection("rewards.days");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int day = parseInt(key);
                if (day <= 0) continue;
                rewards.put(day, from(section.getConfigurationSection(key), "Day " + day, day));
            }
        }
        if (rewards.isEmpty()) {
            rewards.put(1, simple(1, "Day 1", Material.EMERALD, 250L));
            rewards.put(2, simple(2, "Day 2", Material.DIAMOND, 500L));
            rewards.put(3, simple(3, "Day 3", Material.GOLD_INGOT, 750L));
        }
        rewards.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEachOrdered(entry -> {});
        ConfigurationSection weekly = config().getConfigurationSection("rewards.weekly");
        if (weekly != null && weekly.getBoolean("enabled", false)) weeklyBonus = from(weekly.getConfigurationSection("reward"), "Weekly Bonus", 0);
        ConfigurationSection monthly = config().getConfigurationSection("rewards.monthly");
        if (monthly != null && monthly.getBoolean("enabled", false)) monthlyBonus = from(monthly.getConfigurationSection("reward"), "Monthly Bonus", 0);
        cache.clear();
    }

    public YamlConfiguration config() {
        return configs.get("rewards/daily.yml");
    }

    public void open(Player player) {
        menuService.open(player, gui.build(player));
    }

    public void handleClick(Player player, int slot) {
        int claimSlot = config().getInt("menu.slots.claim", 49);
        int backSlot = config().getInt("menu.slots.back", 45);
        if (slot == backSlot) {
            player.closeInventory();
            return;
        }
        if (slot == claimSlot || slot == config().getInt("menu.slots.current", 13)) {
            claim(player);
            return;
        }
        if (slot == config().getInt("menu.slots.weekly", 31) && weeklyEnabled()) {
            Text.send(player, formatRewardText(weeklyBonus, "Weekly bonus"));
            return;
        }
        if (slot == config().getInt("menu.slots.monthly", 33) && monthlyEnabled()) {
            Text.send(player, formatRewardText(monthlyBonus, "Monthly bonus"));
        }
    }

    public List<String> complete(String[] args) {
        return List.of();
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player player) open(player);
        else Text.send(sender, "<red>Players only.</red>");
    }

    public DailyRewardState state(UUID uuid) {
        return cache.computeIfAbsent(uuid, storage::load);
    }

    public boolean isClaimReady(DailyRewardState state, long now) {
        if (state.lastClaimAt() <= 0L) return true;
        return now - state.lastClaimAt() >= cooldownMillis();
    }

    public int currentDay(DailyRewardState state) {
        int count = Math.max(1, rewards.size());
        int day = (state.streak() % count) + 1;
        return day;
    }

    public String formatRemaining(DailyRewardState state, long now) {
        if (state.lastClaimAt() <= 0L) return "0s";
        long remaining = Math.max(0L, cooldownMillis() - (now - state.lastClaimAt()));
        long seconds = remaining / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        return (hours > 0 ? hours + "h " : "") + (minutes > 0 ? minutes + "m " : "") + secs + "s";
    }

    public List<Integer> rewardIds() {
        return rewards.keySet().stream().sorted().toList();
    }

    public int rewardSlot(int index) {
        List<Integer> slots = config().getIntegerList("menu.reward-slots");
        if (index < 0 || index >= slots.size()) return -1;
        return slots.get(index);
    }

    public boolean weeklyEnabled() {
        return weeklyBonus != null;
    }

    public boolean monthlyEnabled() {
        return monthlyBonus != null;
    }

    public ItemStack rewardCard(int day, boolean unlocked, boolean current, DailyRewardState state) {
        RewardDefinition reward = rewards.getOrDefault(day, rewards.values().iterator().next());
        ItemStack item = createItem(reward, day);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Day:</gray> <white>" + day + "</white>");
        lore.add("<gray>Status:</gray> " + (current ? "<green>Current reward</green>" : unlocked ? "<yellow>Unlocked</yellow>" : "<dark_gray>Locked</dark_gray>"));
        lore.add("<gray>Coins:</gray> <white>" + reward.coins + "</white>");
        if (!reward.commands.isEmpty()) lore.add("<gray>Commands:</gray> <white>" + reward.commands.size() + "</white>");
        if (!reward.items.isEmpty()) lore.add("<gray>Items:</gray> <white>" + reward.items.size() + "</white>");
        lore.addAll(reward.lore);
        meta.lore(lore.stream().map(Text::mm).toList());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack bonusCard(String kind) {
        RewardDefinition reward = "weekly".equalsIgnoreCase(kind) ? weeklyBonus : monthlyBonus;
        Material material = "weekly".equalsIgnoreCase(kind) ? Material.SUNFLOWER : Material.NETHER_STAR;
        String title = "weekly".equalsIgnoreCase(kind) ? "<gradient:#f4cd2a:#eda323:#d28d0d>Weekly Bonus</gradient>" : "<gradient:#f4cd2a:#eda323:#d28d0d>Monthly Bonus</gradient>";
        ItemStack item = reward == null ? new ItemStack(material) : createItem(reward, 0);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(title));
        meta.lore(List.of(
                Text.mm("<gray>Optional bonus reward.</gray>"),
                Text.mm("<gray>Occurs every configured interval.</gray>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public void claim(Player player) {
        DailyRewardState state = state(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (!isClaimReady(state, now)) {
            Text.send(player, applyMessage("messages.not-ready", "<red>You can claim again in <white>{remaining}</white>.</red>", "{remaining}", formatRemaining(state, now)));
            return;
        }
        if (state.lastClaimAt() > 0L && now - state.lastClaimAt() > streakResetMillis()) state.streak(0);
        int day = currentDay(state);
        RewardDefinition reward = rewards.getOrDefault(day, rewards.values().iterator().next());
        giveReward(player, reward);
        state.lastClaimAt(now);
        state.streak(state.streak() + 1);
        state.totalClaims(state.totalClaims() + 1);
        storage.save(player.getUniqueId(), state);
        cache.put(player.getUniqueId(), state);
        Text.send(player, applyMessage("messages.claimed", "<green>Daily reward claimed. Streak: <white>{streak}</white></green>", "{streak}", String.valueOf(state.streak()), "{reward}", reward.displayName));
        player.showTitle(net.kyori.adventure.title.Title.title(Text.mm("<green>Daily Claimed</green>"), Text.mm("<gray>Streak " + state.streak() + "</gray>")));
        if (weeklyEnabled() && state.streak() % weeklyInterval() == 0) giveReward(player, weeklyBonus);
        if (monthlyEnabled() && state.streak() % monthlyInterval() == 0) giveReward(player, monthlyBonus);
        open(player);
    }

    public void giveReward(Player player, RewardDefinition reward) {
        if (reward == null) return;
        long coins = reward.coins;
        if (coins > 0L) depositCoins(player, coins);
        for (String command : reward.commands) {
            String parsed = command.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString()).replace("{amount}", String.valueOf(reward.amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
        for (ConfiguredRewardItem item : reward.items) {
            ItemStack stack = item.create(this);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
    }

    private void depositCoins(Player player, long amount) {
        if (vaultHook.available() && vaultHook.deposit(player, amount)) return;
        moneyService.give(player.getUniqueId(), amount);
    }

    private void sendRewardInfo(Player player, RewardDefinition reward, String label) {
        Text.send(player, formatRewardText(reward, label));
    }

    private String formatRewardText(RewardDefinition reward, String label) {
        if (reward == null) return "<red>No reward configured.</red>";
        return "<gray>" + label + ":</gray> <white>" + reward.displayName + "</white> <gray>| Coins:</gray> <white>" + reward.coins + "</white>";
    }

    private String applyMessage(String path, String fallback, String... replacements) {
        String output = config().getString(path, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            output = output.replace(replacements[i], replacements[i + 1]);
        }
        return output;
    }

    public ItemStack createItem(RewardDefinition reward, int day) {
        ItemStack item = reward.customItem == null ? new ItemStack(reward.material) : reward.customItem.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(reward.displayName));
            List<String> lore = new ArrayList<>(reward.lore);
            lore.add("<gray>Day " + day + "</gray>");
            lore.add("<gray>Coins:</gray> <white>" + reward.coins + "</white>");
            if (!reward.commands.isEmpty()) lore.add("<gray>Commands:</gray> <white>" + reward.commands.size() + "</white>");
            if (!reward.items.isEmpty()) lore.add("<gray>Items:</gray> <white>" + reward.items.size() + "</white>");
            meta.lore(lore.stream().map(Text::mm).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (reward.key != null) meta.getPersistentDataContainer().set(new NamespacedKey(plugin, ITEM_KEY), PersistentDataType.STRING, reward.key);
            item.setItemMeta(meta);
        }
        return item;
    }

    private long cooldownMillis() {
        return Math.max(1L, config().getLong("settings.claim-cooldown-hours", 24L)) * 60L * 60L * 1000L;
    }

    private long streakResetMillis() {
        return Math.max(1L, config().getLong("settings.streak-reset-hours", 48L)) * 60L * 60L * 1000L;
    }

    private int weeklyInterval() {
        return Math.max(1, config().getInt("rewards.weekly.interval-days", 7));
    }

    private int monthlyInterval() {
        return Math.max(1, config().getInt("rewards.monthly.interval-days", 30));
    }

    private int parseInt(String input) {
        try { return Integer.parseInt(input); } catch (NumberFormatException ex) { return -1; }
    }

    private RewardDefinition simple(int day, String name, Material material, long coins) {
        return new RewardDefinition("day-" + day, name, material, null, coins, List.of("<gray>Simple fallback reward.</gray>"), List.of(), List.of(), 1);
    }

    private RewardDefinition from(ConfigurationSection section, String defaultName, int day) {
        if (section == null) return simple(day, defaultName, Material.EMERALD, 0L);
        String display = section.getString("display-name", defaultName);
        String materialName = section.getString("icon.material", section.getString("icon", "EMERALD"));
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null) material = Material.EMERALD;
        String itemsAdder = section.getString("icon.itemsadder", section.getString("itemsadder", ""));
        ItemStack custom = itemsAdder == null || itemsAdder.isBlank() ? null : customItem(itemsAdder, material);
        long coins = section.getLong("coins", 0L);
        List<String> lore = section.getStringList("lore");
        List<String> commands = section.getStringList("commands");
        List<ConfiguredRewardItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection item = itemsSection.getConfigurationSection(key);
                if (item == null) continue;
                items.add(ConfiguredRewardItem.from(item));
            }
        }
        int amount = section.getInt("amount", 1);
        return new RewardDefinition("day-" + day, display, material, custom, coins, lore, commands, items, amount);
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

    private final class RewardDefinition {
        private final String key;
        private final String displayName;
        private final Material material;
        private final ItemStack customItem;
        private final long coins;
        private final List<String> lore;
        private final List<String> commands;
        private final List<ConfiguredRewardItem> items;
        private final int amount;

        private RewardDefinition(String key, String displayName, Material material, ItemStack customItem, long coins, List<String> lore, List<String> commands, List<ConfiguredRewardItem> items, int amount) {
            this.key = key;
            this.displayName = displayName;
            this.material = material;
            this.customItem = customItem;
            this.coins = coins;
            this.lore = lore;
            this.commands = commands;
            this.items = items;
            this.amount = amount;
        }
    }

    private record ConfiguredRewardItem(Material material, String itemsAdder, int amount, String displayName, List<String> lore) {
        private static ConfiguredRewardItem from(ConfigurationSection section) {
            String materialName = section.getString("material", "PAPER");
            Material material = Material.matchMaterial(materialName == null ? "" : materialName);
            if (material == null) material = Material.PAPER;
            return new ConfiguredRewardItem(material, section.getString("itemsadder", ""), Math.max(1, section.getInt("amount", 1)), section.getString("display-name", ""), section.getStringList("lore"));
        }

        private ItemStack create(DailyRewardManager owner) {
            ItemStack item = itemsAdder == null || itemsAdder.isBlank() ? new ItemStack(material) : owner.customItem(itemsAdder, material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (displayName != null && !displayName.isBlank()) meta.displayName(Text.mm(displayName));
                if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(Text::mm).toList());
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }
            item.setAmount(amount);
            return item;
        }
    }
}
