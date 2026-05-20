package net.dark.threecore.money;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MoneyService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PlayerDataRepository repository;
    private final DecimalFormat format = new DecimalFormat("#,##0.##");

    public MoneyService(JavaPlugin plugin, ConfigFiles configs, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.configs = configs;
        this.repository = repository;
    }

    public double balance(UUID uuid) { return repository.getMoneyBalance(uuid); }
    public void set(UUID uuid, double amount) { repository.setMoneyBalance(uuid, amount); }
    public void give(UUID uuid, double amount) { set(uuid, balance(uuid) + Math.max(0.0D, amount)); }
    public boolean take(UUID uuid, double amount) {
        double current = balance(uuid);
        if (current < amount) return false;
        set(uuid, current - amount);
        return true;
    }
    public String format(double amount) { return configs.get("economy/money.yml").getString("currency.symbol", "$") + format.format(amount); }

    public void handle(CommandSender sender, String label, String[] args) {
        if (label.equalsIgnoreCase("balance") || label.equalsIgnoreCase("bal")) { showBalance(sender, args); return; }
        if (label.equalsIgnoreCase("pay")) { pay(sender, args); return; }
        if (args.length == 0 || args[0].equalsIgnoreCase("bal") || args[0].equalsIgnoreCase("balance")) { showBalance(sender, shift(args)); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pay" -> pay(sender, shift(args));
            case "give", "remove", "take", "set", "reset" -> admin(sender, sub, shift(args));
            case "help", "commands" -> help(sender);
            default -> help(sender);
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) return List.of("balance", "bal", "pay", "give", "remove", "take", "set", "reset", "help");
        return List.of();
    }

    private void showBalance(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length > 0 && sender.hasPermission("3smpcore.money.admin")) target = Bukkit.getOfflinePlayer(args[0]);
        else if (sender instanceof Player player) target = player;
        else { Text.send(sender, "<red>Usage: /money balance <player></red>"); return; }
        Text.send(sender, configs.get("economy/money.yml").getString("messages.balance", "<gradient:#f4cd2a:#eda323:#d28d0d>Balance</gradient> <gray>{player} has</gray> <gradient:#f4cd2a:#eda323:#d28d0d>{amount}</gradient><gray>.</gray>")
                .replace("{player}", safeName(target))
                .replace("{amount}", format(balance(target.getUniqueId()))));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return; }
        if (args.length < 2) { Text.send(player, "<red>Usage: /pay <player> <amount></red>"); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.getUniqueId().equals(player.getUniqueId())) { Text.send(player, "<red>That player is not available.</red>"); return; }
        Double amount = parse(args[1]);
        if (amount == null || amount <= 0) { Text.send(player, "<red>Invalid amount.</red>"); return; }
        if (!take(player.getUniqueId(), amount)) { Text.send(player, "<red>You do not have enough money.</red>"); return; }
        give(target.getUniqueId(), amount);
        Text.send(player, "<green>Paid " + target.getName() + " " + format(amount) + ".</green>");
        Text.send(target, "<green>Received " + format(amount) + " from " + player.getName() + ".</green>");
    }

    private void admin(CommandSender sender, String action, String[] args) {
        if (!sender.hasPermission("3smpcore.money.admin")) { Text.send(sender, "<red>No permission.</red>"); return; }
        if (args.length < 1 || (!action.equals("reset") && args.length < 2)) { Text.send(sender, "<red>Usage: /money " + action + " <player> " + (action.equals("reset") ? "" : "<amount>") + "</red>"); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        double amount = 0.0D;
        if (!action.equals("reset")) {
            Double parsed = parse(args[1]);
            if (parsed == null) { Text.send(sender, "<red>Invalid amount.</red>"); return; }
            amount = parsed;
        }
        switch (action) {
            case "give" -> give(target.getUniqueId(), amount);
            case "take", "remove" -> take(target.getUniqueId(), amount);
            case "set" -> set(target.getUniqueId(), amount);
            case "reset" -> set(target.getUniqueId(), configs.get("economy/money.yml").getDouble("starting-balance", 0.0D));
        }
        Text.send(sender, "<green>Money updated for " + safeName(target) + ": " + format(balance(target.getUniqueId())) + ".</green>");
    }

    private void help(CommandSender sender) {
        Text.send(sender, "<gradient:#f4cd2a:#eda323:#d28d0d>Money Commands</gradient>");
        Text.send(sender, "<gray>/money balance [player], /bal, /pay <player> <amount>, /sell, /ah</gray>");
        if (sender.hasPermission("3smpcore.money.admin")) Text.send(sender, "<gray>/money give|remove|set|reset <player> [amount]</gray>");
    }

    private String[] shift(String[] args) { if (args.length <= 1) return new String[0]; return java.util.Arrays.copyOfRange(args, 1, args.length); }
    private Double parse(String input) { try { return Double.parseDouble(input); } catch (NumberFormatException ex) { return null; } }
    private String safeName(OfflinePlayer player) { return player.getName() == null ? player.getUniqueId().toString() : player.getName(); }
}
