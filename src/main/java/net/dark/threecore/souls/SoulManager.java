package net.dark.threecore.souls;

import net.dark.threecore.afk.VaultEconomyHook;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final SoulStorage storage;
    private final MoneyService moneyService;
    private final MenuService menuService;
    private final VaultEconomyHook vaultHook;
    private final SoulGui gui;
    private final ConcurrentHashMap<UUID, Long> cachedBalances = new ConcurrentHashMap<>();

    public SoulManager(JavaPlugin plugin, ConfigFiles configs, SoulStorage storage, MoneyService moneyService, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.moneyService = moneyService;
        this.menuService = menuService;
        this.vaultHook = new VaultEconomyHook(plugin);
        this.gui = new SoulGui(this);
        reload();
    }

    public void reload() {
        cachedBalances.clear();
    }

    public org.bukkit.configuration.file.YamlConfiguration config() {
        return configs.get("economy/souls.yml");
    }

    public long balance(UUID uuid) {
        return cachedBalances.computeIfAbsent(uuid, storage::load);
    }

    public void set(UUID uuid, long amount) {
        long next = Math.max(0L, amount);
        cachedBalances.put(uuid, next);
        storage.save(uuid, next);
    }

    public void give(UUID uuid, long amount) {
        set(uuid, balance(uuid) + Math.max(0L, amount));
    }

    public boolean take(UUID uuid, long amount) {
        long current = balance(uuid);
        if (amount < 0L || current < amount) return false;
        set(uuid, current - amount);
        return true;
    }

    public void reset(UUID uuid) {
        set(uuid, 0L);
    }

    public void open(Player player) {
        menuService.open(player, gui.build(player));
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 0) {
                open(player);
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "sell" -> sellSouls(player);
                case "trade" -> open(player);
                default -> open(player);
            }
        } else {
            Text.send(sender, "<red>Players only.</red>");
        }
    }

    public void handleClick(Player player, int slot) {
        if (slot == 11) {
            sendBalance(player);
        } else if (slot == 13) {
            sellSouls(player);
        } else if (slot == 15) {
            openRewards(player);
        } else if (slot == 22) {
            player.closeInventory();
        }
    }

    public void handleRewardsClick(Player player, int slot) {
        int index = 0;
        var section = config().getConfigurationSection("rewards");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var reward = section.getConfigurationSection(id);
            if (reward == null) continue;
            if (slot == rewardSlot(index)) {
                claimReward(player, id);
                return;
            }
            index++;
        }
        if (slot == 49) {
            open(player);
        }
    }

    public void sellSouls(Player player) {
        long bal = balance(player.getUniqueId());
        if (bal <= 0) {
            Text.send(player, "<red>You have no souls to sell.</red>");
            return;
        }
        long value = Math.max(1L, config().getLong("sell-value-per-soul", 25L));
        long coins = bal * value;
        reset(player.getUniqueId());
        if (vaultHook.available()) {
            if (!vaultHook.deposit(player, coins)) {
                moneyService.give(player.getUniqueId(), coins);
            }
        } else {
            moneyService.give(player.getUniqueId(), coins);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        Text.send(player, "<green>Sold " + bal + " souls for <white>" + moneyService.format((double) coins) + "</white>.</green>");
        open(player);
    }

    public void openRewards(Player player) {
        menuService.open(player, gui.buildRewards(player));
    }

    private int rewardSlot(int index) {
        int slot = 10 + index;
        if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
        return slot;
    }

    public void claimReward(Player player, String rewardId) {
        var sec = config().getConfigurationSection("rewards." + rewardId);
        if (sec == null) {
            Text.send(player, "<red>That reward is not configured.</red>");
            return;
        }
        long cost = sec.getLong("cost", 0L);
        if (!take(player.getUniqueId(), cost)) {
            Text.send(player, "<red>You do not have enough souls.</red>");
            return;
        }
        long coins = sec.getLong("coins", 0L);
        if (coins > 0L) {
            if (!vaultHook.available() || !vaultHook.deposit(player, coins)) {
                moneyService.give(player.getUniqueId(), coins);
            }
        }
        for (String command : sec.getStringList("commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()).replace("{amount}", String.valueOf(cost)));
        }
        for (String itemName : sec.getStringList("items")) {
            ItemStack item = new ItemStack(parseMaterial(itemName));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }
            player.getInventory().addItem(item);
        }
        Text.send(player, "<green>Reward claimed with souls.</green>");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        openRewards(player);
    }

    public void sendBalance(Player player) {
        Text.send(player, "<gray>Your souls:</gray> <gradient:#6b7280:#f3f4f6>" + balance(player.getUniqueId()) + "</gradient>");
    }

    private void openRewardButton(Player player, String rewardId) {
        claimReward(player, rewardId);
    }

    private Material parseMaterial(String value) {
        Material material = Material.matchMaterial(value == null ? "" : value.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }
}
