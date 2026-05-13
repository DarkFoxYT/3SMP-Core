package net.dark.threecore.screentext;

import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ScreenTextEditorGUI implements Listener {
    private static final String[] CONTENT_PRESETS = {
        "{grad:royal_gold}&l3SMP{/grad}\n{grad:soft}Premium survival HUD{/grad}",
        "{grad:gold}&lWARNING{/grad}\n{grad:soft}Boss incoming{/grad}",
        "{grad:royal}&lDUELS{/grad}\n{grad:gold}%player_name%{/grad}",
        "{grad:soft}%3smpcore_world%{/grad}  {grad:gold}%3smpcore_online% online{/grad}"
    };
    private final JavaPlugin plugin;
    private final ScreenTextManager manager;
    private final ScreenTextRegistry registry;
    private final NamespacedKey actionKey;
    private final NamespacedKey templateKey;
    private final Map<UUID, EditorState> states = new HashMap<>();

    public ScreenTextEditorGUI(JavaPlugin plugin, ScreenTextManager manager, ScreenTextRegistry registry) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
        this.actionKey = new NamespacedKey(plugin, "screen_text_action");
        this.templateKey = new NamespacedKey(plugin, "screen_text_template");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("main"), 27, "3SMP Screen Text");
        fill(inv);
        inv.setItem(10, button(Material.NETHER_STAR, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Create Text</bold>", List.of("<gray>Start a new screen text template.</gray>"), "create"));
        inv.setItem(12, button(Material.BOOK, "<#60a5fa><bold>Edit Templates</bold>", List.of("<gray>Open saved screen text templates.</gray>"), "templates"));
        inv.setItem(14, button(Material.SPYGLASS, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Preview Warning</bold>", List.of("<gray>Preview the default warning banner.</gray>"), "preview_default"));
        inv.setItem(16, button(Material.LODESTONE, "<#dbeafe><bold>Reload Registry</bold>", List.of("<gray>Reload screen text config.</gray>"), "reload"));
        player.openInventory(inv);
    }

    private void openTemplates(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("templates"), 54, "Screen Text Templates");
        fill(inv);
        int slot = 10;
        for (ScreenText text : registry.templates()) {
            ItemStack item = button(Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>" + text.id(), List.of("<gray>" + text.position().name() + " / " + text.layer().name() + "</gray>", "<gray>Click to edit.</gray>"), "edit");
            item.editMeta(meta -> meta.getPersistentDataContainer().set(templateKey, PersistentDataType.STRING, text.id()));
            inv.setItem(slot++, item);
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
        }
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of(), "back"));
        player.openInventory(inv);
    }

    private void openEdit(Player player, ScreenText text) {
        states.put(player.getUniqueId(), new EditorState(text, 0));
        drawEdit(player);
    }

    private void drawEdit(Player player) {
        EditorState state = states.get(player.getUniqueId());
        if (state == null) {
            open(player);
            return;
        }
        ScreenText text = state.text;
        Inventory inv = Bukkit.createInventory(new Holder("edit"), 54, "Edit Screen Text");
        fill(inv);
        inv.setItem(10, button(Material.NAME_TAG, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Content Preset</bold>", List.of("<gray>Cycle text content.</gray>", "<white>" + text.id() + "</white>"), "content"));
        inv.setItem(11, button(Material.COMPASS, "<#60a5fa><bold>Position</bold>", List.of("<gray>" + text.position().name() + "</gray>"), "position"));
        inv.setItem(12, button(Material.BEACON, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Layer</bold>", List.of("<gray>" + text.layer().name() + "</gray>"), "layer"));
        inv.setItem(13, button(Material.GOLD_INGOT, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Gradient</bold>", List.of("<gray>" + clean(text.style().gradient(), "default") + "</gray>"), "gradient"));
        inv.setItem(14, button(text.style().shadow().enabled() ? Material.ECHO_SHARD : Material.GRAY_DYE, "<#dbeafe><bold>Shadow</bold>", List.of(text.style().shadow().enabled() ? "<green>Enabled</green>" : "<red>Disabled</red>"), "shadow"));
        inv.setItem(15, button(Material.CLOCK, "<#60a5fa><bold>Animation</bold>", List.of("<gray>" + text.animation().type().name() + "</gray>"), "animation"));
        inv.setItem(16, button(Material.REPEATER, "<#dbeafe><bold>Type</bold>", List.of("<gray>" + text.type().name() + "</gray>"), "type"));
        inv.setItem(29, button(Material.SPYGLASS, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Preview Live</bold>", List.of("<gray>Show this on your screen.</gray>"), "preview"));
        inv.setItem(31, button(Material.EMERALD_BLOCK, "<#60a5fa><bold>Save</bold>", List.of("<gray>Write this template to config.</gray>"), "save"));
        inv.setItem(33, button(Material.BARRIER, "<#ef4444><bold>Remove Preview</bold>", List.of("<gray>Clear this screen text id.</gray>"), "remove"));
        inv.setItem(49, button(Material.ARROW, "<gray>Back</gray>", List.of(), "templates"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        if (holder.view.equals("main")) {
            if (action.equals("create")) openEdit(player, createTemplate());
            else if (action.equals("templates")) openTemplates(player);
            else if (action.equals("preview_default")) manager.show(player, "dungeon_warning");
            else if (action.equals("reload")) {
                manager.reload();
                Text.send(player, "<green>Screen text registry reloaded.</green>");
                open(player);
            }
            return;
        }
        if (holder.view.equals("templates")) {
            if (action.equals("back")) open(player);
            else if (action.equals("edit")) {
                String id = item.getItemMeta().getPersistentDataContainer().get(templateKey, PersistentDataType.STRING);
                ScreenText template = registry.template(id);
                if (template != null) openEdit(player, template);
            }
            return;
        }
        if (!holder.view.equals("edit")) return;
        switch (action) {
            case "templates" -> openTemplates(player);
            case "content" -> mutate(player, this::cycleContent);
            case "position" -> mutate(player, text -> text.toBuilder().position(next(Position.values(), text.position())).build());
            case "layer" -> mutate(player, text -> text.toBuilder().layer(next(Layer.values(), text.layer())).build());
            case "gradient" -> mutate(player, this::cycleGradient);
            case "shadow" -> mutate(player, text -> text.toBuilder().style(text.style().toBuilder().shadow(!text.style().shadow().enabled()).build()).build());
            case "animation" -> mutate(player, this::cycleAnimation);
            case "type" -> mutate(player, text -> text.toBuilder().type(next(ScreenTextType.values(), text.type())).build());
            case "preview" -> {
                EditorState state = states.get(player.getUniqueId());
                if (state != null) manager.show(player, state.text.toBuilder().duration(5000).type(ScreenTextType.TIMED).build());
            }
            case "save" -> {
                EditorState state = states.get(player.getUniqueId());
                if (state != null) {
                    registry.saveTemplate(state.text);
                    Text.send(player, "<green>Saved screen text template:</green> <white>" + state.text.id() + "</white>");
                }
            }
            case "remove" -> {
                EditorState state = states.get(player.getUniqueId());
                if (state != null) manager.remove(player, state.text.id());
            }
            default -> {
            }
        }
    }

    private void mutate(Player player, Mutator mutator) {
        EditorState state = states.get(player.getUniqueId());
        if (state == null) return;
        state.text = mutator.apply(state.text);
        drawEdit(player);
    }

    private ScreenText createTemplate() {
        String id = "custom_" + Long.toUnsignedString(System.currentTimeMillis(), 36);
        return ScreenText.builder()
            .id(id)
            .content(CONTENT_PRESETS[0])
            .position(Position.TOP_CENTER)
            .layer(Layer.HUD_HIGH)
            .style(ScreenTextStyle.builder().gradient("royal_gold").shadow(true).maxWidth(28).alignment(TextAlignment.CENTER).build())
            .animation(Animation.FADE_IN)
            .duration(5000)
            .build();
    }

    private ScreenText cycleContent(ScreenText text) {
        int current = 0;
        for (int i = 0; i < CONTENT_PRESETS.length; i++) {
            if (CONTENT_PRESETS[i].equals(text.content())) current = i;
        }
        return text.toBuilder().content(CONTENT_PRESETS[(current + 1) % CONTENT_PRESETS.length]).build();
    }

    private ScreenText cycleGradient(ScreenText text) {
        List<String> gradients = List.of("royal_gold", "royal", "gold", "soft", "blue_soft");
        String active = text.style().gradient().toLowerCase(Locale.ROOT);
        int index = gradients.indexOf(active);
        String next = gradients.get((index + 1 + gradients.size()) % gradients.size());
        return text.toBuilder().style(text.style().toBuilder().gradient(next).build()).build();
    }

    private ScreenText cycleAnimation(ScreenText text) {
        ScreenTextAnimation.Type next = next(ScreenTextAnimation.Type.values(), text.animation().type());
        return text.toBuilder().animation(ScreenTextAnimation.of(next, next == ScreenTextAnimation.Type.NONE ? 0 : 600)).build();
    }

    private <T> T next(T[] values, T current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private void fill(Inventory inv) {
        ItemStack pane = button(Material.BLUE_STAINED_GLASS_PANE, " ", List.of(), "");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack button(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        if (action != null && !action.isBlank()) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (material == Material.EMERALD_BLOCK || material == Material.NETHER_STAR) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Holder(String view) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class EditorState {
        private ScreenText text;
        private int preset;

        private EditorState(ScreenText text, int preset) {
            this.text = text;
            this.preset = preset;
        }
    }

    @FunctionalInterface
    private interface Mutator {
        ScreenText apply(ScreenText text);
    }
}
