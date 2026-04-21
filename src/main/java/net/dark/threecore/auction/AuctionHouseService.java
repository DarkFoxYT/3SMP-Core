package net.dark.threecore.auction;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuctionHouseService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MoneyService moneyService;
    private final Map<String, Auction> auctions = new LinkedHashMap<>();
    private int nextId = 1;

    public AuctionHouseService(JavaPlugin plugin, ConfigFiles configs, MoneyService moneyService) {
        this.plugin = plugin;
        this.configs = configs;
        this.moneyService = moneyService;
        load();
    }

    public void handle(org.bukkit.command.CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) { open(player, "all"); return; }
        if (isCategory(args[0])) { open(player, args[0].toLowerCase(Locale.ROOT)); return; }
        if (args[0].equalsIgnoreCase("sell")) { sell(player, args); return; }
        if (args[0].equalsIgnoreCase("reload") && player.hasPermission("3smpcore.auction.admin")) { load(); Text.send(player, "<green>Auction house reloaded.</green>"); return; }
        Text.send(player, "<yellow>Use /ah or /ah sell <price>.</yellow>");
    }

    public List<String> complete(String[] args) { return args.length <= 1 ? List.of("open", "sell", "armor", "weapons", "tools", "blocks", "consumables", "misc", "reload") : List.of(); }

    public void open(Player player) { open(player, "all"); }

    public void open(Player player, String category) {
        String active = category == null || category.isBlank() ? "all" : category.toLowerCase(Locale.ROOT);
        Inventory inv = Bukkit.createInventory(new AhHolder(active), 54, configs.get("auction-house.yml").getString("menu.title", "3SMP Auction House"));
        for (int i = 0; i < 54; i++) inv.setItem(i, pane());
        inv.setItem(1, categoryIcon(Material.NETHER_STAR, "all", active));
        inv.setItem(2, categoryIcon(Material.DIAMOND_CHESTPLATE, "armor", active));
        inv.setItem(3, categoryIcon(Material.DIAMOND_SWORD, "weapons", active));
        inv.setItem(4, categoryIcon(Material.DIAMOND_PICKAXE, "tools", active));
        inv.setItem(5, categoryIcon(Material.GRASS_BLOCK, "blocks", active));
        inv.setItem(6, categoryIcon(Material.GOLDEN_CARROT, "consumables", active));
        inv.setItem(7, categoryIcon(Material.CHEST, "misc", active));
        int slot = 10;
        for (Auction auction : auctions.values()) {
            if (!active.equals("all") && !categoryOf(auction.item).equals(active)) continue;
            inv.setItem(slot++, icon(auction));
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
            if (slot >= 44) break;
        }
        player.openInventory(inv);
    }

    public void sell(Player player, String[] args) {
        if (args.length < 2) { Text.send(player, "<red>Usage: /ah sell <price></red>"); return; }
        double price;
        try { price = Double.parseDouble(args[1]); } catch (NumberFormatException ex) { Text.send(player, "<red>Invalid price.</red>"); return; }
        if (price <= 0.0D) { Text.send(player, "<red>Price must be positive.</red>"); return; }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) { Text.send(player, "<red>Hold the item you want to list.</red>"); return; }
        String id = String.valueOf(nextId++);
        auctions.put(id, new Auction(id, player.getUniqueId(), price, hand.clone()));
        player.getInventory().setItemInMainHand(null);
        save();
        Text.send(player, "<green>Listed item for " + moneyService.format(price) + ".</green>");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AhHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String clickedCategory = categorySlot(event.getRawSlot());
        if (clickedCategory != null) { open(player, clickedCategory); return; }
        int index = slotIndex(event.getRawSlot());
        if (index < 0) return;
        List<Auction> list = new ArrayList<>(auctions.values()).stream().filter(a -> holder.category.equals("all") || categoryOf(a.item).equals(holder.category)).toList();
        if (index >= list.size()) return;
        Auction auction = list.get(index);
        if (auction.seller.equals(player.getUniqueId())) { Text.send(player, "<red>You cannot buy your own listing.</red>"); return; }
        if (!moneyService.take(player.getUniqueId(), auction.price)) { Text.send(player, "<red>You need " + moneyService.format(auction.price) + ".</red>"); return; }
        moneyService.give(auction.seller, auction.price);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(auction.item.clone());
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        auctions.remove(auction.id);
        save();
        Text.send(player, "<green>Purchased auction for " + moneyService.format(auction.price) + ".</green>");
        open(player, holder.category);
    }

    private void load() {
        auctions.clear();
        YamlConfiguration yaml = configs.get("auction-house.yml");
        ConfigurationSection sec = yaml.getConfigurationSection("listings");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            UUID seller = UUID.fromString(sec.getString(id + ".seller"));
            double price = sec.getDouble(id + ".price");
            ItemStack item = sec.getItemStack(id + ".item");
            if (item == null) continue;
            auctions.put(id, new Auction(id, seller, price, item));
            try { nextId = Math.max(nextId, Integer.parseInt(id) + 1); } catch (NumberFormatException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yaml = configs.get("auction-house.yml");
        yaml.set("listings", null);
        for (Auction auction : auctions.values()) {
            String path = "listings." + auction.id;
            yaml.set(path + ".seller", auction.seller.toString());
            yaml.set(path + ".price", auction.price);
            yaml.set(path + ".item", auction.item);
        }
        try { yaml.save(new File(plugin.getDataFolder(), "auction-house.yml")); } catch (Exception ex) { plugin.getLogger().warning("Failed to save auction-house.yml: " + ex.getMessage()); }
    }

    private ItemStack icon(Auction auction) {
        ItemStack item = auction.item.clone();
        ItemMeta meta = item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.seller);
        lore.add(Text.mm("<gray>Seller:</gray> <white>" + (seller.getName() == null ? auction.seller : seller.getName()) + "</white>"));
        lore.add(Text.mm("<gray>Price:</gray> <green>" + moneyService.format(auction.price) + "</green>"));
        lore.add(Text.mm("<yellow>Click to buy.</yellow>"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCategory(String input) { return List.of("all", "armor", "weapons", "tools", "blocks", "consumables", "misc").contains(input.toLowerCase(Locale.ROOT)); }
    private String categorySlot(int slot) { return switch (slot) { case 1 -> "all"; case 2 -> "armor"; case 3 -> "weapons"; case 4 -> "tools"; case 5 -> "blocks"; case 6 -> "consumables"; case 7 -> "misc"; default -> null; }; }
    private String categoryOf(ItemStack item) {
        String name = item.getType().name();
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) return "armor";
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT")) return "weapons";
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.equals("FISHING_ROD") || name.equals("SHEARS")) return "tools";
        if (item.getType().isBlock()) return "blocks";
        if (item.getType().isEdible() || name.endsWith("POTION")) return "consumables";
        return "misc";
    }
    private ItemStack categoryIcon(Material material, String category, String active) { return pane(material, (active.equals(category) ? "<green>" : "<gray>") + category.substring(0,1).toUpperCase(Locale.ROOT) + category.substring(1) + "</" + (active.equals(category) ? "green" : "gray") + ">"); }
    private ItemStack pane() { return pane(Material.BLUE_STAINED_GLASS_PANE, " "); }
    private ItemStack pane(Material material, String name) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.displayName(Text.mm(name)); stack.setItemMeta(meta); return stack; }
    private int slotIndex(int slot) { int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43}; for (int i=0;i<slots.length;i++) if (slots[i]==slot) return i; return -1; }
    private record Auction(String id, UUID seller, double price, ItemStack item) {}
    private static final class AhHolder implements InventoryHolder { private final String category; private AhHolder(String category) { this.category = category; } @Override public Inventory getInventory() { return null; } }
}
