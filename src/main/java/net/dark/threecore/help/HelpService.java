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
import java.util.List;
import java.util.Locale;

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
        ConfigurationSection pages = configs.get("core/help.yml").getConfigurationSection("pages");
        if (pages == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (String id : pages.getKeys(false)) ids.add(id.toLowerCase(Locale.ROOT));
        return ids;
    }

    public void open(Player player, String pageId) {
        var config = configs.get("core/help.yml");
        String normalized = pageId == null || pageId.isBlank() ? config.getString("settings.default-page", "main") : pageId.toLowerCase(Locale.ROOT);
        if (config.getConfigurationSection("menu.pages." + normalized) == null) normalized = config.getString("settings.default-page", "main");
        int size = Math.max(9, Math.min(54, config.getInt("menu.size", 54)));
        if (size % 9 != 0) size = 54;
        Inventory inv = Bukkit.createInventory(new HelpHolder(normalized), size, replace(config.getString("menu.title", "3SMP Help"), player));
        ItemStack fill = item(config.getConfigurationSection("menu.fill"), Material.BLACK_STAINED_GLASS_PANE, player);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);
        ConfigurationSection page = config.getConfigurationSection("menu.pages." + normalized);
        if (page != null) {
            ItemStack header = item(page.getConfigurationSection("header"), Material.NETHER_STAR, player);
            inv.setItem(config.getInt("menu.header-slot", 4), header);
        }
        ConfigurationSection buttons = config.getConfigurationSection("menu.buttons");
        if (buttons != null) {
            for (String id : buttons.getKeys(false)) {
                ConfigurationSection section = buttons.getConfigurationSection(id);
                if (section == null) continue;
                String permission = section.getString("permission", "");
                if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;
                int slot = section.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) continue;
                inv.setItem(slot, item(section, Material.BOOK, player));
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HelpHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        ConfigurationSection buttons = configs.get("core/help.yml").getConfigurationSection("menu.buttons");
        if (buttons == null) return;
        for (String id : buttons.getKeys(false)) {
            ConfigurationSection section = buttons.getConfigurationSection(id);
            if (section == null || section.getInt("slot", -1) != slot) continue;
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
        return input
                .replace("{player}", sender.getName())
                .replace("{server}", configs.get("core/help.yml").getString("settings.server-name", "3SMP"))
                .replace("{version}", plugin.getPluginMeta().getVersion());
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
