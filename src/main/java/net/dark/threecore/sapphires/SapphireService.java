package net.dark.threecore.sapphires;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.perks.PerkService;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
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
    private final PerkService perkService;

    public SapphireService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService, PerkService perkService) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
        this.perkService = perkService;
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
            if (sender instanceof Player player) openShop(player); else Text.send(sender, "<gray>Use /sapphire shop, /sapphire bal, /sapphire balance, /sapphire give, /sapphire remove, /sapphire take, /sapphire set, /sapphire reset, or /sapphire commands.</gray>");
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
        if (!openDeluxeVault(player)) openVault(player);
    }
    public void openPluginShop(Player player) { menuService.open(player, new SapphireMenu(this).buildEntry(player)); }
    public void openVault(Player player) { openVault(player, 0); }
    public void openVault(Player player, int page) { menuService.open(player, new SapphireMenu(this).buildVault(player, page)); }
    public void openSummary(Player player) { menuService.open(player, new SapphireMenu(this).buildSummary(player)); }

    public void handleMenuClick(Player player, String context, int slot) {
        if (context.equalsIgnoreCase("entry")) {
            if (SapphireMenu.isVaultOpenButton(slot)) openVault(player);
            return;
        }
        if (context.toLowerCase(Locale.ROOT).startsWith("vault")) {
            int page = pageFromContext(context);
            if (slot == sapphirePreviousSlot() && page > 0) {
                openVault(player, page - 1);
                return;
            }
            if (slot == sapphireNextSlot() && page + 1 < sapphirePageCount()) {
                openVault(player, page + 1);
                return;
            }
            if (slot == sapphireStoreSlot()) {
                openShopLink(player);
                return;
            }
            if (slot == sapphireBalanceSlot()) {
                sendBalance(player);
                return;
            }
            String configured = shopItemByPagedSlot(slot, page);
            if (configured != null) {
                purchase(player, configured);
                return;
            }
        }
        switch (slot) {
            case 4, 16 -> sendShopSummary(player);
            case 10 -> sendBalance(player);
            case 12, 49 -> openShopLink(player);
            case 14 -> sendCommandHelp(player);
            default -> {
            }
        }
    }

    public void sendShopSummary(Player player) {
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>3SMP Sapphire Summary</gradient>");
        Text.send(player, "<gray>Balance:</gray> <white>" + balance(player.getUniqueId()) + "</white>");
        Text.send(player, "<gray>Shop link:</gray> <white>" + configs.get("economy/sapphires.yml").getString("sapphire.shop-url", "https://example.com") + "</white>");
        Text.send(player, "<gray>Commands:</gray> <white>/sapphire bal, /sapphire balance, /sapphire shop</white>");
    }
    public void sendBalance(Player player) { Text.send(player, "<gray>Your sapphires: <gradient:#f4cd2a:#eda323:#d28d0d>" + balance(player.getUniqueId()) + "</gradient></gray>"); }
    public void openShopInfo(Player player) { openShopLink(player); }
    public void sendCommandHelp(CommandSender sender) { Text.send(sender, "<gray>Sapphires are premium and non-tradeable. Use <white>/sapphire bal</white> or <white>/sapphire shop</white>.</gray>"); }
    public String shopUrl() { return configs.get("economy/sapphires.yml").getString("sapphire.shop-url", "https://example.com"); }

    public String menuTitle(String key, String fallback) {
        String raw = configs.get("menus/sapphires.yml").getString("titles." + key, fallback);
        if (raw == null || raw.isBlank()) raw = fallback;
        return replaceGuiSymbols(raw);
    }

    private String replaceGuiSymbols(String input) {
        String output = applyItemsAdderFontImages(input);
        var symbols = configs.get("menus/sapphires.yml").getConfigurationSection("menus.itemsadder-font-symbols");
        if (symbols != null) {
            for (String key : symbols.getKeys(false)) {
                output = output.replace(":" + key + ":", decodeUnicodeEscapes(symbols.getString(key, "")));
            }
        }
        return decodeUnicodeEscapes(output);
    }

    private String applyItemsAdderFontImages(String input) {
        if (input == null || input.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return input == null ? "" : input;
        try {
            Class<?> wrapper = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            Method replace = wrapper.getMethod("replaceFontImages", String.class);
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

    public boolean purchase(Player player, String id) {
        var sec = configs.get("economy/sapphires.yml").getConfigurationSection("sapphire.shop-items." + id);
        if (sec == null) {
            Text.send(player, "<red>That sapphire item is not configured.</red>");
            return false;
        }
        if (openCosmeticUnlockMenu(player, id)) return true;
        long price = sec.getLong("price", 0L);
        if (price <= 0L) {
            Text.send(player, "<yellow>That Sapphire item is coming soon.</yellow>");
            return false;
        }
        String currency = sec.getString("currency", "sapphires");
        if (!takeCurrency(player.getUniqueId(), currency, price)) {
            Text.send(player, "<red>You cannot afford this purchase.</red>");
            return false;
        }
        String command = sec.getString("give-command", "");
        int amount = sec.getInt("amount", 1);
        if (id.equalsIgnoreCase("third_life")) {
            giveThirdLife(player, amount);
        } else if (!command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()).replace("{amount}", String.valueOf(amount)).replace("{uuid}", player.getUniqueId().toString()));
        }
        String label = sec.getString("display-name", id.replace('_', ' '));
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Purchase delivered:</gradient> <white>" + label + "</white>");
        player.closeInventory();
        return true;
    }

    private boolean openCosmeticUnlockMenu(Player player, String id) {
        if (id.equalsIgnoreCase("cosmetics")) {
            if (perkService != null) perkService.openCategory(player, "colors");
            Text.send(player, "<gray>Choose a locked chat color to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("kill_effect")) {
            if (perkService != null) perkService.openCategory(player, "kill_effects");
            Text.send(player, "<gray>Choose a locked kill effect to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("badge")) {
            if (perkService != null) perkService.openCategory(player, "badges");
            Text.send(player, "<gray>Choose a locked badge to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("join_quit_message")) {
            if (perkService != null) perkService.openCategory(player, "join_quit_messages");
            Text.send(player, "<gray>Choose a locked join or quit message to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("cosmetic")) {
            if (perkService != null) perkService.openCategory(player, "cosmetics");
            Text.send(player, "<gray>Choose a locked cosmetic to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("weapon_cosmetic")) {
            if (perkService != null) perkService.openCategory(player, "weapon_cosmetics");
            Text.send(player, "<gray>Choose a locked weapon cosmetic to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("tag")) {
            if (perkService != null) perkService.openCategory(player, "tags");
            Text.send(player, "<gray>Choose a locked tag to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("particle")) {
            if (perkService != null) perkService.openCategory(player, "particles");
            Text.send(player, "<gray>Choose a locked particle cosmetic to unlock it with Sapphires.</gray>");
            return true;
        }
        if (id.equalsIgnoreCase("name_color") || id.equalsIgnoreCase("name_gradient")) {
            player.performCommand("visuals");
            Text.send(player, "<gray>Choose the visual you want to unlock.</gray>");
            return true;
        }
        return false;
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

    public String shopItemByPagedSlot(int slot, int page) {
        if (usesConfiguredSapphirePages()) {
            int configuredPage = Math.max(0, page) + 1;
            for (String id : shopItemIds()) {
                if (shopItemPage(id) == configuredPage && shopItemSlot(id) == slot) return id;
            }
            return null;
        }
        List<Integer> slots = sapphireItemSlots();
        int slotIndex = slots.indexOf(slot);
        if (slotIndex < 0) return null;
        int itemIndex = Math.max(0, page) * slots.size() + slotIndex;
        List<String> ids = shopItemIds();
        return itemIndex >= 0 && itemIndex < ids.size() ? ids.get(itemIndex) : null;
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

    public ItemStack sapphireIconItem() {
        return itemsAdderItem(configs.get("economy/sapphires.yml").getString("sapphire.icon-itemsadder", "threesmp:sapphire"), Material.AMETHYST_SHARD);
    }

    public ItemStack guiClickzoneItem() {
        return itemsAdderItem("threesmp:gui_clickzone", Material.PAPER);
    }

    public ItemStack shopItemIcon(String id) {
        String raw = configs.get("economy/sapphires.yml").getString("sapphire.shop-items." + id + ".itemsadder", configs.get("economy/sapphires.yml").getString("sapphire.shop-items." + id + ".itemsadder-id", ""));
        return raw == null || raw.isBlank() ? new ItemStack(shopItemMaterial(id)) : itemsAdderItem(raw, shopItemMaterial(id));
    }

    public List<Integer> sapphireItemSlots() {
        List<Integer> configured = configs.get("menus/sapphires.yml").getIntegerList("shop.item-slots");
        if (configured.isEmpty()) configured = configs.get("menus/sapphires.yml").getIntegerList("item-slots");
        if (configured.isEmpty()) return List.of(18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44);
        List<Integer> out = new ArrayList<>();
        for (int slot : configured) {
            if (slot >= 0 && slot < 54 && !out.contains(slot)) out.add(slot);
        }
        return out.isEmpty() ? List.of(18, 20, 22, 24, 26, 28, 30, 32, 34, 36, 38, 40, 42, 44) : out;
    }

    public int sapphireBalanceSlot() { return configs.get("menus/sapphires.yml").getInt("shop.balance-slot", 13); }
    public int sapphireStoreSlot() { return configs.get("menus/sapphires.yml").getInt("shop.store-slot", 49); }
    public int sapphirePreviousSlot() { return configs.get("menus/sapphires.yml").getInt("shop.previous-slot", 45); }
    public int sapphireNextSlot() { return configs.get("menus/sapphires.yml").getInt("shop.next-slot", 53); }

    public int sapphirePageCount() {
        if (usesConfiguredSapphirePages()) {
            int max = 1;
            for (String id : shopItemIds()) max = Math.max(max, shopItemPage(id));
            return max;
        }
        int pageSize = Math.max(1, sapphireItemSlots().size());
        int total = shopItemIds().size();
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    public int normalizeSapphirePage(int page) {
        return Math.max(0, Math.min(page, sapphirePageCount() - 1));
    }

    public int shopItemSlot(String id) {
        return configs.get("economy/sapphires.yml").getInt("sapphire.shop-items." + id + ".slot", defaultSlot(id));
    }

    public int shopItemPage(String id) {
        return Math.max(1, configs.get("economy/sapphires.yml").getInt("sapphire.shop-items." + id + ".page", 1));
    }

    public boolean usesConfiguredSapphirePages() {
        var items = configs.get("economy/sapphires.yml").getConfigurationSection("sapphire.shop-items");
        if (items == null) return false;
        for (String id : items.getKeys(false)) {
            if (items.isSet(id + ".page")) return true;
        }
        return false;
    }

    private int pageFromContext(String context) {
        String[] parts = context.split(":");
        if (parts.length < 2) return 0;
        try {
            return normalizeSapphirePage(Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Material defaultMaterial(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "crate_keys" -> Material.TRIPWIRE_HOOK;
            case "gem_extractor" -> Material.PRISMARINE_SHARD;
            case "gem_capsule" -> Material.ENDER_CHEST;
            case "cosmetics" -> Material.ENDER_EYE;
            case "kill_effect" -> Material.ENCHANTED_BOOK;
            case "name_color" -> Material.NAME_TAG;
            case "name_gradient" -> Material.AMETHYST_SHARD;
            case "prophecy_armor" -> Material.NETHERITE_CHESTPLATE;
            case "third_life" -> Material.TOTEM_OF_UNDYING;
            default -> Material.AMETHYST_SHARD;
        };
    }

    private int defaultSlot(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "crate_keys" -> 20;
            case "gem_extractor" -> 22;
            case "gem_capsule" -> 24;
            case "cosmetics" -> 29;
            case "kill_effect" -> 30;
            case "name_color" -> 32;
            case "name_gradient" -> 33;
            case "prophecy_armor" -> 38;
            case "third_life" -> 44;
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

    private void giveThirdLife(Player player, int amount) {
        int count = Math.max(1, amount);
        for (int i = 0; i < count; i++) giveItem(player, thirdLifeItem());
        player.updateInventory();
    }

    private ItemStack thirdLifeItem() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "third_life_lives"), PersistentDataType.INTEGER, 3);
        meta.displayName(Text.mm("<gradient:#ff7a18:#ffd166><bold>The Third Life</bold></gradient>"));
        meta.lore(List.of(
                Text.mm("<gray>The prophecy does not let its chosen fall.</gray>"),
                Text.mm("<gray>Not once. Not twice. Not three times.</gray>"),
                Text.mm(""),
                Text.mm("<yellow>Lives Remaining: 3</yellow>"),
                Text.mm(""),
                Text.mm("<dark_gray>Hold in either hand while dying.</dark_gray>"),
                Text.mm("<dark_gray>Returns with one fewer life.</dark_gray>")
        ));
        meta.setMaxStackSize(1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
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
            installDeluxeMenu(menusDir, menu, "menus/deluxemenus/sap_vault.yml", deluxeVaultTemplate());
            installDeluxeMenu(menusDir, menu + "_shop", "menus/deluxemenus/sap_vault_shop.yml", deluxeVaultShopTemplate());
            installDeluxeMenu(menusDir, menu + "_shop_2", "menus/deluxemenus/sap_vault_shop_2.yml", deluxeVaultShopTemplate());
            installDeluxeMenu(menusDir, menu + "_shop_3", "menus/deluxemenus/sap_vault_shop_3.yml", deluxeVaultShopTemplate());
            installDeluxeMenu(menusDir, menu + "_shop_4", "menus/deluxemenus/sap_vault_shop_4.yml", deluxeVaultShopTemplate());
            Path config = deluxeRoot.resolve("config.yml");
            ensureDeluxeMenuRegistered(config, menu);
            ensureDeluxeMenuRegistered(config, menu + "_shop");
            ensureDeluxeMenuRegistered(config, menu + "_shop_2");
            ensureDeluxeMenuRegistered(config, menu + "_shop_3");
            ensureDeluxeMenuRegistered(config, menu + "_shop_4");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not install Sapphire Vault DeluxeMenus file: " + ex.getMessage());
        }
    }

    private void installDeluxeMenu(Path menusDir, String menu, String resourcePath, String fallback) throws IOException {
        Path target = menusDir.resolve(menu + ".yml");
        if (Files.exists(target)) return;
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream != null) Files.copy(stream, target);
            else Files.writeString(target, fallback, StandardCharsets.UTF_8);
        }
        plugin.getLogger().info("Installed DeluxeMenus starter: " + target.getFileName());
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
                menu_title: ":offset_-15:&f:sap_vault:"
                open_command: []
                size: 54
                update_interval: 20
                items:
                  open_center:
                    material: PAPER
                    model_data: 1
                    slots:
                      - 13
                      - 20
                      - 21
                      - 22
                      - 23
                      - 24
                      - 29
                      - 30
                      - 31
                      - 32
                      - 33
                      - 34
                      - 35
                      - 36
                      - 37
                      - 38
                      - 39
                      - 40
                      - 41
                      - 42
                      - 43
                      - 44
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
                menu_title: ":offset_-15:&f:sap_vault:"
                open_command: []
                size: 54
                update_interval: 20
                items:
                  balance:
                    material: "itemsadder-threesmp:sapphire"
                    slot: 13
                    display_name: "&#f4cd2a&lSapphire &#eda323&lBalance"
                    lore:
                      - "&7Current balance: &#f4cd2a%3smpcore_sapphires% &fSapphires"
                      - "&8Premium vault ready."
                  crate_keys:
                    material: TRIPWIRE_HOOK
                    slot: 20
                    display_name: "&#f4cd2a&lProphecy &#eda323&lCrate &#d28d0d&lKey"
                    lore:
                      - "&#f4cd2aPrice: &f500 Sapphires"
                      - "&7Opens the Prophecy Crate."
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy crate_keys"
                  gem_extractor:
                    material: PRISMARINE_SHARD
                    slot: 22
                    display_name: "&#38bdf8&lGem &#22d3ee&lExtractor"
                    lore:
                      - "&#f4cd2aPrice: &f2,500 Sapphires"
                      - "&7Extracts gems from gear."
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy gem_extractor"
                  gem_capsule:
                    material: ENDER_CHEST
                    slot: 24
                    display_name: "&#22c55e&lRough &#84cc16&lGem &#f4cd2a&lCapsule"
                    lore:
                      - "&#f4cd2aPrice: &f250 Sapphires"
                      - "&7Contains a rough gem roll."
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy gem_capsule"
                  cosmetics:
                    material: ENDER_EYE
                    slot: 29
                    display_name: "&#c084fc&lChat &#f472b6&lColor"
                    lore:
                      - "&#f4cd2aPrice: &f350 Sapphires"
                      - "&8Pick a locked chat color to unlock."
                    left_click_commands:
                      - "[player] sapphire deluxe buy cosmetics"
                  kill_effect:
                    material: ENCHANTED_BOOK
                    slot: 30
                    display_name: "&#ef4444&lKill &#f97316&lEffect"
                    lore:
                      - "&#f4cd2aPrice: &f100 Sapphires"
                      - "&8Pick a locked kill effect to unlock."
                    left_click_commands:
                      - "[player] sapphire deluxe buy kill_effect"
                  badge:
                    material: NETHER_STAR
                    slot: 31
                    display_name: "&#f4cd2a&lBadge &#ffffff&lUnlock"
                    lore:
                      - "&#f4cd2aPrice: &f350 Sapphires"
                      - "&8Pick a locked badge to unlock."
                    left_click_commands:
                      - "[player] sapphire deluxe buy badge"
                  name_color:
                    material: NAME_TAG
                    slot: 32
                    display_name: "&#38bdf8&lName &#f4cd2a&lColor"
                    lore:
                      - "&#f4cd2aPrice: &f350 Sapphires"
                      - "&8Pick a locked name color to unlock."
                    left_click_commands:
                      - "[player] sapphire deluxe buy name_color"
                  name_gradient:
                    material: AMETHYST_SHARD
                    slot: 33
                    display_name: "&#f4cd2a&lName &#eda323&lGradient"
                    lore:
                      - "&#f4cd2aPrice: &f350 Sapphires"
                      - "&8Pick a locked name gradient to unlock."
                    left_click_commands:
                      - "[player] sapphire deluxe buy name_gradient"
                  join_quit_message:
                    material: WRITABLE_BOOK
                    slot: 34
                    display_name: "&#60a5fa&lJoin/Quit &#c084fc&lMessage"
                    lore:
                      - "&#f4cd2aPrice: &f350 Sapphires"
                      - "&8Pick a locked join or quit style."
                    left_click_commands:
                      - "[player] sapphire deluxe buy join_quit_message"
                  cosmetic:
                    material: ENDER_EYE
                    slot: 35
                    display_name: "&#c084fc&lCosmetic &#f4cd2a&lUnlock"
                    lore:
                      - "&#f4cd2aPrice: &f500 Sapphires"
                      - "&8Pick a locked cosmetic to unlock."
                    left_click_commands:
                      - "[player] sapphire deluxe buy cosmetic"
                  weapon_cosmetic:
                    material: TRIDENT
                    slot: 36
                    display_name: "&#ef4444&lWeapon &#f4cd2a&lCosmetic"
                    lore:
                      - "&#f4cd2aPrice: &f500 Sapphires"
                      - "&8Pick a locked weapon cosmetic."
                    left_click_commands:
                      - "[player] sapphire deluxe buy weapon_cosmetic"
                  tag:
                    material: PAPER
                    slot: 37
                    display_name: "&#f4cd2a&lTag &#ffffff&lUnlock"
                    lore:
                      - "&#f4cd2aPrice: &f300 Sapphires"
                      - "&8Pick a locked chat tag."
                    left_click_commands:
                      - "[player] sapphire deluxe buy tag"
                  prophecy_armor:
                    material: NETHERITE_CHESTPLATE
                    slot: 38
                    display_name: "&#f4cd2a&lProphecy &#7c3aed&lArmor &#ffffff&lSet"
                    lore:
                      - "&7Full ItemsAdder armor set."
                      - "&7Limited time while available."
                      - "&#f4cd2aPrice: &f5,000 Sapphires"
                      - "&8Click to purchase."
                    left_click_commands:
                      - "[player] sapphire deluxe buy prophecy_armor"
                  particle:
                    material: FIREWORK_ROCKET
                    slot: 43
                    display_name: "&#38bdf8&lParticle &#f4cd2a&lUnlock"
                    lore:
                      - "&#f4cd2aPrice: &f250 Sapphires"
                      - "&8Pick a locked particle to unlock."
                    left_click_commands:
                      - "[player] sapphire deluxe buy particle"
                  third_life:
                    material: TOTEM_OF_UNDYING
                    slot: 44
                    display_name: "&#ef4444&lThird &#f4cd2a&lLife"
                    lore:
                      - "&#f4cd2aPrice: &f500 Sapphires"
                      - "&8A three-use life-saving totem."
                    left_click_commands:
                      - "[player] sapphire deluxe buy third_life"
                """;
    }

    private record SapphireChange(String action, long amount, long before, long after) {}
    private record DeliveryArgs(String targetName, long amount) {}
}


