package net.dark.threecore.visual;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualGuiService implements Listener {
    private static final int[] CATEGORY_SLOTS = {
        11, 12, 13, 14, 15,
        20, 21, 22, 23, 24,
        29, 30, 31, 32, 33,
        38, 39, 40, 41, 42
    };
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final VisualCosmeticService cosmetics;
    private final VisualManager manager;
    private final PlayerDataRepository repository;
    private final Map<UUID, VisualCosmeticService.Type> pendingSigns = new ConcurrentHashMap<>();

    public VisualGuiService(JavaPlugin plugin, ConfigFiles configs, VisualCosmeticService cosmetics, VisualManager manager, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.configs = configs;
        this.cosmetics = cosmetics;
        this.manager = manager;
        this.repository = repository;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("main", null, 0), 45, "3SMP Visuals");
        fill(inv);
        inv.setItem(4, button(Material.SPYGLASS, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Visuals</bold></gradient>", List.of("<gray>Name colors and gradients.</gray>")));
        inv.setItem(20, button(Material.GOLD_NUGGET, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Name Colors</bold>", List.of("<gray>Solid premium name colors.</gray>")));
        inv.setItem(24, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Name Gradients</bold>", List.of("<gray>Royal name gradients.</gray>")));
        inv.setItem(22, button(Material.SPYGLASS, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Preview Current Look</bold>", List.of("<gray>Shows tab, nametag and scoreboard name.</gray>")));
        inv.setItem(40, button(Material.BARRIER, "<#ef4444><bold>Reset Cosmetics</bold>", List.of("<gray>Clear selected visuals.</gray>")));
        player.openInventory(inv);
    }

    private void openCategory(Player player, VisualCosmeticService.Type type) {
        openCategory(player, type, 0);
    }

    private void openCategory(Player player, VisualCosmeticService.Type type, int page) {
        List<VisualCosmeticService.Cosmetic> options = cosmetics.cosmetics(type);
        int maxPage = Math.max(0, (options.size() - 1) / CATEGORY_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(new Holder("category", type, safePage), 54, "3SMP " + nice(type));
        fill(inv);
        VisualCosmeticService.Cosmetic selected = cosmetics.selected(player, type);
        int start = safePage * CATEGORY_SLOTS.length;
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            int index = start + i;
            if (index >= options.size()) break;
            VisualCosmeticService.Cosmetic cosmetic = options.get(index);
            boolean owns = cosmetics.owns(player, type, cosmetic);
            boolean active = selected != null && selected.id().equalsIgnoreCase(cosmetic.id());
            inv.setItem(CATEGORY_SLOTS[i], cosmeticItem(type, cosmetic, owns, active));
        }
        if (safePage > 0) inv.setItem(45, button(Material.ARROW, "<gradient:#f4cd2a:#eda323:#d28d0d>Previous Page</gradient>", List.of("<gray>Go back one page.</gray>")));
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of()));
        inv.setItem(50, button(Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>Page " + (safePage + 1) + "/" + (maxPage + 1) + "</gradient>", List.of("<gray>" + options.size() + " options available.</gray>")));
        if ((safePage + 1) * CATEGORY_SLOTS.length < options.size()) inv.setItem(53, button(Material.ARROW, "<gradient:#f4cd2a:#eda323:#d28d0d>Next Page</gradient>", List.of("<gray>See more options.</gray>")));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (holder.view().equals("main")) {
            if (slot == 20) openCategory(player, VisualCosmeticService.Type.NAME_COLOR);
            else if (slot == 24) openCategory(player, VisualCosmeticService.Type.NAME_GRADIENT);
            else if (slot == 40) { cosmetics.reset(player); manager.refreshAll(); Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Visual cosmetics reset.</gradient>"); }
            else if (slot == 22) manager.preview(player);
            return;
        }
        if (slot == 49) { open(player); return; }
        if (slot == 45 && holder.page() > 0 && holder.type() != null) { openCategory(player, holder.type(), holder.page() - 1); return; }
        if (slot == 53 && holder.type() != null) {
            List<VisualCosmeticService.Cosmetic> options = cosmetics.cosmetics(holder.type());
            if ((holder.page() + 1) * CATEGORY_SLOTS.length < options.size()) openCategory(player, holder.type(), holder.page() + 1);
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || holder.type() == null) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "visual_cosmetic"), org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null || id.isBlank()) return;
        VisualCosmeticService.Cosmetic cosmetic = cosmetics.cosmetic(holder.type(), id);
        if (cosmetic == null) return;
        if (!cosmetics.owns(player, holder.type(), cosmetic)) {
            if (purchaseLockedVisual(player, holder.type(), cosmetic, holder.page())) return;
            Text.send(player, "<red>You do not own this visual.</red>");
            return;
        }
        cosmetics.select(player, holder.type(), id);
        manager.refreshAll();
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Selected visual:</gradient> <white>" + cosmetic.displayName() + "</white>");
        openCategory(player, holder.type(), holder.page());
    }

    @EventHandler
    public void onSign(SignChangeEvent event) {
        VisualCosmeticService.Type type = pendingSigns.remove(event.getPlayer().getUniqueId());
        if (type == null) return;
        String value = "";
        for (int i = 0; i < 4; i++) {
            String line = event.getLine(i);
            if (line != null && !line.isBlank()) { value = line.trim(); break; }
        }
        if (!validHexInput(value, type)) {
            Text.send(event.getPlayer(), "<red>Invalid hex. Use #eda323 or #f4cd2a:#eda323:#d28d0d.</red>");
            return;
        }
        cosmetics.selectCustom(event.getPlayer(), type, value);
        manager.refreshAll();
        Text.send(event.getPlayer(), "<gradient:#f4cd2a:#eda323:#d28d0d>Custom visual color saved.</gradient>");
    }

    private void openSign(Player player, VisualCosmeticService.Type type) {
        pendingSigns.put(player.getUniqueId(), type);
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Place/edit a sign and type your hex.</gradient> <gray>Examples: #eda323 or #f4cd2a:#eda323:#d28d0d</gray>");
        player.closeInventory();
    }

    private boolean validHexInput(String value, VisualCosmeticService.Type type) {
        if (value == null) return false;
        String hex = "#[A-Fa-f0-9]{6}";
        if (type == VisualCosmeticService.Type.NAME_COLOR) return value.matches(hex);
        return value.matches(hex + "(:#[A-Fa-f0-9]{6}){1,4}");
    }

    private boolean purchaseLockedVisual(Player player, VisualCosmeticService.Type type, VisualCosmeticService.Cosmetic cosmetic, int page) {
        String shopItem = sapphireShopItem(type);
        if (shopItem == null) return false;
        long price = configs.get("economy/sapphires.yml").getLong("sapphire.shop-items." + shopItem + ".price", 0L);
        if (price <= 0L) {
            Text.send(player, "<yellow>That visual unlock is coming soon.</yellow>");
            return true;
        }
        UUID uuid = player.getUniqueId();
        long current = repository.getSapphireBalance(uuid);
        if (current < price) {
            Text.send(player, "<red>You need " + formatSapphires(price) + " Sapphires to unlock that.</red>");
            return true;
        }
        repository.setSapphireBalance(uuid, current - price);
        cosmetics.unlock(player, type, cosmetic.id());
        cosmetics.select(player, type, cosmetic.id());
        manager.refreshAll();
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Unlocked visual:</gradient> <white>" + cosmetic.displayName() + "</white> <gray>(-" + formatSapphires(price) + " Sapphires)</gray>");
        openCategory(player, type, page);
        return true;
    }

    private ItemStack cosmeticItem(VisualCosmeticService.Type type, VisualCosmeticService.Cosmetic cosmetic, boolean owns, boolean active) {
        ItemStack item = new ItemStack(owns || sapphireShopItem(type) != null ? cosmetic.icon() : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm((owns ? "<gradient:#f4cd2a:#eda323:#d28d0d>" : "<gray>") + cosmetic.displayName()));
        meta.lore(List.of(Text.mm(visualLore(type, owns, active))));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "visual_cosmetic"), org.bukkit.persistence.PersistentDataType.STRING, cosmetic.id());
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String visualLore(VisualCosmeticService.Type type, boolean owns, boolean active) {
        if (owns) return active ? "<gradient:#f4cd2a:#eda323:#d28d0d>Selected</gradient>" : "<gray>Click to select.</gray>";
        String shopItem = sapphireShopItem(type);
        if (shopItem == null) return "<red>You do not own this visual.</red>";
        long price = configs.get("economy/sapphires.yml").getLong("sapphire.shop-items." + shopItem + ".price", 0L);
        return price > 0L
            ? "<gray>Click to unlock for <gradient:#f4cd2a:#eda323:#d28d0d>" + formatSapphires(price) + " Sapphires</gradient>.</gray>"
            : "<gray>This visual unlock is coming soon.</gray>";
    }

    private String sapphireShopItem(VisualCosmeticService.Type type) {
        return switch (type) {
            case NAME_COLOR -> "name_color";
            case NAME_GRADIENT -> "name_gradient";
            default -> null;
        };
    }

    private String formatSapphires(long amount) {
        return String.format(java.util.Locale.US, "%,d", amount);
    }

    private void fill(Inventory inv) {
        ItemStack pane = button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String nice(VisualCosmeticService.Type type) {
        return switch (type) {
            case PREFIX -> "Prefixes";
            case NAME_COLOR -> "Name Colors";
            case NAME_GRADIENT -> "Name Gradients";
            case SHADOW -> "Text Shadows";
        };
    }

    private record Holder(String view, VisualCosmeticService.Type type, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
