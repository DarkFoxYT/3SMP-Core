package net.dark.threecore.visual;

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
    private final VisualCosmeticService cosmetics;
    private final VisualManager manager;
    private final Map<UUID, VisualCosmeticService.Type> pendingSigns = new ConcurrentHashMap<>();

    public VisualGuiService(JavaPlugin plugin, VisualCosmeticService cosmetics, VisualManager manager) {
        this.plugin = plugin;
        this.cosmetics = cosmetics;
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("main", null, 0), 45, "3SMP Visuals");
        fill(inv);
        inv.setItem(4, button(Material.SPYGLASS, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Visuals</bold></gradient>", List.of("<gray>Name colors, gradients, prefixes, and shadows.</gray>")));
        inv.setItem(11, button(Material.GOLD_INGOT, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Prefixes</bold>", List.of("<gray>Choose your owned prefix.</gray>")));
        inv.setItem(12, button(Material.GOLD_NUGGET, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Name Colors</bold>", List.of("<gray>Solid premium name colors.</gray>")));
        inv.setItem(13, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Name Gradients</bold>", List.of("<gray>Royal name gradients.</gray>")));
        inv.setItem(14, button(Material.ECHO_SHARD, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Text Shadows</bold>", List.of("<gray>Stored for supported render targets.</gray>")));
        inv.setItem(20, button(Material.OAK_SIGN, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Custom Hex</bold>", List.of("<gray>Type #RRGGBB or #RRGGBB:#RRGGBB.</gray>")));
        inv.setItem(22, button(Material.SPYGLASS, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Preview Current Look</bold>", List.of("<gray>Shows tab, nametag and scoreboard name.</gray>")));
        inv.setItem(24, button(Material.BARRIER, "<#ef4444><bold>Reset Cosmetics</bold>", List.of("<gray>Clear selected visuals.</gray>")));
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
            boolean owns = cosmetics.owns(player, cosmetic);
            boolean active = selected != null && selected.id().equalsIgnoreCase(cosmetic.id());
            inv.setItem(CATEGORY_SLOTS[i], cosmeticItem(cosmetic, owns, active));
        }
        if (safePage > 0) inv.setItem(45, button(Material.ARROW, "<gradient:#f4cd2a:#eda323:#d28d0d>Previous Page</gradient>", List.of("<gray>Go back one page.</gray>")));
        if (type == VisualCosmeticService.Type.NAME_COLOR || type == VisualCosmeticService.Type.NAME_GRADIENT) {
            inv.setItem(48, button(Material.OAK_SIGN, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Type Custom Hex</bold></gradient>", List.of("<gray>Use letters and numbers: #eda323</gray>", "<gray>Gradient: #f4cd2a:#eda323:#d28d0d</gray>")));
        }
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
            if (slot == 11) openCategory(player, VisualCosmeticService.Type.PREFIX);
            else if (slot == 12) openCategory(player, VisualCosmeticService.Type.NAME_COLOR);
            else if (slot == 13) openCategory(player, VisualCosmeticService.Type.NAME_GRADIENT);
            else if (slot == 14) openCategory(player, VisualCosmeticService.Type.SHADOW);
            else if (slot == 24) { cosmetics.reset(player); manager.refreshAll(); Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d>Visual cosmetics reset.</gradient>"); }
            else if (slot == 22) manager.preview(player);
            else if (slot == 20) openSign(player, VisualCosmeticService.Type.NAME_COLOR);
            return;
        }
        if (slot == 49) { open(player); return; }
        if (slot == 45 && holder.page() > 0 && holder.type() != null) { openCategory(player, holder.type(), holder.page() - 1); return; }
        if (slot == 53 && holder.type() != null) {
            List<VisualCosmeticService.Cosmetic> options = cosmetics.cosmetics(holder.type());
            if ((holder.page() + 1) * CATEGORY_SLOTS.length < options.size()) openCategory(player, holder.type(), holder.page() + 1);
            return;
        }
        if (slot == 48 && (holder.type() == VisualCosmeticService.Type.NAME_COLOR || holder.type() == VisualCosmeticService.Type.NAME_GRADIENT)) {
            openSign(player, holder.type());
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || holder.type() == null) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "visual_cosmetic"), org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null || id.isBlank()) return;
        VisualCosmeticService.Cosmetic cosmetic = cosmetics.cosmetic(holder.type(), id);
        if (cosmetic == null || !cosmetics.owns(player, cosmetic)) {
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

    private ItemStack cosmeticItem(VisualCosmeticService.Cosmetic cosmetic, boolean owns, boolean active) {
        ItemStack item = new ItemStack(owns ? cosmetic.icon() : Material.GRAY_DYE);
        if (!owns) item.setType(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm((owns ? "<gradient:#f4cd2a:#eda323:#d28d0d>" : "<gray>") + cosmetic.displayName()));
        meta.lore(List.of(Text.mm(owns ? active ? "<gradient:#f4cd2a:#eda323:#d28d0d>Selected</gradient>" : "<gray>Click to select.</gray>" : "<red>You do not own this visual.</red>")));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "visual_cosmetic"), org.bukkit.persistence.PersistentDataType.STRING, cosmetic.id());
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
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
