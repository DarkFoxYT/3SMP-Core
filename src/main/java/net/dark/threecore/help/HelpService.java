package net.dark.threecore.help;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HelpService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public HelpService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void handle(CommandSender sender, String[] args) {
        if (sender instanceof Player player && configs.get("core/help.yml").getBoolean("menu.enabled", true)) {
            open(player, args.length == 0 ? configs.get("core/help.yml").getString("settings.default-page", "main") : args[0].toLowerCase(Locale.ROOT));
            return;
        }
        String pageId = args.length == 0 ? configs.get("core/help.yml").getString("settings.default-page", "main") : args[0].toLowerCase(Locale.ROOT);
        ConfigurationSection page = configs.get("core/help.yml").getConfigurationSection("pages." + pageId);
        if (page == null) {
            Text.send(sender, configs.get("core/help.yml").getString("messages.unknown-page", "<red>That help page does not exist.</red>"));
            return;
        }
        String permission = page.getString("permission", "");
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            Text.send(sender, configs.get("core/help.yml").getString("messages.no-permission", "<red>You cannot view that help page.</red>"));
            return;
        }
        for (String line : page.getStringList("lines")) {
            Text.raw(sender, replace(line, sender));
        }
    }

    public List<String> complete(String[] args) {
        if (args.length > 1) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        ConfigurationSection menuPages = configs.get("core/help.yml").getConfigurationSection("menu.pages");
        if (menuPages != null) for (String id : menuPages.getKeys(false)) ids.add(id.toLowerCase(Locale.ROOT));
        ConfigurationSection pages = configs.get("core/help.yml").getConfigurationSection("pages");
        if (pages != null) for (String id : pages.getKeys(false)) ids.add(id.toLowerCase(Locale.ROOT));
        return new ArrayList<>(ids);
    }

    public void open(Player player, String pageId) {
        var config = configs.get("core/help.yml");
        String normalized = pageId == null || pageId.isBlank() ? config.getString("settings.default-page", "main") : pageId.toLowerCase(Locale.ROOT);
        if (config.getConfigurationSection("menu.pages." + normalized) == null) normalized = config.getString("settings.default-page", "main");
        int size = Math.max(9, Math.min(54, config.getInt("menu.size", 54)));
        if (size % 9 != 0) size = 54;
        ConfigurationSection page = config.getConfigurationSection("menu.pages." + normalized);
        String rawTitle = page == null ? config.getString("menu.title", "3SMP Help") : page.getString("title", config.getString("menu.title", "3SMP Help"));
        Inventory inv = Bukkit.createInventory(new HelpHolder(normalized), size, replaceGuiSymbols(replace(rawTitle, player)));
        if (config.getBoolean("menu.fill.enabled", false)) {
            ItemStack fill = item(config.getConfigurationSection("menu.fill"), Material.BLACK_STAINED_GLASS_PANE, player);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);
        }
        if (page != null) {
            ItemStack header = item(page.getConfigurationSection("header"), Material.NETHER_STAR, player);
            int headerSlot = page.getInt("header-slot", config.getInt("menu.header-slot", -1));
            if (headerSlot >= 0 && headerSlot < inv.getSize()) inv.setItem(headerSlot, header);
        }
        ConfigurationSection buttons = buttonsFor(normalized);
        if (buttons != null) {
            for (String id : buttons.getKeys(false)) {
                ConfigurationSection section = buttons.getConfigurationSection(id);
                if (section == null) continue;
                String permission = section.getString("permission", "");
                if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;
                List<Integer> slots = buttonSlots(section, inv.getSize());
                if (slots.isEmpty()) continue;
                ItemStack button = item(section, Material.BOOK, player);
                for (int slot : slots) inv.setItem(slot, button);
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HelpHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        ConfigurationSection buttons = buttonsFor(holder.page());
        if (buttons == null) return;
        for (String id : buttons.getKeys(false)) {
            ConfigurationSection section = buttons.getConfigurationSection(id);
            if (section == null || !buttonSlots(section, event.getView().getTopInventory().getSize()).contains(slot)) continue;
            String permission = section.getString("permission", "");
            if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
                Text.send(player, configs.get("core/help.yml").getString("messages.no-permission", "<red>You cannot view that help page.</red>"));
                return;
            }
            String action = section.getString("action", "message").toLowerCase(Locale.ROOT);
            String value = replace(section.getString("value", ""), player);
            switch (action) {
                case "page" -> open(player, value);
                case "command" -> {
                    player.closeInventory();
                    player.performCommand(value.startsWith("/") ? value.substring(1) : value);
                }
                case "console-command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.replace("{player}", player.getName()));
                case "close" -> player.closeInventory();
                default -> {
                    player.closeInventory();
                    for (String line : section.getStringList("messages")) Text.raw(player, replace(line, player));
                    if (!value.isBlank()) Text.raw(player, value);
                }
            }
            return;
        }
    }

    private String replace(String input, CommandSender sender) {
        if (input == null) return "";
        return input
                .replace("{player}", sender.getName())
                .replace("{server}", configs.get("core/help.yml").getString("settings.server-name", "3SMP"))
                .replace("{version}", plugin.getPluginMeta().getVersion());
    }

    private ConfigurationSection buttonsFor(String pageId) {
        ConfigurationSection pageButtons = configs.get("core/help.yml").getConfigurationSection("menu.pages." + pageId + ".buttons");
        return pageButtons != null ? pageButtons : configs.get("core/help.yml").getConfigurationSection("menu.buttons");
    }

    private List<Integer> buttonSlots(ConfigurationSection section, int size) {
        Set<Integer> slots = new LinkedHashSet<>();
        if (section.isSet("slot")) addSlot(slots, section.getInt("slot", -1), size);

        List<?> configuredSlots = section.getList("slots");
        if (configuredSlots != null) {
            for (Object raw : configuredSlots) {
                if (raw instanceof Number number) {
                    addSlot(slots, number.intValue(), size);
                    continue;
                }
                if (raw instanceof String text) {
                    for (String part : text.split("[,\\s]+")) {
                        if (part.isBlank()) continue;
                        try {
                            addSlot(slots, Integer.parseInt(part), size);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return new ArrayList<>(slots);
    }

    private void addSlot(Set<Integer> slots, int slot, int size) {
        if (slot >= 0 && slot < size) slots.add(slot);
    }

    private String replaceGuiSymbols(String input) {
        String output = applyItemsAdderFontImages(input);
        ConfigurationSection symbols = configs.get("core/help.yml").getConfigurationSection("menu.itemsadder-font-symbols");
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

    private ItemStack item(ConfigurationSection section, Material fallback, CommandSender sender) {
        if (section == null) return new ItemStack(fallback);
        Material material = material(section.getString("material", fallback.name()), fallback);
        String itemsAdder = section.getString("itemsadder", "");
        ItemStack stack = itemsAdder == null || itemsAdder.isBlank() ? new ItemStack(material) : customItem(itemsAdder, material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(replace(section.getString("name", " "), sender)));
            meta.lore(section.getStringList("lore").stream().map(line -> Text.mm(replace(line, sender))).toList());
            int customModelData = section.getInt("custom-model-data", 0);
            if (customModelData > 0) meta.setCustomModelData(customModelData);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack customItem(String raw, Material fallback) {
        if (!raw.isBlank() && Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Method getInstance = customStack.getMethod("getInstance", String.class);
                Object stack = getInstance.invoke(null, raw);
                if (stack != null) {
                    Method getItemStack = customStack.getMethod("getItemStack");
                    Object item = getItemStack.invoke(stack);
                    if (item instanceof ItemStack itemStack) return itemStack;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return new ItemStack(fallback);
    }

    private Material material(String raw, Material fallback) {
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record HelpHolder(String page) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
}
