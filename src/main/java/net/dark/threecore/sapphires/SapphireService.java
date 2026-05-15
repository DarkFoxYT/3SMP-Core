package net.dark.threecore.sapphires;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.sapphires.gui.SapphireMenu;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;

public final class SapphireService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MenuService menuService;

    public SapphireService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
        ensureDeluxeMenusVault();
    }

    public void reload() {
        ensureDeluxeMenusVault();
    }

    public long balance(UUID uuid) { return repository.getSapphireBalance(uuid); }
    public void set(UUID uuid, long amount) { repository.setSapphireBalance(uuid, amount); }
    public void give(UUID uuid, long amount) { set(uuid, balance(uuid) + Math.max(0L, amount)); }
    public boolean take(UUID uuid, long amount) { long current = balance(uuid); if (amount < 0L || current < amount) return false; set(uuid, current - amount); return true; }
    public void reset(UUID uuid) { set(uuid, 0L); }
    public long parseAmountInput(String input) { return parseLong(input); }
    public List<String> commandIds() {
        var section = configs.get("economy/sapphires.yml").getConfigurationSection("sapphire.commands");
        if (section == null) return List.of();
        return List.copyOf(section.getKeys(false));
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) openShop(player); else Text.send(sender, "<gray>Use /sapphire shop, /sapphire bal, /sapphire ballance, /sapphire give, /sapphire remove, /sapphire take, /sapphire set, /sapphire reset, or /sapphire commands.</gray>");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "shop", "vault", "menu" -> { if (sender instanceof Player player) openShop(player); else Text.send(sender, "<gray>Players only.</gray>"); }
            case "balance", "bal", "ballance" -> { if (sender instanceof Player player) sendBalance(player); else Text.send(sender, "<red>Players only.</red>"); }
            case "buy", "purchase" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<gray>Players only.</gray>"); return; }
                if (args.length < 2) { Text.send(sender, "<yellow>Use /sapphire buy <item>.</yellow>"); return; }
                purchase(player, args[1]);
            }
            case "deluxe", "dm" -> {
                if (sender instanceof Player player) handleDeluxeAction(player, args);
                else Text.send(sender, "<gray>Players only.</gray>");
            }
            case "givearmor", "prophecyarmor", "armor" -> {
                if (!sender.hasPermission("3smpcore.sapphires.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
                if (args.length < 2) { Text.send(sender, "<red>Usage: /sapphire " + sub + " <player></red>"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { Text.send(sender, "<red>That player must be online to receive ItemsAdder armor.</red>"); return; }
                giveProphecyArmor(target);
                if (sender instanceof Player) Text.send(sender, "<green>Gave Prophecy Armor to " + target.getName() + ".</green>");
            }
            case "give", "add", "grant", "remove", "take", "set", "reset" -> {
                if (!sender.hasPermission("3smpcore.sapphires.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
                if (args.length < 2) { Text.send(sender, "<red>Usage: /sapphire " + sub + " <player> [amount]</red>"); return; }
                DeliveryArgs delivery = parseDeliveryArgs(sub, args);
                String targetName = delivery.targetName();
                long amount = delivery.amount();
                OfflinePlayer target = targetPlayer(targetName);
                if (requiresAmount(sub) && amount <= 0L) {
                    Text.send(sender, "<red>Amount must be greater than 0.</red>");
                    return;
                }
                SapphireChange change = applyChange(sub, target.getUniqueId(), amount);
                logDelivery(sender, displayName(target, targetName), change);
                Text.send(sender, "<green>Updated sapphires for " + displayName(target, targetName) + ".</green> <gray>" + change.before() + " -> " + change.after() + "</gray>");
                if (target.isOnline() && target.getPlayer() != null) {
                    Text.send(target.getPlayer(), "<gradient:#f4cd2a:#eda323:#d28d0d>Sapphire balance updated.</gradient> <gray>New balance:</gray> <white>" + balance(target.getUniqueId()) + "</white>");
                }
            }
            case "info" -> sendCommandHelp(sender);
            case "commands" -> Text.send(sender, "<gray>Configured sapphire commands: " + String.join(", ", commandIds()) + "</gray>");
            default -> { if (sender instanceof Player player) openShop(player); else Text.send(sender, "<yellow>Use /sapphire shop|bal|give|remove|take|set|reset|commands</yellow>"); }
        }
    }

    public void openShop(Player player) {
        if (openDeluxeVault(player)) return;
        openPluginShop(player);
    }
    public void openPluginShop(Player player) { menuService.open(player, new SapphireMenu(this).buildEntry(player)); }
    public void openVault(Player player) { menuService.open(player, new SapphireMenu(this).buildVault(player)); }
    public void openSummary(Player player) { menuService.open(player, new SapphireMenu(this).buildSummary(player)); }

    public void handleMenuClick(Player player, String context, int slot) {
        if (context.equalsIgnoreCase("entry")) {
            if (SapphireMenu.isVaultOpenButton(slot)) openVault(player);
            return;
        }
        switch (slot) {
            case 4, 16 -> sendShopSummary(player);
            case 10 -> sendBalance(player);
            case 12 -> openShopLink(player);
            case 14 -> sendCommandHelp(player);
            case 28 -> purchase(player, "crate_keys");
            case 30 -> purchase(player, "gem_extractor");
            case 32 -> purchase(player, "gem_capsule");
            case 34 -> purchase(player, "cosmetics");
            case 38 -> purchase(player, "prophecy_armor");
            case 40 -> purchase(player, "donor_rank");
            default -> {
                String id = shopItemBySlot(slot);
                if (id != null) purchase(player, id);
            }
        }
    }

    public void sendShopSummary(Player player) {
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>3SMP Sapphire Summary</gradient>");
        Text.send(player, "<gray>Balance:</gray> <white>" + balance(player.getUniqueId()) + "</white>");
        Text.send(player, "<gray>Shop link:</gray> <white>" + configs.get("economy/sapphires.yml").getString("sapphire.shop-url", "https://example.com") + "</white>");
        Text.send(player, "<gray>Commands:</gray> <white>/sapphire bal, /sapphire ballance, /sapphire shop</white>");
    }
    public void sendBalance(Player player) { Text.send(player, "<gray>Your sapphires: <gradient:#f4cd2a:#eda323:#d28d0d>" + balance(player.getUniqueId()) + "</gradient></gray>"); }
    public void openShopInfo(Player player) { openShopLink(player); }
    public void sendCommandHelp(CommandSender sender) { Text.send(sender, "<gray>Sapphires are premium and non-tradeable. Use <white>/sapphire bal</white> or <white>/sapphire shop</white>.</gray>"); }
    public String shopUrl() { return configs.get("economy/sapphires.yml").getString("sapphire.shop-url", "https://example.com"); }
    public boolean purchase(Player player, String id) {
        var sec = configs.get("economy/sapphires.yml").getConfigurationSection("sapphire.shop-items." + id);
        if (sec == null) {
            Text.send(player, "<red>That sapphire item is not configured.</red>");
            return false;
        }
        long price = sec.getLong("price", 0L);
        String currency = sec.getString("currency", "sapphires");
        if (!takeCurrency(player.getUniqueId(), currency, price)) {
            Text.send(player, "<red>You cannot afford this purchase.</red>");
            return false;
        }
        String command = sec.getString("give-command", "");
        int amount = sec.getInt("amount", 1);
        if (!command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()).replace("{amount}", String.valueOf(amount)).replace("{uuid}", player.getUniqueId().toString()));
        }
        String label = sec.getString("display-name", id.replace('_', ' '));
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Purchase delivered:</gradient> <white>" + label + "</white>");
        player.closeInventory();
        return true;
    }

    public List<String> shopItemIds() {
        var section = configs.get("economy/sapphires.yml").getConfigurationSection("sapphire.shop-items");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    public String shopItemBySlot(int slot) {
        for (String id : shopItemIds()) {
            if (shopItemSlot(id) == slot) return id;
        }
        return null;
    }

    public String shopItemDisplayName(String id) {
        return configs.get("economy/sapphires.yml").getString("sapphire.shop-items." + id + ".display-name", id.replace('_', ' '));
    }

    public long shopItemPrice(String id) {
        return configs.get("economy/sapphires.yml").getLong("sapphire.shop-items." + id + ".price", 0L);
    }

    public String shopItemCurrency(String id) {
        return configs.get("economy/sapphires.yml").getString("sapphire.shop-items." + id + ".currency", "sapphires");
    }

    public Material shopItemMaterial(String id) {
        String raw = configs.get("economy/sapphires.yml").getString("sapphire.shop-items." + id + ".material", defaultMaterial(id).name());
        Material material = Material.matchMaterial(raw == null ? "" : raw);
        return material == null ? defaultMaterial(id) : material;
    }

    public int shopItemSlot(String id) {
        return configs.get("economy/sapphires.yml").getInt("sapphire.shop-items." + id + ".slot", defaultSlot(id));
    }

    private Material defaultMaterial(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "crate_keys" -> Material.TRIPWIRE_HOOK;
            case "gem_extractor" -> Material.PRISMARINE_SHARD;
            case "gem_capsule" -> Material.ENDER_CHEST;
            case "cosmetics" -> Material.ENDER_EYE;
            case "prophecy_armor" -> Material.NETHERITE_CHESTPLATE;
            case "donor_rank" -> Material.NETHER_STAR;
            default -> Material.AMETHYST_SHARD;
        };
    }

    private int defaultSlot(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "crate_keys" -> 20;
            case "gem_extractor" -> 22;
            case "gem_capsule" -> 24;
            case "cosmetics" -> 29;
            case "prophecy_armor" -> 31;
            case "donor_rank" -> 33;
            default -> 40;
        };
    }

    private boolean openDeluxeVault(Player player) {
        var yaml = configs.get("menus/sapphires.yml");
        if (!yaml.getBoolean("menus.deluxemenus.enabled", true)) return false;
        if (Bukkit.getPluginManager().getPlugin("DeluxeMenus") == null) return false;
        String menu = yaml.getString("menus.deluxemenus.vault-menu", "sap_vault");
        if (menu == null || menu.isBlank()) return false;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open " + menu + " " + player.getName());
        return true;
    }

    private void handleDeluxeAction(Player player, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "menu";
        switch (action) {
            case "", "menu", "vault", "shop" -> openShop(player);
            case "plugin", "fallback" -> openPluginShop(player);
            case "balance", "bal", "ballance" -> sendBalance(player);
            case "summary", "info" -> sendShopSummary(player);
            case "commands", "help" -> sendCommandHelp(player);
            case "link", "store" -> openShopLink(player);
            case "buy", "purchase" -> {
                if (args.length < 3) Text.send(player, "<yellow>Use /sapphire deluxe buy <item>.</yellow>");
                else purchase(player, args[2]);
            }
            default -> openShop(player);
        }
    }

    private void giveProphecyArmor(Player player) {
        giveItem(player, itemsAdderItem("threesmp:prophecy_helmet", Material.NETHERITE_HELMET));
        giveItem(player, itemsAdderItem("threesmp:prophecy_chestplate", Material.NETHERITE_CHESTPLATE));
        giveItem(player, itemsAdderItem("threesmp:prophecy_leggings", Material.NETHERITE_LEGGINGS));
        giveItem(player, itemsAdderItem("threesmp:prophecy_boots", Material.NETHERITE_BOOTS));
        player.updateInventory();
    }

    private void giveItem(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private ItemStack itemsAdderItem(String id, Material fallback) {
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
        return new ItemStack(fallback);
    }

    public void handleSummaryClick(Player player, int slot) {
        if (slot == 22) openShop(player);
        else if (slot == 11) sendBalance(player);
        else if (slot == 13) openShopLink(player);
        else if (slot == 15) sendCommandHelp(player);
    }

    public void openShopLink(Player player) {
        String url = shopUrl();
        player.sendMessage(Component.text("Open the sapphire shop: ").append(Component.text(url).clickEvent(ClickEvent.openUrl(url))));
    }

    public void executeConfigured(String action, CommandSender sender, String targetName, long amount) {
        OfflinePlayer target = targetPlayer(targetName);
        if (requiresAmount(action) && amount <= 0L) {
            Text.send(sender, "<red>Amount must be greater than 0.</red>");
            return;
        }
        SapphireChange change = applyChange(action, target.getUniqueId(), amount);
        logDelivery(sender, displayName(target, targetName), change);
        Text.send(sender, "<green>Sapphire action executed for " + displayName(target, targetName) + ".</green> <gray>" + change.before() + " -> " + change.after() + "</gray>");
        if (target.isOnline() && target.getPlayer() != null) {
            Text.send(target.getPlayer(), "<gradient:#f4cd2a:#eda323:#d28d0d>Sapphire delivery confirmed.</gradient> <gray>Balance:</gray> <white>" + balance(target.getUniqueId()) + "</white>");
        }
    }

    private DeliveryArgs parseDeliveryArgs(String action, String[] args) {
        String targetName = "";
        long amount = 0L;
        for (int i = 1; i < args.length; i++) {
            String value = args[i] == null ? "" : args[i].trim();
            if (value.isBlank()) continue;
            long parsed = parseLong(value);
            if (parsed > 0L && amount <= 0L) {
                amount = parsed;
                continue;
            }
            if (targetName.isBlank()) targetName = value;
        }
        return new DeliveryArgs(targetName, amount);
    }

    private SapphireChange applyChange(String action, UUID target, long amount) {
        long before = balance(target);
        String canonical = canonicalAction(action);
        switch (canonical) {
            case "give" -> give(target, amount);
            case "take", "remove" -> take(target, amount);
            case "set" -> set(target, amount);
            case "reset" -> reset(target);
            default -> throw new IllegalArgumentException("Unknown sapphire action: " + action);
        }
        return new SapphireChange(canonical, amount, before, balance(target));
    }

    private String canonicalAction(String action) {
        String normalized = action == null ? "" : action.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "add", "grant" -> "give";
            default -> normalized;
        };
    }

    private boolean requiresAmount(String action) {
        return switch (canonicalAction(action)) {
            case "give", "take", "remove", "set" -> true;
            default -> false;
        };
    }

    private OfflinePlayer targetPlayer(String targetName) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) return online;
        for (OfflinePlayer cached : Bukkit.getOfflinePlayers()) {
            if (cached.getName() != null && cached.getName().equalsIgnoreCase(targetName)) return cached;
        }
        return Bukkit.getOfflinePlayer(targetName);
    }

    private void logDelivery(CommandSender sender, String targetName, SapphireChange change) {
        plugin.getLogger().info("[Sapphires] " + change.action() + " target=" + targetName + " amount=" + change.amount() + " before=" + change.before() + " after=" + change.after() + " sender=" + sender.getName());
    }

    private boolean takeCurrency(UUID uuid, String currency, long amount) {
        if (currency.equalsIgnoreCase("money")) {
            double current = repository.getMoneyBalance(uuid);
            if (current < amount) return false;
            repository.setMoneyBalance(uuid, current - amount);
            return true;
        }
        return take(uuid, amount);
    }

    private long parseLong(String input) {
        if (input == null) return 0L;
        String normalized = input.trim().replace(",", "").replace("_", "");
        if (normalized.isBlank()) return 0L;
        long multiplier = 1L;
        char suffix = Character.toLowerCase(normalized.charAt(normalized.length() - 1));
        if (suffix == 'k' || suffix == 'm') {
            multiplier = suffix == 'k' ? 1_000L : 1_000_000L;
            normalized = normalized.substring(0, normalized.length() - 1);
            if (normalized.isBlank()) return 0L;
        }
        try {
            return Math.multiplyExact(Long.parseLong(normalized), multiplier);
        } catch (NumberFormatException ignored) {
            try {
                double parsed = Double.parseDouble(normalized);
                if (!Double.isFinite(parsed) || parsed <= 0.0D) return 0L;
                double total = Math.floor(parsed * multiplier);
                return total > Long.MAX_VALUE ? Long.MAX_VALUE : (long) total;
            } catch (NumberFormatException | ArithmeticException ex) {
                return 0L;
            }
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }
    private String displayName(OfflinePlayer player, String fallback) { return player.getName() == null ? fallback : player.getName(); }

    private void ensureDeluxeMenusVault() {
        var yaml = configs.get("menus/sapphires.yml");
        if (!yaml.getBoolean("menus.deluxemenus.enabled", true)) return;
        if (!yaml.getBoolean("menus.deluxemenus.install-vault", true)) return;
        Path pluginRoot = plugin.getDataFolder().toPath().getParent();
        if (pluginRoot == null) return;
        String menu = yaml.getString("menus.deluxemenus.vault-menu", "sap_vault");
        if (menu == null || menu.isBlank()) menu = "sap_vault";
        Path deluxeRoot = pluginRoot.resolve("DeluxeMenus");
        Path menusDir = deluxeRoot.resolve("gui_menus");
        try {
            Files.createDirectories(menusDir);
            writeIfMissing(menusDir.resolve(menu + ".yml"), deluxeVaultTemplate());
            writeIfMissing(menusDir.resolve(menu + "_shop.yml"), deluxeVaultShopTemplate());
            ensureDeluxeMenuRegistered(deluxeRoot.resolve("config.yml"), menu);
            ensureDeluxeMenuRegistered(deluxeRoot.resolve("config.yml"), menu + "_shop");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not install Sapphire Vault DeluxeMenus file: " + ex.getMessage());
        }
    }

    private void writeIfMissing(Path path, String content) throws IOException {
        if (Files.exists(path)) return;
        Files.writeString(path, content, StandardCharsets.UTF_8);
        plugin.getLogger().info("Installed DeluxeMenus starter: " + path.getFileName());
    }

    private void ensureDeluxeMenuRegistered(Path config, String menu) throws IOException {
        if (!Files.exists(config)) return;
        String content = Files.readString(config, StandardCharsets.UTF_8);
        String key = "  " + menu + ":";
        if (content.contains("\n" + key) || content.startsWith(key)) return;
        int menusIndex = content.indexOf("gui_menus:");
        if (menusIndex < 0) return;
        int insertAt = content.indexOf('\n', menusIndex);
        if (insertAt < 0) insertAt = content.length();
        String entry = "\n  " + menu + ":\n    file: " + menu + ".yml";
        Files.writeString(config, content.substring(0, insertAt) + entry + content.substring(insertAt), StandardCharsets.UTF_8);
    }

    private String deluxeVaultTemplate() {
        return """
                # Generated by 3SMPCore. Safe to edit; it will not be overwritten while this file exists.
                menu_title: ":offset_-8::sap_vault:"
                open_command: []
                size: 54
                update_interval: 20
                items:
                  open_center:
                    material: PAPER
                    model_data: 1
                    slots:
                      - 13
                      - 21
                      - 22
                      - 23
                      - 29
                      - 30
                      - 31
                      - 32
                      - 33
                      - 39
                      - 40
                      - 41
                    display_name: "&r"
                    lore: []
                    left_click_commands:
                      - "[openguimenu] sap_vault_shop"
                    right_click_commands:
                      - "[openguimenu] sap_vault_shop"
                """;
    }

    private String deluxeVaultShopTemplate() {
        return """
                # Generated by 3SMPCore. Safe to edit; it will not be overwritten while this file exists.
                menu_title: ":offset_-8::sap_vault:"
                open_command: []
                size: 54
                update_interval: 20
                items:
                  balance:
                    material: EMERALD
                    slot: 13
                    display_name: "&#f4cd2a&lSapphire Balance"
                    lore:
                      - "&7Current balance: &f%3smpcore_sapphires%"
                      - "&8Your vault is open."
                  crate_keys:
                    material: TRIPWIRE_HOOK
                    slot: 20
                    display_name: "&#eda323&lProphecy Crate Key"
                    lore:
                      - "&7Price: &f100 sapphires"
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy crate_keys"
                  gem_extractor:
                    material: PRISMARINE_SHARD
                    slot: 22
                    display_name: "&#eda323&lGem Extractor"
                    lore:
                      - "&7Price: &f250 sapphires"
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy gem_extractor"
                  gem_capsule:
                    material: ENDER_CHEST
                    slot: 24
                    display_name: "&#eda323&lRough Gem Capsule"
                    lore:
                      - "&7Price: &f400 sapphires"
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy gem_capsule"
                  cosmetics:
                    material: ENDER_EYE
                    slot: 29
                    display_name: "&#eda323&lCosmetic Unlock"
                    lore:
                      - "&7Price: &f500 sapphires"
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy cosmetics"
                  prophecy_armor:
                    material: NETHERITE_CHESTPLATE
                    slot: 31
                    display_name: "&#f4cd2a&lProphecy Armor Set"
                    lore:
                      - "&7Full ItemsAdder armor set."
                      - "&7Price: &f1500 sapphires"
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy prophecy_armor"
                  donor_rank:
                    material: NETHER_STAR
                    slot: 33
                    display_name: "&#eda323&lVIP Rank"
                    lore:
                      - "&7Price: &f2000 sapphires"
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy donor_rank"
                  close:
                    material: BARRIER
                    slot: 53
                    display_name: "&cClose"
                    left_click_commands:
                      - "[close]"
                """;
    }

    private record SapphireChange(String action, long amount, long before, long after) {}
    private record DeliveryArgs(String targetName, long amount) {}
}


