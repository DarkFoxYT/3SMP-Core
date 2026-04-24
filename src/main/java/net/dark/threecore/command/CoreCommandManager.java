package net.dark.threecore.command;

import net.dark.threecore.ThreeSMPCorePlugin;
import net.dark.threecore.chat.ChatFormatService;
import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.command.base.CommandTree;
import net.dark.threecore.command.base.SubCommand;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gems.GemService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.daily.DailyRewardManager;
import net.dark.threecore.launchpads.LaunchpadService;
import net.dark.threecore.souls.SoulManager;
import net.dark.threecore.market.MarketPlotManager;
import net.dark.threecore.commandspy.CommandSpyManager;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.warp.WarpManager;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.clearlag.ClearLagManager;
import net.dark.threecore.afk.AfkZoneManager;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CoreCommandManager implements CommandExecutor, TabCompleter {
    private final ThreeSMPCorePlugin plugin;
    private final ConfigFiles configs;
    private final PerkService perkService;
    private final SapphireService sapphireService;
    private final GemService gemService;
    private final ChatFormatService chatFormatService;
    private final SpawnService spawnService;
    private final LaunchpadService launchpadService;
    private final CommandSpyManager commandSpyManager;
    private final WarpManager warpManager;
    private final MoneyService moneyService;
    private final ClearLagManager clearLagManager;
    private final DuelService duelService;
    private final AfkZoneManager afkZoneManager;
    private final DailyRewardManager dailyRewardManager;
    private final SoulManager soulManager;
    private final MarketPlotManager marketPlotManager;
    private CommandTree root;

    public CoreCommandManager(ThreeSMPCorePlugin plugin, ConfigFiles configs, PerkService perkService, SapphireService sapphireService, GemService gemService, ChatFormatService chatFormatService, SpawnService spawnService, LaunchpadService launchpadService, CommandSpyManager commandSpyManager, WarpManager warpManager, MoneyService moneyService, ClearLagManager clearLagManager, DuelService duelService, AfkZoneManager afkZoneManager, DailyRewardManager dailyRewardManager, SoulManager soulManager, MarketPlotManager marketPlotManager) {
        this.plugin = plugin; this.configs = configs; this.perkService = perkService; this.sapphireService = sapphireService; this.gemService = gemService; this.chatFormatService = chatFormatService; this.spawnService = spawnService; this.launchpadService = launchpadService; this.commandSpyManager = commandSpyManager; this.warpManager = warpManager; this.moneyService = moneyService; this.clearLagManager = clearLagManager; this.duelService = duelService; this.afkZoneManager = afkZoneManager; this.dailyRewardManager = dailyRewardManager; this.soulManager = soulManager; this.marketPlotManager = marketPlotManager;
    }

    public void register() {
        root = new CommandTree("3smpcore", "3smpcore.admin", "Main command hub");
        root.add(new ReloadCommand()); root.add(new InfoCommand()); root.add(new AdminCommand()); root.add(new DevPanelCommand()); root.add(new DebugCommand()); root.add(new SapphireRootCommand()); root.add(new SpawnRootCommand()); root.add(new GiveRootCommand()); root.add(new LicenseCommand()); root.add(new AfkZoneCommand()); root.add(new DailyRootCommand()); root.add(new SoulsRootCommand()); root.add(new MarketRootCommand());
        PluginCommand command = plugin.getCommand("3smpcore"); if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        PluginCommand launchpadCommand = plugin.getCommand("launchpad"); if (launchpadCommand != null) { launchpadCommand.setExecutor(this::handleLaunchpad); launchpadCommand.setTabCompleter((sender, cmd, alias, args) -> args.length == 1 ? List.of("give", "menu", "settarget") : List.of()); }
    }

    private boolean handleLaunchpad(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) { if (sender instanceof Player player) launchpadService.openMenu(player); else Text.send(sender, "<red>Players only.</red>"); return true; }
        if (args[0].equalsIgnoreCase("give")) { if (!sender.hasPermission("3smpcore.launchpad.admin")) { Text.send(sender, "<red>No permission.</red>"); return true; } if (args.length < 3) { Text.send(sender, "<red>Usage: /launchpad give <player> <id></red>"); return true; } Player target = plugin.getServer().getPlayerExact(args[1]); if (target == null) { Text.send(sender, "<red>Player not found.</red>"); return true; } launchpadService.give(target, args[2]); Text.send(sender, "<green>Given launchpad to " + target.getName() + ".</green>"); return true; }
        if (args[0].equalsIgnoreCase("settarget")) { if (!sender.hasPermission("3smpcore.launchpad.admin")) { Text.send(sender, "<red>No permission.</red>"); return true; } if (!(sender instanceof Player player)) { Text.send(sender, "<red>Players only.</red>"); return true; } if (args.length < 5) { Text.send(sender, "<red>Usage: /launchpad settarget <id> <x> <y> <z> [world]</red>"); return true; } String worldName = args.length >= 6 ? args[5] : player.getWorld().getName(); var world = Bukkit.getWorld(worldName); if (world == null) { Text.send(sender, "<red>World not found.</red>"); return true; } try { double x = Double.parseDouble(args[2]); double y = Double.parseDouble(args[3]); double z = Double.parseDouble(args[4]); Location target = new Location(world, x, y, z, player.getYaw(), player.getPitch()); launchpadService.setTarget(args[1], target); Text.send(sender, "<green>Launchpad target saved.</green>"); } catch (NumberFormatException ex) { Text.send(sender, "<red>Invalid coordinates.</red>"); } return true; }
        Text.send(sender, "<yellow>Use /launchpad menu, /launchpad give <player> <id>, or /launchpad settarget <id> <x> <y> <z> [world]</yellow>"); return true;
    }

    public void reload() { sapphireService.reload(); launchpadService.reload(); commandSpyManager.reload(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { if (args.length == 0) { Text.send(sender, "<gradient:#60a5fa:#c084fc>3SMPCore</gradient> <gray>- use /3smpcore reload|admin|sapphire|spawn|devpanel|debug|daily|souls|give|info</gray>"); return true; } String sub = args[0].toLowerCase(Locale.ROOT); for (SubCommand child : root.children()) { if (child.name().equalsIgnoreCase(sub)) { if (!child.canUse(sender)) { Text.send(sender, "<red>No permission.</red>"); return true; } child.execute(new CommandContext(sender, label, Arrays.copyOfRange(args, 1, args.length), List.of(sub))); return true; } } Text.send(sender, "<red>Unknown subcommand.</red>"); return true; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if (args.length == 1) { List<String> list = new ArrayList<>(); for (SubCommand child : root.children()) if (child.canUse(sender)) list.add(child.name()); return list; } String sub = args[0].toLowerCase(Locale.ROOT); for (SubCommand child : root.children()) { if (child.name().equalsIgnoreCase(sub) && child.canUse(sender)) return child.tabComplete(new CommandContext(sender, alias, Arrays.copyOfRange(args, 1, args.length), List.of(sub))); } return List.of(); }

    private final class ReloadCommand implements SubCommand { public String name() { return "reload"; } public String permission() { return "3smpcore.reload"; } public String description() { return "Reload all systems"; } public void execute(CommandContext context) { plugin.reloadAll(); Text.send(context.sender(), "<green>Reloaded all configs and managers.</green>"); } }
    private final class InfoCommand implements SubCommand { public String name() { return "info"; } public String permission() { return "3smpcore.admin"; } public String description() { return "Plugin info"; } public void execute(CommandContext context) { Text.send(context.sender(), "<gradient:#60a5fa:#c084fc>3SMPCore</gradient> <gray>v" + plugin.getDescription().getVersion() + " loaded.</gray>"); } }

    private final class AdminCommand implements SubCommand {
        public String name() { return "admin"; }
        public String permission() { return "3smpcore.admin"; }
        public String description() { return "Admin tools"; }
        public void execute(CommandContext context) {
            if (context.args().length == 0) { help(context.sender()); return; }
            String sub = context.arg(0).toLowerCase(Locale.ROOT);
            switch (sub) {
                case "setspawn" -> { if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; } spawnService.setSpawnLocation(player, player.getLocation()); }
                case "spy" -> { if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; } commandSpyManager.toggle(player); }
                case "setwarp" -> { if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; } if (context.args().length < 2) { Text.send(player, "<red>Usage: /3smpcore admin setwarp <id></red>"); return; } warpManager.setWarp(player, context.arg(1), player.getLocation()); }
                case "clearlag" -> { int removed = clearLagManager.clear(); Text.send(context.sender(), "<green>ClearLag removed " + removed + " entities. Console logged it too.</green>"); }
                case "pvp" -> toggleSpawnPvp(context);
                case "money" -> moneyService.handle(context.sender(), "money", Arrays.copyOfRange(context.args(), 1, context.args().length));
                case "tag" -> tag(context);
                case "cosmetics" -> cosmetics(context);
                case "jobs" -> jobs(context);
                default -> help(context.sender());
            }
        }
        private void tag(CommandContext context) {
            if (context.args().length < 2) { Text.send(context.sender(), "<yellow>/3smpcore admin tag set|remove|info|list ...</yellow>"); return; }
            String action = context.arg(1).toLowerCase(Locale.ROOT);
            if (action.equals("list")) { Text.send(context.sender(), "<gray>Configured tags are managed in tags.yml and /perks list <player>.</gray>"); return; }
            if (context.args().length < 3) { Text.send(context.sender(), "<red>Usage: /3smpcore admin tag " + action + " <player> [id]</red>"); return; }
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(context.arg(2));
            if (action.equals("set")) { if (context.args().length < 4) { Text.send(context.sender(), "<red>Usage: /3smpcore admin tag set <player> <id></red>"); return; } perkService.setActive(target.getUniqueId(), "tag", context.arg(3)); Text.send(context.sender(), "<green>Tag updated.</green>"); }
            else if (action.equals("remove")) { perkService.clearActive(target.getUniqueId(), "tag"); Text.send(context.sender(), "<green>Tag removed.</green>"); }
            else if (action.equals("info")) Text.send(context.sender(), perkService.summary(target.getUniqueId()));
        }
        private void cosmetics(CommandContext context) {
            if (context.args().length < 2) { Text.send(context.sender(), "<yellow>/3smpcore admin cosmetics info|set|reset ...</yellow>"); return; }
            String action = context.arg(1).toLowerCase(Locale.ROOT);
            if (context.args().length < 3) { Text.send(context.sender(), "<red>Usage: /3smpcore admin cosmetics " + action + " <player> [id]</red>"); return; }
            org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(context.arg(2));
            if (action.equals("set")) { if (context.args().length < 4) { Text.send(context.sender(), "<red>Usage: /3smpcore admin cosmetics set <player> <id></red>"); return; } perkService.setActive(target.getUniqueId(), "cosmetic", context.arg(3)); Text.send(context.sender(), "<green>Cosmetic updated.</green>"); }
            else if (action.equals("reset")) { perkService.clearActive(target.getUniqueId(), "cosmetic"); Text.send(context.sender(), "<green>Cosmetic reset.</green>"); }
            else if (action.equals("info")) Text.send(context.sender(), perkService.summary(target.getUniqueId()));
        }
        private void toggleSpawnPvp(CommandContext context) {
            if (context.args().length < 2) { Text.send(context.sender(), "<yellow>/3smpcore admin pvp <on|off></yellow>"); return; }
            boolean enabled = context.arg(1).equalsIgnoreCase("on") || context.arg(1).equalsIgnoreCase("true");
            configs.get("core/config.yml").set("spawn.zone.pvp.enabled", enabled);
            try { configs.get("core/config.yml").save(new java.io.File(plugin.getDataFolder(), "core/config.yml")); } catch (Exception ignored) {}
            Text.send(context.sender(), enabled ? "<green>Spawn PvP enabled.</green>" : "<red>Spawn PvP disabled.</red>");
        }
        private void jobs(CommandContext context) { Text.send(context.sender(), "<yellow>Jobs admin command is reserved here; the jobs module data layer is not active in this build yet.</yellow>"); }
        private void help(CommandSender sender) {
            Text.send(sender, "<gradient:#1A2A4A:#f59e0b>Admin Commands</gradient>");
            Text.send(sender, "<gray>/3smpcore admin setspawn | spy | setwarp <id> | clearlag | pvp <on|off></gray>");
            Text.send(sender, "<gray>/3smpcore admin tag set/remove/info/list ...</gray>");
            Text.send(sender, "<gray>/3smpcore admin cosmetics info/set/reset ...</gray>");
            Text.send(sender, "<gray>/3smpcore admin money give|remove|set|reset <player> [amount]</gray>");
        }
        public List<String> tabComplete(CommandContext context) {
            if (context.args().length <= 1) return List.of("setspawn", "spy", "tag", "jobs", "cosmetics", "setwarp", "clearlag", "pvp", "money");
            if (context.arg(0).equalsIgnoreCase("tag")) return List.of("set", "remove", "info", "list");
            if (context.arg(0).equalsIgnoreCase("cosmetics")) return List.of("info", "set", "reset");
            if (context.arg(0).equalsIgnoreCase("jobs")) return List.of("info", "reset", "setlevel", "addxp", "give", "kick");
            return List.of();
        }
    }

    private final class DevPanelCommand implements SubCommand { public String name() { return "devpanel"; } public String permission() { return "3smpcore.dev"; } public String description() { return "Developer panel"; } public void execute(CommandContext context) { if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; } duelService.openDevMenu(player); } }
    private final class DebugCommand implements SubCommand { public String name() { return "debug"; } public String permission() { return "3smpcore.debug"; } public String description() { return "Debug tools"; } public void execute(CommandContext context) { String mode = context.args().length == 0 ? "status" : context.arg(0).toLowerCase(Locale.ROOT); switch (mode) { case "status" -> { Text.send(context.sender(), "<gradient:#60a5fa:#c084fc>3SMPCore Debug</gradient>"); Text.send(context.sender(), "<gray>Spawn:</gray> <white>" + spawnService.getSpawnLocation() + "</white>"); Text.send(context.sender(), "<gray>Launchpads:</gray> <white>" + launchpadService.ids().size() + " definitions</white>"); Text.send(context.sender(), "<gray>Dev panel:</gray> <white>use /3smpcore devpanel</white>"); } case "reload" -> { plugin.reloadAll(); Text.send(context.sender(), "<green>Reloaded all systems.</green>"); } case "spawn" -> { if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; } spawnService.sendToSpawn(player); } case "launchpad", "launchpads" -> { if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; } launchpadService.openMenu(player); } default -> Text.send(context.sender(), "<yellow>Use /3smpcore debug status|reload|spawn|launchpad|launchpads</yellow>"); } } public List<String> tabComplete(CommandContext context) { return context.args().length <= 1 ? List.of("status", "reload", "spawn", "launchpad", "launchpads") : List.of(); } }
    private final class SapphireRootCommand implements SubCommand { public String name() { return "sapphire"; } public String permission() { return "3smpcore.sapphires.use"; } public String description() { return "Sapphire system"; } public void execute(CommandContext context) { sapphireService.handleCommand(context.sender(), context.args()); } public List<String> tabComplete(CommandContext context) { return List.of("shop", "bal", "ballance", "balance", "give", "remove", "take", "set", "reset", "commands"); } }
    private final class SpawnRootCommand implements SubCommand { public String name() { return "spawn"; } public String permission() { return "3smpcore.spawn.use"; } public String description() { return "Teleport to spawn"; } public void execute(CommandContext context) { if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; } if (context.args().length > 0 && context.arg(0).equalsIgnoreCase("set")) { if (!player.hasPermission("3smpcore.spawn.admin")) { Text.send(player, "<red>No permission.</red>"); return; } spawnService.setSpawnLocation(player, player.getLocation()); return; } spawnService.sendToSpawn(player); } public List<String> tabComplete(CommandContext context) { return context.args().length <= 1 ? List.of("set") : List.of(); } }
    private final class GiveRootCommand implements SubCommand { public String name() { return "give"; } public String permission() { return "3smpcore.admin"; } public String description() { return "Future-proof utility commands"; } public void execute(CommandContext context) { if (context.args().length == 0) { Text.send(context.sender(), "<yellow>Use: /3smpcore give sapphire <give|take|set|reset> <player> [amount]</yellow>"); return; } if (!context.arg(0).equalsIgnoreCase("sapphire")) { Text.send(context.sender(), "<yellow>Use /sapphire shop, /sapphire bal, /sapphire pay, /sapphire give, /sapphire remove, /sapphire take, /sapphire set, /sapphire reset, or /sapphire commands.</yellow>"); return; } if (context.args().length < 3) { Text.send(context.sender(), "<red>Usage: /3smpcore give sapphire <give|take|set|reset> <player> [amount]</red>"); return; } String action = context.arg(1); String playerName = context.arg(2); long amount = context.args().length >= 4 ? Long.parseLong(context.arg(3)) : 0L; sapphireService.executeConfigured(action, context.sender(), playerName, amount); } }
    private final class AfkZoneCommand implements SubCommand {
        public String name() { return "afkzone"; }
        public String permission() { return "3smpcore.afkzone.admin"; }
        public String description() { return "AFK zone tools"; }
        public void execute(CommandContext context) { afkZoneManager.handle(context.sender(), context.args()); }
        public List<String> tabComplete(CommandContext context) { return afkZoneManager.complete(context.args()); }
    }
    private final class DailyRootCommand implements SubCommand {
        public String name() { return "daily"; }
        public String permission() { return "3smpcore.daily.use"; }
        public String description() { return "Daily rewards"; }
        public void execute(CommandContext context) {
            if (!(context.sender() instanceof Player player)) {
                Text.send(context.sender(), "<red>Players only.</red>");
                return;
            }
            dailyRewardManager.open(player);
        }
        public List<String> tabComplete(CommandContext context) { return List.of(); }
    }
    private final class SoulsRootCommand implements SubCommand {
        public String name() { return "souls"; }
        public String permission() { return "3smpcore.souls.use"; }
        public String description() { return "Souls system"; }
        public void execute(CommandContext context) {
            if (!(context.sender() instanceof Player player)) {
                Text.send(context.sender(), "<red>Players only.</red>");
                return;
            }
            soulManager.open(player);
        }
        public List<String> tabComplete(CommandContext context) { return List.of("sell", "trade"); }
    }
    private final class MarketRootCommand implements SubCommand {
        public String name() { return "market"; }
        public String permission() { return "3smpcore.market.use"; }
        public String description() { return "Market district"; }
        public void execute(CommandContext context) { marketPlotManager.handle(context.sender(), context.args()); }
        public List<String> tabComplete(CommandContext context) { return marketPlotManager.complete(context.args()); }
    }
    private final class LicenseCommand implements SubCommand {
        public String name() { return "license"; }
        public String permission() { return "3smpcore.admin"; }
        public String description() { return "License tools"; }
        public void execute(CommandContext context) {
            if (context.args().length == 0) {
                Text.send(context.sender(), "<gray>Use /3smpcore license status|reload</gray>");
                return;
            }
            String sub = context.arg(0).toLowerCase(Locale.ROOT);
            switch (sub) {
                case "status" -> {
                    boolean valid = plugin.getLicenseManager() != null && plugin.getLicenseManager().validate();
                    Text.send(context.sender(), valid ? "<green>License is valid.</green>" : "<red>License is invalid.</red>");
                }
                case "reload" -> {
                    boolean valid = plugin.getLicenseManager() != null && plugin.getLicenseManager().validate();
                    Text.send(context.sender(), valid ? "<green>License revalidated successfully.</green>" : "<red>License is invalid.</red>");
                    if (!valid) plugin.getServer().getPluginManager().disablePlugin(plugin);
                }
                default -> Text.send(context.sender(), "<gray>Use /3smpcore license status|reload</gray>");
            }
        }
        public List<String> tabComplete(CommandContext context) { return context.args().length <= 1 ? List.of("status", "reload") : List.of(); }
    }
}
