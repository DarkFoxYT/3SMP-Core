package net.dark.threecore.market;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MarketPlotManager {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MarketStorage storage;
    private final MenuService menuService;
    private final MoneyService moneyService;
    private final MarketWorldManager worldManager;
    private final MarketRentService rentService;
    private Location first;
    private Location second;

    public MarketPlotManager(JavaPlugin plugin, ConfigFiles configs, MarketStorage storage, MenuService menuService, MoneyService moneyService, MarketWorldManager worldManager, MarketRentService rentService) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.menuService = menuService;
        this.moneyService = moneyService;
        this.worldManager = worldManager;
        this.rentService = rentService;
        syncConfiguredPlots();
    }

    public void reload() {
        worldManager.ensureWorld();
        syncConfiguredPlots();
    }

    public void open(Player player) {
        World marketWorld = worldManager.ensureWorld();
        if (player.getWorld() != marketWorld) {
            player.teleport(new Location(marketWorld, 0.5D, marketWorld.getHighestBlockYAt(0, 0) + 1.0D, 0.5D));
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) menuService.open(player, buildMenu(player));
            }
        }.runTaskLater(plugin, 1L);
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) open(player);
            else Text.send(sender, "<red>Players only.</red>");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                player.getInventory().addItem(wand());
                Text.send(player, "<green>Market plot wand given.</green>");
            }
            case "create" -> {
                if (args.length < 2) { Text.send(sender, "<red>Usage: /3smpcore market create <name></red>"); return; }
                createPlot(sender, args[1]);
            }
            case "setname" -> {
                if (args.length < 3) { Text.send(sender, "<red>Usage: /3smpcore market setname <id> <name></red>"); return; }
                setName(args[1], args[2]);
                Text.send(sender, "<green>Market plot name updated.</green>");
            }
            case "setprice" -> {
                if (args.length < 3) { Text.send(sender, "<red>Usage: /3smpcore market setprice <id> <price></red>"); return; }
                try { setPrice(args[1], Double.parseDouble(args[2])); Text.send(sender, "<green>Market plot price updated.</green>"); }
                catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid price.</red>"); }
            }
            case "setrent" -> {
                if (args.length < 3) { Text.send(sender, "<red>Usage: /3smpcore market setrent <id> <rent></red>"); return; }
                try { setRent(args[1], Double.parseDouble(args[2])); Text.send(sender, "<green>Market plot rent updated.</green>"); }
                catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid rent.</red>"); }
            }
            case "trust" -> {
                if (args.length < 3) { Text.send(sender, "<red>Usage: /3smpcore market trust <id> <player></red>"); return; }
                trust(args[1], Bukkit.getOfflinePlayer(args[2]).getUniqueId());
                Text.send(sender, "<green>Player trusted.</green>");
            }
            case "untrust" -> {
                if (args.length < 3) { Text.send(sender, "<red>Usage: /3smpcore market untrust <id> <player></red>"); return; }
                untrust(args[1], Bukkit.getOfflinePlayer(args[2]).getUniqueId());
                Text.send(sender, "<green>Player untrusted.</green>");
            }
            case "delete" -> {
                if (args.length < 2) { Text.send(sender, "<red>Usage: /3smpcore market delete <name></red>"); return; }
                deletePlot(sender, args[1]);
            }
            case "list" -> Text.send(sender, "<gray>Market plots:</gray> <white>" + String.join(", ", listPlotIds()) + "</white>");
            case "buy" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 2) { Text.send(player, "<red>Usage: /market buy <id></red>"); return; }
                buy(player, args[1]);
            }
            case "rent" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                payRent(player);
            }
            case "tp", "teleport" -> {
                if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
                if (args.length < 2) { Text.send(player, "<red>Usage: /market teleport <id></red>"); return; }
                openById(player, args[1]);
            }
            case "setpos1" -> { if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; } setFirst(player); }
            case "setpos2" -> { if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; } setSecond(player); }
            case "world" -> Text.send(sender, "<gray>World:</gray> <white>" + worldManager.worldName() + "</white>");
            default -> {
                if (sender instanceof Player player) open(player);
                else Text.send(sender, "<yellow>Use /market or /3smpcore market wand|create|delete|list</yellow>");
            }
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("wand", "create", "delete", "list", "buy", "rent", "teleport", "setpos1", "setpos2", "setname", "setprice", "setrent", "trust", "untrust", "world");
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("buy") || args[0].equalsIgnoreCase("teleport"))) return listPlotIds();
        return List.of();
    }

    public Inventory buildMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new net.dark.threecore.gui.menu.CoreMenuHolder(net.dark.threecore.gui.menu.CoreMenuType.MARKET_MAIN, "market"), 27, title());
        fill(inv);
        inv.setItem(11, button(Material.COMPASS, "<gradient:#f4cd2a:#eda323:#d28d0d>Teleport to Plot</gradient>", List.of("<gray>Go to a market plot you own.</gray>")));
        inv.setItem(13, button(Material.CHEST, "<gradient:#f4cd2a:#eda323:#d28d0d>Manage Plot</gradient>", List.of("<gray>Change plot details.</gray>", "<gray>Open the market settings panel.</gray>")));
        inv.setItem(15, button(Material.EMERALD, "<gradient:#f4cd2a:#eda323:#d28d0d>Pay Rent</gradient>", List.of("<gray>Pay your weekly rent.</gray>")));
        inv.setItem(19, button(Material.PAPER, "<gradient:#f4cd2a:#eda323:#d28d0d>Plot Info</gradient>", List.of("<gray>Price, rent, owner, and access.</gray>")));
        inv.setItem(22, button(Material.PLAYER_HEAD, "<gradient:#f4cd2a:#eda323:#d28d0d>Trusted Players</gradient>", List.of("<gray>Add or remove trusted players.</gray>")));
        inv.setItem(25, button(Material.REDSTONE, "<gradient:#f4cd2a:#eda323:#d28d0d>Plot Settings</gradient>", List.of("<gray>Manage plot configuration.</gray>")));
        return inv;
    }

    public void handle(Player player, int slot) {
        if (slot == 11) teleportToOwned(player);
        else if (slot == 13) manageMenu(player);
        else if (slot == 15) payRent(player);
        else if (slot == 19) showPlotInfo(player);
        else if (slot == 22) Text.send(player, "<yellow>Trust management is available through plot edit commands for now.</yellow>");
        else if (slot == 25) Text.send(player, "<yellow>Plot settings are handled by the manage screen and `/market` actions.</yellow>");
    }

    public void setFirst(Player player) { first = player.getLocation(); Text.send(player, "<green>Market plot position 1 set.</green>"); }
    public void setSecond(Player player) { second = player.getLocation(); Text.send(player, "<green>Market plot position 2 set.</green>"); }

    public void createPlot(CommandSender sender, String id) {
        if (first == null || second == null) {
            Text.send(sender, "<red>Set both positions first.</red>");
            return;
        }
        YamlConfiguration yaml = configs.get("world/market.yml");
        String path = "plots." + id.toLowerCase(Locale.ROOT);
        yaml.set(path + ".name", id);
        yaml.set(path + ".price", yaml.getDouble(path + ".price", 0D));
        yaml.set(path + ".rent", yaml.getDouble(path + ".rent", 0D));
        yaml.set(path + ".world", worldManager.worldName());
        yaml.set(path + ".pos1.x", first.getX()); yaml.set(path + ".pos1.y", first.getY()); yaml.set(path + ".pos1.z", first.getZ());
        yaml.set(path + ".pos2.x", second.getX()); yaml.set(path + ".pos2.y", second.getY()); yaml.set(path + ".pos2.z", second.getZ());
        yaml.set(path + ".pos1.yaw", first.getYaw()); yaml.set(path + ".pos1.pitch", first.getPitch());
        yaml.set(path + ".pos2.yaw", second.getYaw()); yaml.set(path + ".pos2.pitch", second.getPitch());
        save(yaml, "world/market.yml");
        storage.save(load(id));
        Text.send(sender, "<green>Market plot saved.</green>");
    }

    public void deletePlot(CommandSender sender, String id) {
        storage.delete(id);
        YamlConfiguration yaml = configs.get("world/market.yml");
        yaml.set("plots." + id.toLowerCase(Locale.ROOT), null);
        save(yaml, "world/market.yml");
        Text.send(sender, "<green>Market plot deleted.</green>");
    }

    public List<String> listPlotIds() {
        List<String> ids = new ArrayList<>();
        var section = configs.get("world/market.yml").getConfigurationSection("plots");
        if (section != null) ids.addAll(section.getKeys(false));
        return ids;
    }

    public void setPrice(String id, double price) { update(id, plot -> plot.withPrice(price)); }
    public void setRent(String id, double rent) { update(id, plot -> plot.withRent(rent)); }
    public void setName(String id, String name) { update(id, plot -> plot.withName(name)); }
    public void trust(String id, UUID uuid) { update(id, plot -> { plot.trusted().add(uuid); return plot.withTrusted(plot.trusted()); }); }
    public void untrust(String id, UUID uuid) { update(id, plot -> { plot.trusted().remove(uuid); return plot.withTrusted(plot.trusted()); }); }

    public void openById(Player player, String id) {
        MarketPlot plot = loadStoredOrConfigured(id);
        if (plot == null) { Text.send(player, "<red>Plot not found.</red>"); return; }
        if (plot.owner() != null && !plot.owner().equals(player.getUniqueId()) && !plot.trusted().contains(player.getUniqueId()) && !player.hasPermission("3smpcore.market.admin")) {
            Text.send(player, "<red>You do not have access to that plot.</red>");
            return;
        }
        player.teleport(center(plot));
    }

    public MarketPlot load(String id) {
        YamlConfiguration yaml = configs.get("world/market.yml");
        String path = "plots." + id.toLowerCase(Locale.ROOT);
        if (!yaml.contains(path)) return null;
        return new MarketPlot(
                id.toLowerCase(Locale.ROOT),
                yaml.getString(path + ".name", id),
                yaml.getString(path + ".world", worldManager.worldName()),
                yaml.getDouble(path + ".pos1.x"), yaml.getDouble(path + ".pos1.y"), yaml.getDouble(path + ".pos1.z"),
                yaml.getDouble(path + ".pos2.x"), yaml.getDouble(path + ".pos2.y"), yaml.getDouble(path + ".pos2.z"),
                null, yaml.getDouble(path + ".price", 0D), yaml.getDouble(path + ".rent", 0D),
                0L, 0L, System.currentTimeMillis(), java.util.Collections.emptySet()
        );
    }

    public void buy(Player player, String id) {
        MarketPlot plot = loadStoredOrConfigured(id);
        if (plot == null) { Text.send(player, "<red>Plot not found.</red>"); return; }
        if (plot.owner() != null) { Text.send(player, "<red>This plot is already owned.</red>"); return; }
        if (!moneyService.take(player.getUniqueId(), plot.price())) { Text.send(player, "<red>You cannot afford this plot.</red>"); return; }
        storage.save(plot.withOwner(player.getUniqueId(), System.currentTimeMillis() + rentPeriodMillis(), System.currentTimeMillis()));
        Text.send(player, "<green>You bought <white>" + plot.name() + "</white>.</green>");
    }

    public void payRent(Player player) {
        MarketPlot owned = ownedPlot(player.getUniqueId());
        if (owned == null) { Text.send(player, "<red>You do not own a plot.</red>"); return; }
        rentService.payRent(player, owned.id());
    }

    public void teleportToOwned(Player player) {
        MarketPlot owned = ownedPlot(player.getUniqueId());
        if (owned == null) { Text.send(player, "<red>You do not own a plot.</red>"); return; }
        player.teleport(center(owned));
    }

    public MarketPlot ownedPlot(UUID uuid) {
        return storage.loadAll().stream().filter(plot -> uuid.equals(plot.owner())).findFirst().orElse(null);
    }

    public void setupWorld() { worldManager.ensureWorld(); }

    private void manageMenu(Player player) {
        MarketPlot owned = ownedPlot(player.getUniqueId());
        if (owned == null) { Text.send(player, "<red>You do not own a plot.</red>"); return; }
        Text.send(player, "<gray>Plot:</gray> <white>" + owned.name() + "</white> <gray>Price:</gray> <white>" + moneyService.format(owned.price()) + "</white> <gray>Rent:</gray> <white>" + moneyService.format(owned.rent()) + "</white>");
        menuService.open(player, buildMenu(player));
    }

    private void showPlotInfo(Player player) {
        MarketPlot owned = ownedPlot(player.getUniqueId());
        if (owned == null) { Text.send(player, "<red>You do not own a plot.</red>"); return; }
        Text.send(player, "<gradient:#f4cd2a:#eda323:#d28d0d><bold>Market Plot Info</bold></gradient>");
        Text.send(player, "<gray>Name:</gray> <white>" + owned.name() + "</white>");
        Text.send(player, "<gray>Price:</gray> <white>" + moneyService.format(owned.price()) + "</white>");
        Text.send(player, "<gray>Rent:</gray> <white>" + moneyService.format(owned.rent()) + "</white>");
        Text.send(player, "<gray>World:</gray> <white>" + owned.world() + "</white>");
    }

    private long rentPeriodMillis() { return Math.max(1L, configs.get("world/market.yml").getLong("rent.period-days", 7L)) * 24L * 60L * 60L * 1000L; }

    private MarketPlot update(String id, java.util.function.UnaryOperator<MarketPlot> operator) {
        MarketPlot plot = loadStoredOrConfigured(id);
        if (plot == null) return null;
        MarketPlot next = operator.apply(plot);
        storage.save(next);
        return next;
    }

    private Location center(MarketPlot plot) {
        World world = Bukkit.getWorld(plot.world());
        if (world == null) world = worldManager.ensureWorld();
        double x = (plot.pos1x() + plot.pos2x()) / 2.0D;
        double y = Math.max(plot.pos1y(), plot.pos2y()) + 1.0D;
        double z = (plot.pos1z() + plot.pos2z()) / 2.0D;
        return new Location(world, x, y, z);
    }

    private ItemStack button(Material material, String name, List<String> lore) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.displayName(Text.mm(name)); meta.lore(lore.stream().map(Text::mm).toList()); stack.setItemMeta(meta); return stack; }
    private void fill(Inventory inv) { ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta meta = pane.getItemMeta(); meta.displayName(Text.mm(" ")); pane.setItemMeta(meta); for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane); }
    private String title() { return configs.get("menus/market.yml").getString("menu.title", "3SMP Market"); }
    private void save(YamlConfiguration yaml, String file) { try { yaml.save(new File(plugin.getDataFolder(), file)); } catch (Exception ignored) {} }
    private ItemStack wand() { ItemStack stack = new ItemStack(Material.BLAZE_ROD); ItemMeta meta = stack.getItemMeta(); meta.displayName(Text.mm("<gradient:#f4cd2a:#eda323:#d28d0d>Market Plot Wand</gradient>")); meta.lore(List.of(Text.mm("<gray>Left click for pos1, right click for pos2.</gray>"))); stack.setItemMeta(meta); return stack; }

    private MarketPlot loadStoredOrConfigured(String id) {
        if (id == null || id.isBlank()) return null;
        MarketPlot plot = storage.load(id.toLowerCase(Locale.ROOT));
        if (plot != null) return plot;
        plot = load(id);
        if (plot != null) storage.save(plot);
        return plot;
    }

    private void syncConfiguredPlots() {
        for (String id : listPlotIds()) {
            if (storage.load(id.toLowerCase(Locale.ROOT)) == null) {
                MarketPlot plot = load(id);
                if (plot != null) storage.save(plot);
            }
        }
    }
}
