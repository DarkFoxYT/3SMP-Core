package net.dark.threecore;

import net.dark.threecore.chat.ChatFormatService;
import net.dark.threecore.commandspy.CommandSpyManager;
import net.dark.threecore.afk.AfkManager;
import net.dark.threecore.auction.AuctionHouseService;
import net.dark.threecore.clearlag.ClearLagManager;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.sell.SellService;
import net.dark.threecore.survival.SurvivalService;
import net.dark.threecore.glow.GlowManager;
import net.dark.threecore.hologram.HologramManager;
import net.dark.threecore.particle.ParticleManager;
import net.dark.threecore.rtp.RtpManager;
import net.dark.threecore.warp.WarpManager;
import net.dark.threecore.command.CoreCommandManager;
import net.dark.threecore.command.CommandRegistrar;
import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.placeholder.SmpCoreExpansion;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.Database;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.DuelLeaderboardService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.gems.GemService;
import net.dark.threecore.gems.SeasonalGemRegistry;
import net.dark.threecore.gems.listener.GemAutoApplyListener;
import net.dark.threecore.launchpads.LaunchpadService;
import net.dark.threecore.spawn.SpawnProtectionService;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.spawn.SpawnZoneManager;
import net.dark.threecore.gui.MenuListener;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ThreeSMPCorePlugin extends JavaPlugin {
    private Database database;
    private ConfigFiles configs;
    private PlayerDataRepository repository;
    private MenuService menuService;
    private PerkService perkService;
    private SapphireService sapphireService;
    private GemService gemService;
    private ChatFormatService chatFormatService;
    private PartyService partyService;
    private DuelLeaderboardService duelLeaderboardService;
    private DuelService duelService;
    private CoreCommandManager commandManager;
    private LaunchpadService launchpadService;
    private SpawnProtectionService spawnProtectionService;
    private SpawnService spawnService;
    private SpawnZoneManager spawnZoneManager;
    private GemAutoApplyListener gemAutoApplyListener;
    private WarpManager warpManager;
    private RtpManager rtpManager;
    private ParticleManager particleManager;
    private CommandSpyManager commandSpyManager;
    private GlowManager glowManager;
    private HologramManager hologramManager;
    private MoneyService moneyService;
    private SurvivalService survivalService;
    private SellService sellService;
    private AuctionHouseService auctionHouseService;
    private AfkManager afkManager;
    private ClearLagManager clearLagManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultFiles();

        this.configs = new ConfigFiles(this);
        this.database = new Database(this);
        database.init();
        this.repository = new PlayerDataRepository(database);
        this.menuService = new MenuService(this);
        this.particleManager = new ParticleManager(this, configs);
        this.perkService = new PerkService(this, configs, repository, menuService, particleManager);
        this.sapphireService = new SapphireService(this, configs, repository, menuService);
        this.partyService = new PartyService(this, configs, menuService);
        this.duelLeaderboardService = new DuelLeaderboardService(repository, menuService, configs);
        this.launchpadService = new LaunchpadService(this, configs, menuService, perkService);
        this.spawnProtectionService = new SpawnProtectionService(this);
        this.spawnService = new SpawnService(this, configs);
        this.spawnZoneManager = new SpawnZoneManager(this, configs);
        SeasonalGemRegistry gemRegistry = new SeasonalGemRegistry(configs);
        this.duelService = new DuelService(this, configs, repository, menuService, partyService, duelLeaderboardService, launchpadService);
        this.gemService = new GemService(this, configs, repository, menuService, gemRegistry);
        this.chatFormatService = new ChatFormatService(this, configs, perkService);
        this.gemAutoApplyListener = new GemAutoApplyListener(this, gemRegistry, duelService);
        this.warpManager = new WarpManager(this, configs, database);
        this.rtpManager = new RtpManager(this, configs);
        this.moneyService = new MoneyService(this, configs, repository);
        this.survivalService = new SurvivalService(this, configs, rtpManager);
        this.sellService = new SellService(this, configs, moneyService);
        this.auctionHouseService = new AuctionHouseService(this, configs, moneyService);
        this.afkManager = new AfkManager(this, configs);
        this.clearLagManager = new ClearLagManager(this, configs);
        this.commandSpyManager = new CommandSpyManager(this, configs);
        this.glowManager = new GlowManager();
        this.hologramManager = new HologramManager(this);
        this.commandManager = new CoreCommandManager(this, configs, perkService, sapphireService, gemService, chatFormatService, spawnService, launchpadService, commandSpyManager, warpManager, moneyService, clearLagManager, duelService);
        this.particleManager.reload();

        commandManager.register();
        new CommandRegistrar(this, configs, perkService, sapphireService, gemService, chatFormatService).registerAll();
        registerDirectCommand("duel", duelService::handle, duelService::complete);
        registerDirectCommand("party", partyService::handle, partyService::complete);
        registerDirectCommand("survival", context -> survivalService.handle(context.sender(), context.args()), context -> survivalService.complete(context.args()));
        registerDirectCommand("money", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> moneyService.complete(context.args()));
        registerDirectCommand("balance", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> List.of());
        registerDirectCommand("bal", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> List.of());
        registerDirectCommand("pay", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> List.of());
        registerDirectCommand("sell", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) sellService.open(player); }, context -> List.of());
        registerDirectCommand("ah", context -> auctionHouseService.handle(context.sender(), context.args()), context -> auctionHouseService.complete(context.args()));
        registerDirectCommand("auction", context -> auctionHouseService.handle(context.sender(), context.args()), context -> auctionHouseService.complete(context.args()));
        registerDirectCommand("auctionhouse", context -> auctionHouseService.handle(context.sender(), context.args()), context -> auctionHouseService.complete(context.args()));
        registerDirectCommand("spy", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) commandSpyManager.toggle(player); else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>"); }, context -> List.of());
        registerDirectCommand("spawn", context -> {
            if (!(context.sender() instanceof org.bukkit.entity.Player player)) return;
            if (context.args().length > 0 && context.arg(0).equalsIgnoreCase("set") && player.hasPermission("3smpcore.spawn.admin")) {
                spawnService.setSpawnLocation(player, player.getLocation());
                return;
            }
            spawnService.sendToSpawn(player);
        }, context -> context.args().length == 0 ? List.of("set") : List.of());
        registerDirectCommand("warp", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) {
                if (context.args().length == 0) warpManager.open(player);
                else if (context.arg(0).equalsIgnoreCase("set") && context.args().length >= 2 && player.hasPermission("3smpcore.admin")) warpManager.setWarp(player, context.arg(1), player.getLocation());
                else warpManager.teleport(player, context.arg(0));
            }
        }, context -> context.args().length == 0 ? warpManager.ids() : List.of("set"));
        registerDirectCommand("rtp", context -> rtpManager.handle(context.sender(), context.args()), context -> context.args().length == 0 ? List.of("reload") : List.of());
        registerDirectCommand("launchpad", context -> {
            if (context.args().length == 0 || context.arg(0).equalsIgnoreCase("menu")) {
                if (context.sender() instanceof org.bukkit.entity.Player player) launchpadService.openMenu(player);
                return;
            }
            if (context.arg(0).equalsIgnoreCase("give") && context.args().length >= 3) {
                org.bukkit.entity.Player target = getServer().getPlayerExact(context.arg(1));
                if (target != null) launchpadService.give(target, context.arg(2));
            }
        }, context -> context.args().length == 0 ? List.of("menu", "give") : List.of());

        getServer().getPluginManager().registerEvents(new MenuListener(duelService, partyService, perkService, gemService, sapphireService, duelLeaderboardService, launchpadService, warpManager), this);
        getServer().getPluginManager().registerEvents(launchpadService, this);
        getServer().getPluginManager().registerEvents(spawnProtectionService, this);
        getServer().getPluginManager().registerEvents(spawnService, this);
        getServer().getPluginManager().registerEvents(spawnZoneManager, this);
        getServer().getPluginManager().registerEvents(gemAutoApplyListener, this);
        getServer().getPluginManager().registerEvents(perkService, this);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                syncParticle(event.getPlayer().getUniqueId());
            }
        }, this);
        getServer().getPluginManager().registerEvents(gemService, this);
        getServer().getPluginManager().registerEvents(chatFormatService, this);
        getServer().getPluginManager().registerEvents(partyService, this);
        getServer().getPluginManager().registerEvents(duelService, this);
        getServer().getPluginManager().registerEvents(commandSpyManager, this);
        getServer().getPluginManager().registerEvents(sellService, this);
        getServer().getPluginManager().registerEvents(auctionHouseService, this);
        getServer().getPluginManager().registerEvents(afkManager, this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) { new SmpCoreExpansion(this, perkService, warpManager, spawnService, moneyService).register(); }
    }

    @Override
    public void onDisable() {
        if (particleManager != null) particleManager.shutdown();
        if (hologramManager != null) hologramManager.removeAll();
        if (spawnZoneManager != null) spawnZoneManager.reload();
        if (afkManager != null) afkManager.shutdown();
        if (clearLagManager != null) clearLagManager.shutdown();
        if (database != null) database.close();
    }

    public void reloadAll() {
        reloadConfig();
        configs.reload();
        if (commandManager != null) commandManager.reload();
        if (launchpadService != null) launchpadService.reload();
        if (warpManager != null) warpManager.reload();
        if (rtpManager != null) rtpManager.reload();
        if (particleManager != null) particleManager.reload();
        if (hologramManager != null) hologramManager.removeAll();
        if (spawnZoneManager != null) spawnZoneManager.reload();
        if (afkManager != null) afkManager.reload();
        if (clearLagManager != null) clearLagManager.reload();
        if (commandSpyManager != null) commandSpyManager.reload();
    }

    private void registerDirectCommand(String name, CommandExecutorBridge executor, CommandCompleterBridge completer) {
        PluginCommand command = getCommand(name);
        if (command == null) return;
        command.setExecutor((sender, cmd, label, args) -> {
            executor.handle(new CommandContext(sender, label, args, List.of(name)));
            return true;
        });
        command.setTabCompleter((sender, cmd, alias, args) -> completer.complete(new CommandContext(sender, alias, args, List.of(name))));
    }

    private void syncParticle(java.util.UUID uuid) {
        if (particleManager == null || perkService == null) return;
        String active = perkService.data(uuid).activeParticle();
        particleManager.set(uuid, active == null ? "" : active);
    }

    private void saveDefaultFiles() {
        for (String file : new String[]{
                "messages.yml", "help.yml", "perks.yml", "prefixes.yml", "tags.yml", "colors.yml", "cosmetics.yml", "effects.yml",
                "sapphires.yml", "gems.yml", "gem_capsules.yml", "duels.yml", "duel-kits.yml", "duel-maps.yml", "party.yml", "trims.yml",
                "duel-messages.yml", "menus/perks.yml", "menus/gems.yml", "menus/sapphires.yml", "menus/duels.yml", "menus/party.yml", "menus/dev.yml",
                "launchpads.yml", "warps.yml", "rtp.yml", "survival.yml", "money.yml", "sell.yml", "auction-house.yml", "afk.yml", "clearlag.yml", "particles.yml", "commandspy.yml", "badges.yml", "glow.yml", "holograms.yml"
        }) {
            saveResource(file, false);
        }
    }

    @FunctionalInterface
    private interface CommandExecutorBridge {
        void handle(CommandContext context);
    }

    @FunctionalInterface
    private interface CommandCompleterBridge {
        List<String> complete(CommandContext context);
    }
}
