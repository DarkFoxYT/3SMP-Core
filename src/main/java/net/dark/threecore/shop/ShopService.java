package net.dark.threecore.shop;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopService implements Listener {
    private static final int[] DEFAULT_ITEM_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MoneyService moneyService;
    private List<ShopCategory> cachedCategories = List.of();
    private final Map<String, List<ShopItem>> cachedItems = new LinkedHashMap<>();
    private final Map<UUID, PendingQuantity> pendingQuantities = new ConcurrentHashMap<>();

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
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "shop:categories"), menuSize(), title("menu.title", "3SMP Shop"));
        fill(inv);
        inv.setItem(configs.get("economy/shop.yml").getInt("menu.header-slot", 4), button(material(configs.get("economy/shop.yml").getString("menu.header-material", "EMERALD")), configs.get("economy/shop.yml").getString("menu.header", "<gradient:#f4cd2a:#eda323:#d28d0d>3SMP Shop</gradient>"), List.of("<gray>Buy and sell everyday items.</gray>", "<gray>Balance:</gray> <white>" + moneyService.format(moneyService.balance(player.getUniqueId())) + "</white>")));
        int[] slots = configuredSlots("menu.category-slots", DEFAULT_ITEM_SLOTS);
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
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "shop:" + category.id() + ":" + Math.max(0, page)), menuSize(), title("menu.category-title", "Shop: " + plain(category.name())));
        fill(inv);
        inv.setItem(configs.get("economy/shop.yml").getInt("menu.header-slot", 4), button(category.icon(), category.name(), List.of("<gray>Balance:</gray> <white>" + moneyService.format(moneyService.balance(player.getUniqueId())) + "</white>")));
        List<ShopItem> items = items(category.id());
        int[] itemSlots = configuredSlots("menu.item-slots", DEFAULT_ITEM_SLOTS);
        int start = page * itemSlots.length;
        for (int i = 0; i < itemSlots.length && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            inv.setItem(itemSlots[i], button(icon(item), item.name(), List.of(
                    item.buyPrice() > 0.0D ? "<gray>Buy:</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + moneyService.format(item.buyPrice()) + "</gradient>" : "<gray>Buy:</gray> <red>Not available</red>",
                    item.sellPrice() > 0.0D ? "<gray>Sell:</gray> <green>" + moneyService.format(item.sellPrice()) + "</green>" : "<gray>Sell:</gray> <red>Not available</red>",
                    "<gray>Amount:</gray> <white>" + item.amount() + "</white>",
                    item.buyPrice() > 0.0D ? "<green>Left-click to buy.</green>" : "<dark_gray>Buying is disabled for this item.</dark_gray>",
                    item.sellPrice() > 0.0D ? "<yellow>Right-click to sell this amount.</yellow>" : "<dark_gray>Sell price has not been configured yet.</dark_gray>",
                    "<aqua>Shift-click to choose a quantity.</aqua>"
            )));
        }
        int previousSlot = configs.get("economy/shop.yml").getInt("menu.previous-slot", 45);
        int backSlot = configs.get("economy/shop.yml").getInt("menu.back-slot", 49);
        int nextSlot = configs.get("economy/shop.yml").getInt("menu.next-slot", 53);
        if (page > 0) inv.setItem(previousSlot, button(Material.ARROW, "<gray>Previous Page</gray>", List.of()));
        inv.setItem(backSlot, button(Material.BARRIER, "<red>Back</red>", List.of("<gray>Return to categories.</gray>")));
        if (start + itemSlots.length < items.size()) inv.setItem(nextSlot, button(Material.ARROW, "<gray>Next Page</gray>", List.of()));
        player.openInventory(inv);
    }

    public void handleClick(Player player, String context, int slot) {
        handleClick(player, context, slot, ClickType.LEFT);
    }

    public void handleClick(Player player, String context, int slot, ClickType click) {
        if (context.equalsIgnoreCase("shop:categories")) {
            int[] slots = configuredSlots("menu.category-slots", DEFAULT_ITEM_SLOTS);
            List<ShopCategory> categories = categories();
            for (int i = 0; i < slots.length && i < categories.size(); i++) if (slot == slots[i]) openCategory(player, categories.get(i).id(), 0);
            return;
        }
        if (!context.startsWith("shop:")) return;
        String[] parts = context.split(":");
        if (parts.length < 3) return;
        String category = parts[1];
        int page = parseInt(parts[2]);
        if (slot == configs.get("economy/shop.yml").getInt("menu.back-slot", 49)) { openCategories(player); return; }
        if (slot == configs.get("economy/shop.yml").getInt("menu.previous-slot", 45) && page > 0) { openCategory(player, category, page - 1); return; }
        if (slot == configs.get("economy/shop.yml").getInt("menu.next-slot", 53)) { openCategory(player, category, page + 1); return; }
        int index = slotIndex(slot);
        if (index < 0) return;
        List<ShopItem> items = items(category);
        int itemIndex = page * configuredSlots("menu.item-slots", DEFAULT_ITEM_SLOTS).length + index;
        if (itemIndex >= items.size()) return;
        ShopItem item = items.get(itemIndex);
        if (click != null && click.isShiftClick()) {
            promptQuantity(player, category, page, item, click.isRightClick());
            return;
        }
        if (click != null && click.isRightClick()) sell(player, item);
        else buy(player, item);
        openCategory(player, category, page);
    }

    private void buy(Player player, ShopItem item) {
        if (item.buyPrice() <= 0.0D) {
            Text.send(player, "<red>This item cannot be bought here.</red>");
            return;
        }
        ItemStack unit = stack(item);
        unit.setAmount(1);
        if (!canFit(player, unit, item.amount())) {
            Text.send(player, "<red>You need more inventory space.</red>");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        if (!moneyService.take(player.getUniqueId(), item.buyPrice())) { Text.send(player, "<red>You need " + moneyService.format(item.buyPrice()) + ".</red>"); return; }
        int remaining = item.amount();
        while (remaining > 0) {
            ItemStack purchase = unit.clone();
            int take = Math.min(remaining, purchase.getMaxStackSize());
            purchase.setAmount(take);
            player.getInventory().addItem(purchase);
            remaining -= take;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.35f);
        Text.actionBar(player, "<green>Bought</green> <white>" + item.amount() + "x " + item.material().name().toLowerCase(Locale.ROOT) + "</white> <gray>for</gray> <gradient:#f4cd2a:#eda323:#d28d0d>" + moneyService.format(item.buyPrice()) + "</gradient>");
    }

    private void sell(Player player, ShopItem item) {
        if (item.sellPrice() <= 0.0D) {
            Text.send(player, "<red>This item cannot be sold here.</red>");
            return;
        }
        int remaining = item.amount();
        ItemStack match = stack(item);
        match.setAmount(1);
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || !matches(stack, match, item)) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (remaining <= 0) break;
        }
        int sold = item.amount() - remaining;
        if (sold <= 0) {
            Text.send(player, "<red>You do not have enough of that item to sell.</red>");
            return;
        }
        double payout = item.sellPrice() * (sold / (double) item.amount());
        moneyService.give(player.getUniqueId(), payout);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.0f);
        Text.actionBar(player, "<green>Sold</green> <white>" + sold + "x " + item.material().name().toLowerCase(Locale.ROOT) + "</white> <gray>for</gray> <green>" + moneyService.format(payout) + "</green>");
    }

    private void promptQuantity(Player player, String category, int page, ShopItem item, boolean selling) {
        if (selling && item.sellPrice() <= 0.0D) {
            Text.send(player, "<red>This item cannot be sold here.</red>");
            return;
        }
        if (!selling && item.buyPrice() <= 0.0D) {
            Text.send(player, "<red>This item cannot be bought here.</red>");
            return;
        }
        pendingQuantities.put(player.getUniqueId(), new PendingQuantity(category, page, item, selling, System.currentTimeMillis() + 30_000L));
        player.closeInventory();
        Text.send(player, "<yellow>Type the quantity in chat, or type <white>cancel</white>.</yellow>");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuantityChat(AsyncChatEvent event) {
        PendingQuantity pending = pendingQuantities.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleQuantityInput(event.getPlayer(), input, pending));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingQuantities.remove(event.getPlayer().getUniqueId());
    }

    private void handleQuantityInput(Player player, String input, PendingQuantity pending) {
        if (input.equalsIgnoreCase("cancel")) {
            pendingQuantities.remove(player.getUniqueId());
            Text.send(player, "<gray>Shop quantity cancelled.</gray>");
            openCategory(player, pending.category(), pending.page());
            return;
        }
        if (System.currentTimeMillis() > pending.expiresAtMillis()) {
            pendingQuantities.remove(player.getUniqueId());
            Text.send(player, "<red>That shop quantity prompt expired.</red>");
            return;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            Text.send(player, "<red>Enter a whole number, or type cancel.</red>");
            return;
        }
        int max = Math.max(1, configs.get("economy/shop.yml").getInt("menu.max-quantity", 2304));
        if (quantity < 1 || quantity > max) {
            Text.send(player, "<red>Quantity must be between 1 and " + max + ".</red>");
            return;
        }
        pendingQuantities.remove(player.getUniqueId());
        ShopItem item = pending.item().withAmount(quantity);
        if (pending.selling()) sell(player, item);
        else buy(player, item);
        openCategory(player, pending.category(), pending.page());
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
        return out;
    }

    private List<ShopItem> loadItems(String category) {
        ConfigurationSection section = configs.get("economy/shop.yml").getConfigurationSection("categories." + category + ".items");
        if (section == null) return List.of();
        List<ShopItem> out = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            Material material = material(section.getString(id + ".material", id));
            String itemsAdder = section.getString(id + ".itemsadder", section.getString(id + ".itemsadder-id", ""));
            int amount = Math.max(1, section.getInt(id + ".amount", 1));
            double buyPrice = Math.max(0.0D, section.getDouble(id + ".buy-price", section.getDouble(id + ".price", 1.0D)));
            double sellPrice = section.isSet(id + ".sell-price") ? Math.max(0.0D, section.getDouble(id + ".sell-price", 0.0D)) : 0.0D;
            String name = section.getString(id + ".display-name", "<white>" + material.name() + "</white>");
            out.add(new ShopItem(id, material, itemsAdder == null ? "" : itemsAdder, amount, buyPrice, sellPrice, name));
        }
        return out;
    }

    private boolean canFit(Player player, ItemStack purchase, int amount) {
        int remaining = amount;
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

    private int slotIndex(int slot) { int[] slots = configuredSlots("menu.item-slots", DEFAULT_ITEM_SLOTS); for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i; return -1; }
    private int menuSize() { int size = configs.get("economy/shop.yml").getInt("menu.size", 54); return Math.max(9, Math.min(54, ((size + 8) / 9) * 9)); }
    private int[] configuredSlots(String path, int[] fallback) {
        List<Integer> list = configs.get("economy/shop.yml").getIntegerList(path);
        if (list.isEmpty()) return fallback;
        int[] slots = list.stream().filter(slot -> slot >= 0 && slot < menuSize()).mapToInt(Integer::intValue).toArray();
        return slots.length == 0 ? fallback : slots;
    }
    private void fill(Inventory inv) {
        if (!configs.get("economy/shop.yml").getBoolean("menu.fill.enabled", true)) return;
        ItemStack filler = button(material(configs.get("economy/shop.yml").getString("menu.fill.material", "BLACK_STAINED_GLASS_PANE")), configs.get("economy/shop.yml").getString("menu.fill.name", " "), List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }
    private int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; } }
    private Material material(String input) { try { return Material.valueOf(input.toUpperCase(Locale.ROOT)); } catch (Exception ignored) { return Material.CHEST; } }
    private String plain(String input) { return input.replaceAll("<[^>]+>", ""); }
    private ItemStack icon(ShopItem item) {
        ItemStack stack = stack(item);
        stack.setAmount(Math.max(1, Math.min(item.amount(), stack.getMaxStackSize())));
        return stack;
    }
    private ItemStack stack(ShopItem item) {
        ItemStack stack = item.itemsAdder().isBlank() ? new ItemStack(item.material()) : customItem(item.itemsAdder(), item.material());
        stack.setAmount(Math.max(1, Math.min(item.amount(), stack.getMaxStackSize())));
        return stack;
    }
    private boolean matches(ItemStack current, ItemStack match, ShopItem item) {
        if (item.itemsAdder().isBlank()) return current.getType() == item.material();
        return current.isSimilar(match);
    }
    private ItemStack customItem(String id, Material fallback) {
        if (id != null && !id.isBlank() && Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = customStack.getMethod("getInstance", String.class).invoke(null, id);
                if (stack != null) {
                    Object item = stack.getClass().getMethod("getItemStack").invoke(stack);
                    if (item instanceof ItemStack itemStack) return itemStack.clone();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return new ItemStack(fallback);
    }
    private String title(String path, String fallback) {
        String input = configs.get("economy/shop.yml").getString(path, fallback);
        if (input == null || input.isBlank()) input = fallback;
        return replaceGuiSymbols(input);
    }
    private String replaceGuiSymbols(String input) {
        String output = applyItemsAdderFontImages(input);
        ConfigurationSection shopSymbols = configs.get("economy/shop.yml").getConfigurationSection("menu.itemsadder-font-symbols");
        if (shopSymbols != null) {
            for (String key : shopSymbols.getKeys(false)) {
                output = output.replace(":" + key + ":", decodeUnicodeEscapes(shopSymbols.getString(key, "")));
            }
        }
        ConfigurationSection symbols = configs.get("menus/duels.yml").getConfigurationSection("menus.itemsadder-font-symbols");
        if (symbols != null) {
            for (String key : symbols.getKeys(false)) {
                output = output.replace(":" + key + ":", decodeUnicodeEscapes(symbols.getString(key, "")));
            }
        }
        return decodeUnicodeEscapes(output);
    }
    private String applyItemsAdderFontImages(String input) {
        if (input == null || input.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return input;
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
    private ItemStack button(Material material, String name, List<String> lore) { return button(new ItemStack(material), name, lore); }
    private ItemStack button(ItemStack item, String name, List<String> lore) { ItemMeta meta = item.getItemMeta(); meta.displayName(Text.mm(name)); meta.lore(lore.stream().map(Text::mm).toList()); item.setItemMeta(meta); return item; }
    private record ShopCategory(String id, String name, Material icon) {}
    private record PendingQuantity(String category, int page, ShopItem item, boolean selling, long expiresAtMillis) {}
    private record ShopItem(String id, Material material, String itemsAdder, int amount, double buyPrice, double sellPrice, String name) {
        private ShopItem withAmount(int newAmount) {
            double multiplier = newAmount / (double) amount;
            return new ShopItem(id, material, itemsAdder, newAmount, buyPrice * multiplier, sellPrice * multiplier, name);
        }
    }
}
