package net.dark.threecore.shop;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShopService implements Listener {
    private static final int[] ITEM_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MoneyService moneyService;
    private List<ShopCategory> cachedCategories = List.of();
    private final Map<String, List<ShopItem>> cachedItems = new LinkedHashMap<>();

    public ShopService(JavaPlugin plugin, ConfigFiles configs, MoneyService moneyService) {
        this.plugin = plugin;
        this.configs = configs;
        this.moneyService = moneyService;
        reload();
    }

    public void reload() {
        cachedCategories = loadCategories();
        cachedItems.clear();
        for (ShopCategory category : cachedCategories) cachedItems.put(category.id(), loadItems(category.id()));
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (args.length == 0) { openCategories(player); return; }
        openCategory(player, args[0].toLowerCase(Locale.ROOT), 0);
    }

    public List<String> complete(String[] args) { return args.length <= 1 ? categories().stream().map(ShopCategory::id).toList() : List.of(); }

    public void openCategories(Player player) {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "shop:categories"), 54, configs.get("economy/shop.yml").getString("menu.title", "3SMP Shop"));
        fill(inv);
        inv.setItem(4, button(Material.EMERALD, configs.get("economy/shop.yml").getString("menu.header", "<gradient:#f4cd2a:#eda323:#d28d0d>3SMP Shop</gradient>"), List.of("<gray>Buy blocks, items, food, tools, and more.</gray>", "<gray>Balance:</gray> <white>" + moneyService.format(moneyService.balance(player.getUniqueId())) + "</white>")));
        int[] slots = {10,12,14,16,28,30,32,34};
        List<ShopCategory> categories = categories();
        for (int i = 0; i < categories.size() && i < slots.length; i++) {
            ShopCategory category = categories.get(i);
            inv.setItem(slots[i], button(category.icon(), category.name(), List.of("<gray>Click to browse.</gray>", "<gray>Items:</gray> <white>" + items(category.id()).size() + "</white>")));
        }
        player.openInventory(inv);
    }

    public void openCategory(Player player, String categoryId, int page) {
        ShopCategory category = category(categoryId);
        if (category == null) { Text.send(player, "<red>Unknown shop category.</red>"); openCategories(player); return; }
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "shop:" + category.id() + ":" + Math.max(0, page)), 54, "Shop: " + plain(category.name()));
        fill(inv);
        inv.setItem(4, button(category.icon(), category.name(), List.of("<gray>Balance:</gray> <white>" + moneyService.format(moneyService.balance(player.getUniqueId())) + "</white>")));
        List<ShopItem> items = items(category.id());
        int start = page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            inv.setItem(ITEM_SLOTS[i], button(item.material(), item.name(), List.of("<gray>Price:</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + moneyService.format(item.price()) + "</gradient>", "<gray>Amount:</gray> <white>" + item.amount() + "</white>", "<green>Click to buy.</green>")));
        }
        if (page > 0) inv.setItem(45, button(Material.ARROW, "<gray>Previous Page</gray>", List.of()));
        inv.setItem(49, button(Material.BARRIER, "<red>Back</red>", List.of("<gray>Return to categories.</gray>")));
        if (start + ITEM_SLOTS.length < items.size()) inv.setItem(53, button(Material.ARROW, "<gray>Next Page</gray>", List.of()));
        player.openInventory(inv);
    }

    public void handleClick(Player player, String context, int slot) {
        if (context.equalsIgnoreCase("shop:categories")) {
            int[] slots = {10,12,14,16,28,30,32,34};
            List<ShopCategory> categories = categories();
            for (int i = 0; i < slots.length && i < categories.size(); i++) if (slot == slots[i]) openCategory(player, categories.get(i).id(), 0);
            return;
        }
        if (!context.startsWith("shop:")) return;
        String[] parts = context.split(":");
        if (parts.length < 3) return;
        String category = parts[1];
        int page = parseInt(parts[2]);
        if (slot == 49) { openCategories(player); return; }
        if (slot == 45 && page > 0) { openCategory(player, category, page - 1); return; }
        if (slot == 53) { openCategory(player, category, page + 1); return; }
        int index = slotIndex(slot);
        if (index < 0) return;
        List<ShopItem> items = items(category);
        int itemIndex = page * ITEM_SLOTS.length + index;
        if (itemIndex >= items.size()) return;
        buy(player, items.get(itemIndex));
        openCategory(player, category, page);
    }

    private void buy(Player player, ShopItem item) {
        ItemStack purchase = new ItemStack(item.material(), item.amount());
        if (!canFit(player, purchase)) {
            Text.send(player, "<red>You need more inventory space.</red>");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        if (!moneyService.take(player.getUniqueId(), item.price())) { Text.send(player, "<red>You need " + moneyService.format(item.price()) + ".</red>"); return; }
        player.getInventory().addItem(purchase);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.35f);
        Text.actionBar(player, "<green>Bought</green> <white>" + item.amount() + "x " + item.material().name().toLowerCase(Locale.ROOT) + "</white> <gray>for</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + moneyService.format(item.price()) + "</gradient>");
    }

    private List<ShopCategory> categories() {
        return cachedCategories;
    }

    private ShopCategory category(String id) { return categories().stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst().orElse(null); }

    private List<ShopItem> items(String category) {
        return cachedItems.getOrDefault(category.toLowerCase(Locale.ROOT), List.of());
    }

    private List<ShopCategory> loadCategories() {
        ConfigurationSection section = configs.get("economy/shop.yml").getConfigurationSection("categories");
        if (section == null) return List.of();
        List<ShopCategory> out = new ArrayList<>();
        for (String id : section.getKeys(false)) out.add(new ShopCategory(id.toLowerCase(Locale.ROOT), section.getString(id + ".display-name", id), material(section.getString(id + ".icon", "CHEST"))));
        out.sort(Comparator.comparing(ShopCategory::id));
        return out;
    }

    private List<ShopItem> loadItems(String category) {
        ConfigurationSection section = configs.get("economy/shop.yml").getConfigurationSection("categories." + category + ".items");
        if (section == null) return List.of();
        List<ShopItem> out = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            Material material = material(section.getString(id + ".material", id));
            int amount = Math.max(1, section.getInt(id + ".amount", 1));
            double price = Math.max(0.0D, section.getDouble(id + ".price", 1.0D));
            String name = section.getString(id + ".display-name", "<white>" + material.name() + "</white>");
            out.add(new ShopItem(id, material, amount, price, name));
        }
        return out;
    }

    private boolean canFit(Player player, ItemStack purchase) {
        int remaining = purchase.getAmount();
        int max = purchase.getMaxStackSize();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                remaining -= max;
            } else if (stack.isSimilar(purchase)) {
                remaining -= Math.max(0, max - stack.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private int slotIndex(int slot) { for (int i = 0; i < ITEM_SLOTS.length; i++) if (ITEM_SLOTS[i] == slot) return i; return -1; }
    private int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; } }
    private Material material(String input) { try { return Material.valueOf(input.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Material.CHEST; } }
    private String plain(String input) { return input.replaceAll("<[^>]+>", ""); }
    private void fill(Inventory inv) { for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of())); }
    private ItemStack button(Material material, String name, List<String> lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(Text.mm(name)); meta.lore(lore.stream().map(Text::mm).toList()); item.setItemMeta(meta); return item; }
    private record ShopCategory(String id, String name, Material icon) {}
    private record ShopItem(String id, Material material, int amount, double price, String name) {}
}
