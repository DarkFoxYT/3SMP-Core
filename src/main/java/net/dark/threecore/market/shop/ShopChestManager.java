package net.dark.threecore.market.shop;

import net.dark.threecore.market.MarketPlot;
import net.dark.threecore.market.MarketPlotManager;
import net.dark.threecore.market.MarketStorage;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;

public final class ShopChestManager implements Listener {
    private final JavaPlugin plugin;
    private final MarketPlotManager plotManager;
    private final MarketStorage marketStorage;
    private final ShopChestStorage storage;
    private final ShopStockService stockService;
    private final ShopTransactionService transactionService;
    private final ShopChestGui gui = new ShopChestGui();
    private final MoneyService moneyService;

    public ShopChestManager(JavaPlugin plugin, MarketPlotManager plotManager, MarketStorage marketStorage, ShopChestStorage storage, ShopStockService stockService, ShopTransactionService transactionService, MoneyService moneyService) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.marketStorage = marketStorage;
        this.storage = storage;
        this.stockService = stockService;
        this.transactionService = transactionService;
        this.moneyService = moneyService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Chest chest)) return;
        MarketPlot plot = plotAt(block.getLocation());
        if (plot == null) return;
        Player player = event.getPlayer();
        ShopChestData data = storage.load(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (player.isSneaking() && isOwner(player, plot)) {
            event.setCancelled(true);
            player.openInventory(chest.getBlockInventory());
            return;
        }
        if (data != null && data.enabled()) {
            event.setCancelled(true);
            if (isOwner(player, plot)) player.openInventory(gui.buildSettings(data));
            else player.openInventory(gui.buildBuy(data, stockService.stock(chest.getBlockInventory(), data)));
            return;
        }
        if (isOwner(player, plot)) {
            event.setCancelled(true);
            ShopChestData next = data == null ? defaultData(block.getLocation(), player.getUniqueId()) : data;
            storage.save(next);
            player.openInventory(gui.buildSettings(next));
            return;
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof net.dark.threecore.gui.menu.CoreMenuHolder holder)) return;
        if (holder.type() != net.dark.threecore.gui.menu.CoreMenuType.SHOP_CHEST) return;
        event.setCancelled(true);
        Location chest = parseKey(holder.context());
        if (chest == null) return;
        ShopChestData data = storage.load(chest.getWorld().getName(), chest.getBlockX(), chest.getBlockY(), chest.getBlockZ());
        if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("settings:")) {
            handleSettingsClick(player, chest, data, event.getRawSlot());
        } else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("buy:")) {
            if (event.getRawSlot() == 22 && data != null) {
                Block block = chest.getBlock();
                if (block.getState() instanceof Chest chestState) transactionService.buy(player, data, chestState.getBlockInventory());
            }
        }
    }

    private void handleSettingsClick(Player player, Location chestLoc, ShopChestData data, int slot) {
        if (data == null) data = defaultData(chestLoc, player.getUniqueId());
        if (slot == 11) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) { Text.send(player, "<red>Hold an item first.</red>"); return; }
            storage.save(data.withItem(held.getType().name(), held.getItemMeta() != null && held.getItemMeta().hasDisplayName() ? plain(held.getItemMeta().displayName().toString()) : held.getType().name()));
            Text.send(player, "<green>Shop item updated.</green>");
        } else if (slot == 13) {
            Text.send(player, "<yellow>Use /3smpcore market setprice or another value input flow can be added next.</yellow>");
        } else if (slot == 15) {
            Text.send(player, "<yellow>Use /3smpcore market setrent or /market quantity flow can be added next.</yellow>");
        } else if (slot == 22) {
            boolean next = !data.enabled();
            storage.save(data.withEnabled(next));
            Text.send(player, next ? "<green>Shop enabled.</green>" : "<red>Shop disabled.</red>");
        }
    }

    private MarketPlot plotAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        for (MarketPlot plot : marketStorage.loadAll()) {
            if (!plot.world().equalsIgnoreCase(location.getWorld().getName())) continue;
            double minX = Math.min(plot.pos1x(), plot.pos2x());
            double maxX = Math.max(plot.pos1x(), plot.pos2x());
            double minY = Math.min(plot.pos1y(), plot.pos2y());
            double maxY = Math.max(plot.pos1y(), plot.pos2y());
            double minZ = Math.min(plot.pos1z(), plot.pos2z());
            double maxZ = Math.max(plot.pos1z(), plot.pos2z());
            if (location.getX() >= minX && location.getX() <= maxX && location.getY() >= minY && location.getY() <= maxY && location.getZ() >= minZ && location.getZ() <= maxZ) return plot;
        }
        return null;
    }

    private boolean isOwner(Player player, MarketPlot plot) {
        return player.isOp() || player.hasPermission("3smpcore.market.admin") || (plot != null && player.getUniqueId().equals(plot.owner()));
    }

    private ShopChestData defaultData(Location loc, UUID owner) {
        return new ShopChestData(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), owner, 1.0D, 1, false, Material.COBBLESTONE.name(), "Cobblestone");
    }

    private String key(Location location) { return ShopChestStorage.key(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    private Location parseKey(String context) {
        if (context == null) return null;
        String[] parts = context.contains(":") ? context.substring(context.indexOf(':') + 1).split(":") : context.split(":");
        if (parts.length < 4) return null;
        org.bukkit.World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    private String plain(String input) { return input.replaceAll("<[^>]+>", ""); }
}
