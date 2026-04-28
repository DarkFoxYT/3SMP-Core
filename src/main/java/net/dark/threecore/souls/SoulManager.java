package net.dark.threecore.souls;

import net.dark.threecore.afk.VaultEconomyHook;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulManager implements Listener {
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
        if (!canUseForgeHere(player)) {
            Text.send(player, config().getString("npc.only-dungeon-spawn-message", "<red>The Soul Forge is only available at dungeon spawn.</red>"));
            return;
        }
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
        if (slot == 4) {
            sendBalance(player);
        } else if (slot == 20) {
            openForge(player);
        } else if (slot == 22) {
            sellSouls(player);
        } else if (slot == 24) {
            openRewards(player);
        } else if (slot == 40) {
            player.closeInventory();
        }
    }

    public void openForge(Player player) {
        if (!canUseForgeHere(player)) {
            Text.send(player, config().getString("npc.only-dungeon-spawn-message", "<red>The Soul Forge is only available at dungeon spawn.</red>"));
            return;
        }
        menuService.open(player, gui.buildForge(player));
    }

    public long enchantCost() {
        return Math.max(1L, config().getLong("enchanting.cost", 35L));
    }

    public void handleForgeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot >= event.getView().getTopInventory().getSize()) {
            event.setCancelled(event.isShiftClick());
            return;
        }
        if (slot == 20) {
            ItemStack current = event.getView().getTopInventory().getItem(20);
            ItemStack cursor = event.getCursor();
            boolean hasCursor = cursor != null && !cursor.getType().isAir();
            boolean hasCurrent = !isForgePlaceholder(current);
            if (!hasCursor && !hasCurrent) return;
            event.getView().getTopInventory().setItem(20, hasCursor ? cursor.clone() : null);
            player.setItemOnCursor(hasCurrent ? current : null);
            return;
        }
        if (slot == 24) {
            rollEnchant(player, event.getView().getTopInventory().getItem(20));
            return;
        }
        if (slot == 40) {
            ItemStack item = event.getView().getTopInventory().getItem(20);
            if (!isForgePlaceholder(item)) player.getInventory().addItem(item);
            event.getView().getTopInventory().setItem(20, null);
            open(player);
        }
    }

    @EventHandler
    public void onForgeClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof net.dark.threecore.gui.menu.CoreMenuHolder holder)) return;
        if (!holder.context().equalsIgnoreCase("souls-forge")) return;
        ItemStack item = event.getInventory().getItem(20);
        if (!isForgePlaceholder(item)) event.getPlayer().getInventory().addItem(item);
        event.getInventory().setItem(20, null);
    }

    private void rollEnchant(Player player, ItemStack item) {
        if (isForgePlaceholder(item)) {
            Text.send(player, "<red>Put an item in the forge slot first.</red>");
            return;
        }
        long cost = enchantCost();
        if (!take(player.getUniqueId(), cost)) {
            Text.send(player, "<red>You need " + cost + " souls.</red>");
            return;
        }
        EnchantRoll roll = randomEnchant();
        if (roll == null) {
            give(player.getUniqueId(), cost);
            Text.send(player, "<red>No soul enchants are configured yet.</red>");
            return;
        }
        item.addUnsafeEnchantment(roll.enchantment(), roll.level());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.mm("<gradient:#8b5cf6:#f3f4f6>Soul-forged:</gradient> <white>" + roll.display() + " " + roll.level() + "</white>"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "soul_forged"), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.1f);
        Text.send(player, "<green>Soul enchant applied:</green> <white>" + roll.display() + " " + roll.level() + "</white>");
    }

    private boolean isForgePlaceholder(ItemStack item) {
        return item == null || item.getType().isAir() || item.getType() == Material.PURPLE_STAINED_GLASS_PANE;
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

    @EventHandler
    public void onNpcClick(PlayerInteractEntityEvent event) {
        if (!config().getBoolean("npc.enabled", true)) return;
        if (!canUseForgeHere(event.getPlayer())) return;
        String name = event.getRightClicked().customName() == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getRightClicked().customName());
        String required = config().getString("npc.name", "Soul Forge");
        if (!name.equalsIgnoreCase(required)) return;
        event.setCancelled(true);
        open(event.getPlayer());
    }

    private boolean canUseForgeHere(Player player) {
        if (player.hasPermission("3smpcore.souls.admin")) return true;
        if (!config().getBoolean("npc.only-dungeon-spawn", true)) return true;
        Location spawn = dungeonSpawn();
        if (spawn == null || player.getWorld() == null || !player.getWorld().equals(spawn.getWorld())) return false;
        double radius = Math.max(1.0D, config().getDouble("npc.spawn-radius", 18.0D));
        return player.getLocation().distanceSquared(spawn) <= radius * radius;
    }

    private Location dungeonSpawn() {
        var sec = configs.get("dungeons/dungeons.yml").getConfigurationSection("spawn");
        if (sec == null) return null;
        var world = Bukkit.getWorld(sec.getString("world", configs.get("dungeons/dungeons.yml").getString("world", "dungeons")));
        return world == null ? null : new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
    }

    private EnchantRoll randomEnchant() {
        var section = config().getConfigurationSection("enchanting.enchants");
        List<EnchantRoll> rolls = new ArrayList<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String key = section.getString(id + ".minecraft", id);
                Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (enchant == null) continue;
                int min = section.getInt(id + ".min-level", 1);
                int max = Math.max(min, section.getInt(id + ".max-level", min));
                double weight = Math.max(0.01D, section.getDouble(id + ".weight", 1.0D));
                int level = ThreadLocalRandom.current().nextInt(min, max + 1);
                rolls.add(new EnchantRoll(enchant, level, section.getString(id + ".display", id), weight));
            }
        }
        if (rolls.isEmpty()) {
            Enchantment fallback = Enchantment.getByKey(NamespacedKey.minecraft("sharpness"));
            if (fallback == null) fallback = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (fallback == null) return null;
            rolls.add(new EnchantRoll(fallback, 1, "Sharpness", 1.0D));
        }
        double total = rolls.stream().mapToDouble(EnchantRoll::weight).sum();
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (EnchantRoll option : rolls) {
            roll -= option.weight();
            if (roll <= 0.0D) return option;
        }
        return rolls.getFirst();
    }

    private record EnchantRoll(Enchantment enchantment, int level, String display, double weight) {}
}
