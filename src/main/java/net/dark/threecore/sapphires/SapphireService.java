package net.dark.threecore.sapphires;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.sapphires.gui.SapphireMenu;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SapphireService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final MenuService menuService;

    public SapphireService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository, MenuService menuService) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
        this.menuService = menuService;
    }

    public void reload() {
    }

    public long balance(UUID uuid) { return repository.getSapphireBalance(uuid); }
    public void set(UUID uuid, long amount) { repository.setSapphireBalance(uuid, amount); }
    public void give(UUID uuid, long amount) { set(uuid, balance(uuid) + Math.max(0L, amount)); }
    public boolean take(UUID uuid, long amount) { long current = balance(uuid); if (amount < 0L || current < amount) return false; set(uuid, current - amount); return true; }
    public void reset(UUID uuid) { set(uuid, 0L); }
    public List<String> commandIds() {
        var section = configs.get("sapphires.yml").getConfigurationSection("sapphire.commands");
        if (section == null) return List.of();
        return List.copyOf(section.getKeys(false));
    }

    public void handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) openShop(player); else Text.send(sender, "<gray>Use /sapphire shop, /sapphire bal, /sapphire ballance, /sapphire give, /sapphire remove, /sapphire take, /sapphire set, /sapphire reset, or /sapphire commands.</gray>");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "shop" -> { if (sender instanceof Player player) openShop(player); else Text.send(sender, "<gray>Players only.</gray>"); }
            case "balance", "bal", "ballance" -> { if (sender instanceof Player player) sendBalance(player); else Text.send(sender, "<red>Players only.</red>"); }
            case "give", "remove", "take", "set", "reset" -> {
                if (!sender.hasPermission("3smpcore.sapphires.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
                if (args.length < 2) { Text.send(sender, "<red>Usage: /sapphire " + sub + " <player> [amount]</red>"); return; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                long amount = args.length >= 3 ? parseLong(args[2]) : 0L;
                apply(sub, target.getUniqueId(), amount);
                Text.send(sender, "<green>Updated sapphires for " + displayName(target, args[1]) + ".</green>");
            }
            case "commands" -> Text.send(sender, "<gray>Configured sapphire commands: " + String.join(", ", commandIds()) + "</gray>");
            default -> { if (sender instanceof Player player) openShop(player); else Text.send(sender, "<yellow>Use /sapphire shop|bal|give|remove|take|set|reset|commands</yellow>"); }
        }
    }

    public void openShop(Player player) { menuService.open(player, new SapphireMenu(this).build(player)); }
    public void openSummary(Player player) { menuService.open(player, new SapphireMenu(this).buildSummary(player)); }

    public void handleMenuClick(Player player, int slot) {
        switch (slot) {
            case 7 -> sendShopSummary(player);
            case 11 -> sendBalance(player);
            case 13 -> openShopLink(player);
            case 15 -> sendCommandHelp(player);
            default -> { }
        }
    }

    public void sendShopSummary(Player player) {
        Text.send(player, "<gradient:#1A2A4A:#D6E8F7>3SMP Sapphire Summary</gradient>");
        Text.send(player, "<gray>Balance:</gray> <white>" + balance(player.getUniqueId()) + "</white>");
        Text.send(player, "<gray>Shop link:</gray> <white>" + configs.get("sapphires.yml").getString("sapphire.shop-url", "https://example.com") + "</white>");
        Text.send(player, "<gray>Commands:</gray> <white>/sapphire bal, /sapphire ballance, /sapphire shop</white>");
    }
    public void sendBalance(Player player) { Text.send(player, "<gray>Your sapphires: <gradient:#22d3ee:#a78bfa>" + balance(player.getUniqueId()) + "</gradient></gray>"); }
    public void openShopInfo(Player player) { openShopLink(player); }
    public void sendCommandHelp(Player player) { Text.send(player, "<gray>Sapphires are premium and non-tradeable. Use <white>/sapphire bal</white> or <white>/sapphire shop</white>.</gray>"); }
    public String shopUrl() { return configs.get("sapphires.yml").getString("sapphire.shop-url", "https://example.com"); }

    public void handleSummaryClick(Player player, int slot) {
        if (slot == 22) openShop(player);
        else if (slot == 11) sendBalance(player);
        else if (slot == 13) openShopLink(player);
        else if (slot == 15) sendCommandHelp(player);
    }

    public void openShopLink(Player player) {
        String url = shopUrl();
        player.sendMessage(Component.text("Open the sapphire shop: ").append(Component.text(url).clickEvent(ClickEvent.openUrl(url))));
    }

    public void executeConfigured(String action, CommandSender sender, String targetName, long amount) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        apply(action, target.getUniqueId(), amount);
        Text.send(sender, "<green>Sapphire action executed for " + displayName(target, targetName) + ".</green>");
    }

    private void apply(String action, UUID target, long amount) {
        switch (action.toLowerCase(Locale.ROOT)) {
            case "give" -> give(target, amount);
            case "take", "remove" -> take(target, amount);
            case "set" -> set(target, amount);
            case "reset" -> reset(target);
            default -> throw new IllegalArgumentException("Unknown sapphire action: " + action);
        }
    }

    private long parseLong(String input) { try { return Long.parseLong(input); } catch (NumberFormatException ex) { return 0L; } }
    private String displayName(OfflinePlayer player, String fallback) { return player.getName() == null ? fallback : player.getName(); }
}


