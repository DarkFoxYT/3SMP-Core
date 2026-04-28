package net.dark.threecore;

import net.dark.threecore.chat.ChatFormatService;
import net.dark.threecore.commandspy.CommandSpyManager;
import net.dark.threecore.afk.AfkZoneManager;
import net.dark.threecore.auction.AuctionHouseService;
import net.dark.threecore.clearlag.ClearLagManager;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.sell.SellService;
import net.dark.threecore.shop.ShopService;
import net.dark.threecore.market.shop.ShopChestManager;
import net.dark.threecore.market.shop.ShopChestStorage;
import net.dark.threecore.market.shop.ShopStockService;
import net.dark.threecore.market.shop.ShopTransactionService;
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
import net.dark.threecore.placeholder.ThreeSmpCoreExpansion;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.Database;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.DuelLeaderboardService;
import net.dark.threecore.daily.DailyRewardManager;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.welcome.WelcomeService;
import net.dark.threecore.joinqueue.JoinQueueService;
import net.dark.threecore.essentials.EssentialCommandService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.gems.GemService;
import net.dark.threecore.gems.SeasonalGemRegistry;
import net.dark.threecore.gems.listener.GemAutoApplyListener;
import net.dark.threecore.fishing.FishingListener;
import net.dark.threecore.fishing.FishingRewardManager;
import net.dark.threecore.launchpads.LaunchpadService;
import net.dark.threecore.spawn.SpawnProtectionService;
import net.dark.threecore.spawn.SpawnService;
import net.dark.threecore.license.LicenseManager;
import net.dark.threecore.zonepvp.ZonePvpService;
import net.dark.threecore.social.FriendService;
import net.dark.threecore.social.SocialTabService;
import net.dark.threecore.market.MarketWorldManager;
import net.dark.threecore.market.MarketStorage;
import net.dark.threecore.market.MarketRentService;
import net.dark.threecore.market.MarketPlotManager;
import net.dark.threecore.market.MarketProtectionListener;
import net.dark.threecore.spawn.SpawnZoneManager;
import net.dark.threecore.gui.MenuListener;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.shared.EconomyService;
import net.dark.threecore.shared.GuiThemeService;
import net.dark.threecore.shared.MessageService;
import net.dark.threecore.shared.PlayerDataService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import net.dark.threecore.souls.SoulDropService;
import net.dark.threecore.souls.SoulManager;
import net.dark.threecore.souls.SoulStorage;
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
    private ShopService shopService;
    private AuctionHouseService auctionHouseService;
    private AfkZoneManager afkZoneManager;
    private ClearLagManager clearLagManager;
    private FishingRewardManager fishingRewardManager;
    private FishingListener fishingListener;
    private ZonePvpService zonePvpService;
    private FriendService friendService;
    private SocialTabService socialTabService;
    private DungeonService dungeonService;
    private WelcomeService welcomeService;
    private JoinQueueService joinQueueService;
    private EssentialCommandService essentialCommandService;
    private DailyRewardManager dailyRewardManager;
    private LicenseManager licenseManager;
    private SoulManager soulManager;
    private SoulDropService soulDropService;
    private MarketWorldManager marketWorldManager;
    private MarketStorage marketStorage;
    private MarketRentService marketRentService;
    private MarketPlotManager marketPlotManager;
    private MarketProtectionListener marketProtectionListener;
    private ShopChestManager shopChestManager;
    private EconomyService economyService;
    private MessageService messageService;
    private GuiThemeService guiThemeService;
    private PlayerDataService playerDataService;

    @Override
    public void onEnable() {
        saveDefaultFiles();

        this.licenseManager = new LicenseManager(this);
        licenseManager.ensureTemplate();
        if (!licenseManager.validate()) {
            getLogger().severe("3SMPCore license validation failed. Create a valid license/license.yml before enabling gameplay systems.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.configs = new ConfigFiles(this);
        this.database = new Database(this);
        database.init();
        this.repository = new PlayerDataRepository(database);
        this.playerDataService = new PlayerDataService(repository);
        this.menuService = new MenuService(this);
        this.messageService = new MessageService(configs);
        this.guiThemeService = new GuiThemeService(configs);
        this.particleManager = new ParticleManager(this, configs);
        this.perkService = new PerkService(this, configs, repository, menuService, particleManager);
        this.sapphireService = new SapphireService(this, configs, repository, menuService);
        this.partyService = new PartyService(this, configs, menuService);
        this.friendService = new FriendService(this, repository);
        this.duelLeaderboardService = new DuelLeaderboardService(repository, menuService, configs);
        this.launchpadService = new LaunchpadService(this, configs, menuService, perkService);
        this.spawnProtectionService = new SpawnProtectionService(this, configs);
        this.spawnService = new SpawnService(this, configs);
        this.spawnZoneManager = new SpawnZoneManager(this, configs);
        SeasonalGemRegistry gemRegistry = new SeasonalGemRegistry(configs);
        this.duelService = new DuelService(this, configs, repository, menuService, partyService, duelLeaderboardService, launchpadService, dungeonService);
        this.partyService.setDuelService(duelService);
        this.gemService = new GemService(this, configs, repository, menuService, gemRegistry);
        this.chatFormatService = new ChatFormatService(this, configs, perkService);
        this.gemAutoApplyListener = new GemAutoApplyListener(this, gemRegistry, duelService, configs);
        this.warpManager = new WarpManager(this, configs, database);
        this.rtpManager = new RtpManager(this, configs);
        this.moneyService = new MoneyService(this, configs, repository);
        this.economyService = new EconomyService(this, moneyService);
        this.survivalService = new SurvivalService(this, configs, rtpManager, repository);
        this.sellService = new SellService(this, configs, moneyService);
        this.shopService = new ShopService(this, configs, moneyService);
        this.auctionHouseService = new AuctionHouseService(this, configs, moneyService);
        this.dailyRewardManager = new DailyRewardManager(this, configs, database, menuService, moneyService);
        this.fishingRewardManager = new FishingRewardManager(this, configs, repository, moneyService, menuService);
        this.afkZoneManager = new AfkZoneManager(this, configs, menuService);
        this.soulManager = new SoulManager(this, configs, new SoulStorage(database), moneyService, menuService);
        this.marketWorldManager = new MarketWorldManager(this, configs);
        this.marketStorage = new MarketStorage(database);
        this.marketRentService = new MarketRentService(this, configs, marketStorage, moneyService, marketWorldManager);
        this.marketPlotManager = new MarketPlotManager(this, configs, marketStorage, menuService, moneyService, marketWorldManager, marketRentService);
        this.marketProtectionListener = new MarketProtectionListener(this, marketStorage, marketWorldManager);
        ShopChestStorage shopChestStorage = new ShopChestStorage(database);
        ShopStockService shopStockService = new ShopStockService();
        ShopTransactionService shopTransactionService = new ShopTransactionService(moneyService, shopStockService, shopChestStorage);
        this.shopChestManager = new ShopChestManager(this, marketPlotManager, marketStorage, shopChestStorage, shopStockService, shopTransactionService, moneyService);
        this.clearLagManager = new ClearLagManager(this, configs);
        this.dungeonService = new DungeonService(this, configs, menuService, repository, partyService, survivalService);
        this.duelService.setDungeonService(dungeonService);
        this.fishingListener = new FishingListener(fishingRewardManager, duelService, dungeonService);
        this.socialTabService = new SocialTabService(this);
        this.partyService.setDungeonService(dungeonService);
        this.partyService.setFriendService(friendService);
        this.partyService.setSocialTabService(socialTabService);
        this.dungeonService.setSocialTabService(socialTabService);
        this.socialTabService.bind(partyService, dungeonService);
        this.welcomeService = new WelcomeService(this, configs);
        this.spawnService.setWelcomeService(welcomeService);
        this.spawnService.setSurvivalService(survivalService);
        this.spawnService.setPerkService(perkService);
        this.spawnService.setDuelService(duelService);
        this.spawnService.setPartyService(partyService);
        this.spawnService.setDungeonService(dungeonService);
        this.survivalService.setSpawnService(spawnService);
        this.joinQueueService = new JoinQueueService(this, configs, spawnService, welcomeService);
        this.essentialCommandService = new EssentialCommandService();
        this.zonePvpService = new ZonePvpService(this, configs, repository);
        this.zonePvpService.setCosmeticsItemRefresher(perkService::giveCosmeticsItem);
        this.spawnZoneManager.zonePvpService(zonePvpService);
        this.soulDropService = new SoulDropService(this, configs, soulManager, duelService, dungeonService, zonePvpService);
        this.commandSpyManager = new CommandSpyManager(this, configs);
        this.glowManager = new GlowManager();
        this.hologramManager = new HologramManager(this, configs, repository);
        if (hologramManager != null) hologramManager.reload();
        this.commandManager = new CoreCommandManager(this, configs, perkService, sapphireService, gemService, chatFormatService, spawnService, launchpadService, commandSpyManager, warpManager, moneyService, clearLagManager, duelService, afkZoneManager, dailyRewardManager, soulManager, marketPlotManager);
        duelService.addPostMatchItemRefresher(perkService::giveCosmeticsItem);
        duelService.addPostMatchItemRefresher(dungeonService::giveItem);
        this.particleManager.reload();
        marketWorldManager.ensureWorld();
        marketRentService.start();

        commandManager.register();
        new CommandRegistrar(this, configs, perkService, sapphireService, gemService, chatFormatService).registerAll();
        registerDirectCommand("duel", duelService::handle, duelService::complete);
        registerDirectCommand("savearena", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) duelService.saveArenaCommand(player); }, context -> List.of());
        registerDirectCommand("zonepvp", context -> zonePvpService.handle(context.sender(), context.args()), context -> zonePvpService.complete(context.args()));
        registerDirectCommand("dungeon", context -> dungeonService.handle(context.sender(), context.args()), context -> dungeonService.complete(context.args()));
        registerDirectCommand("d", context -> dungeonService.handle(context.sender(), context.args()), context -> dungeonService.complete(context.args()));
        registerDirectCommand("dungeons", context -> dungeonService.handle(context.sender(), context.args()), context -> dungeonService.complete(context.args()));
        registerDirectCommand("leave", context -> {
            if (!(context.sender() instanceof org.bukkit.entity.Player player)) return;
            if (dungeonService.isInActiveDungeon(player.getUniqueId()) || dungeonService.isDungeonWorld(player.getWorld())) dungeonService.leave(player);
            else duelService.leaveDuelOrQueue(player);
        }, context -> List.of());
        registerDirectCommand("spectate", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) duelService.spectate(player, context.arg(0)); }, context -> getServer().getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList());
        registerDirectCommand("spec", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) duelService.spectate(player, context.arg(0)); }, context -> getServer().getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).toList());
        registerDirectCommand("party", partyService::handle, partyService::complete);
        registerDirectCommand("friend", context -> friendService.handle(context), context -> friendService.complete(context));
        registerDirectCommand("friends", context -> friendService.handle(context), context -> friendService.complete(context));
        registerDirectCommand("devpanel", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) duelService.openDevMenu(player); else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>"); }, context -> List.of());
        registerDirectCommand("survival", context -> survivalService.handle(context.sender(), context.args()), context -> survivalService.complete(context.args()));
        registerDirectCommand("money", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> moneyService.complete(context.args()));
        registerDirectCommand("balance", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> List.of());
        registerDirectCommand("bal", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> List.of());
        registerDirectCommand("pay", context -> moneyService.handle(context.sender(), context.label(), context.args()), context -> List.of());
        registerDirectCommand("sell", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) sellService.open(player); }, context -> List.of());
        registerDirectCommand("shop", context -> shopService.handle(context.sender(), context.args()), context -> shopService.complete(context.args()));
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
        registerDirectCommand("afk", context -> {
            if (!(context.sender() instanceof org.bukkit.entity.Player player)) return;
            if (!player.hasPermission("3smpcore.command.afk")) {
                net.dark.threecore.text.Text.send(player, "<red>No permission.</red>");
                return;
            }
            afkZoneManager.enterCommand(player);
        }, context -> List.of());
        registerDirectCommand("daily", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) dailyRewardManager.open(player);
            else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>");
        }, context -> List.of());
        registerDirectCommand("rewards", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) dailyRewardManager.open(player);
            else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>");
        }, context -> List.of());
        registerDirectCommand("souls", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) soulManager.open(player);
            else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>");
        }, context -> List.of("sell", "trade"));
        registerDirectCommand("fishdebug", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) {
                if (!player.hasPermission("3smpcore.fishing.debug")) {
                    net.dark.threecore.text.Text.send(player, "<red>No permission.</red>");
                    return;
                }
                fishingRewardManager.debugStatus(player);
            } else {
                net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>");
            }
        }, context -> List.of());
        registerDirectCommand("fishingdebug", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) {
                if (!player.hasPermission("3smpcore.fishing.debug")) {
                    net.dark.threecore.text.Text.send(player, "<red>No permission.</red>");
                    return;
                }
                fishingRewardManager.debugStatus(player);
            } else {
                net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>");
            }
        }, context -> List.of());
        registerDirectCommand("market", context -> marketPlotManager.handle(context.sender(), context.args()), context -> marketPlotManager.complete(context.args()));
        registerDirectCommand("warp", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) {
                if (context.args().length == 0) warpManager.open(player);
                else if (context.arg(0).equalsIgnoreCase("set") && context.args().length >= 2 && player.hasPermission("3smpcore.admin")) warpManager.setWarp(player, context.arg(1), player.getLocation());
                else warpManager.teleport(player, context.arg(0));
            }
        }, context -> context.args().length == 0 ? warpManager.ids() : List.of("set"));
        registerDirectCommand("rtp", context -> rtpManager.handle(context.sender(), context.args()), context -> context.args().length == 0 ? List.of("reload") : List.of());
        registerDirectCommand("speed", context -> essentialCommandService.speed(context), context -> List.of("0", "1", "2", "3", "5", "10"));
        registerDirectCommand("fly", context -> essentialCommandService.fly(context), context -> List.of("true", "false"));
        registerDirectCommand("gamemode", context -> essentialCommandService.gamemode(context), context -> essentialCommandService.completeGameMode(context.args()));
        registerDirectCommand("gm", context -> essentialCommandService.gamemode(context), context -> essentialCommandService.completeGameMode(context.args()));
        registerDirectCommand("gms", context -> essentialCommandService.shortcut(context, org.bukkit.GameMode.SURVIVAL), context -> List.of());
        registerDirectCommand("gmc", context -> essentialCommandService.shortcut(context, org.bukkit.GameMode.CREATIVE), context -> List.of());
        registerDirectCommand("gma", context -> essentialCommandService.shortcut(context, org.bukkit.GameMode.ADVENTURE), context -> List.of());
        registerDirectCommand("gmsp", context -> essentialCommandService.shortcut(context, org.bukkit.GameMode.SPECTATOR), context -> List.of());
        registerDirectCommand("time", context -> essentialCommandService.time(context), context -> essentialCommandService.completeTime(context.args()));
        registerDirectCommand("gamerule", context -> essentialCommandService.gamerule(context), context -> essentialCommandService.completeGamerule(context.args()));        registerDirectCommand("launchpad", context -> {
            if (context.args().length == 0 || context.arg(0).equalsIgnoreCase("menu")) {
                if (context.sender() instanceof org.bukkit.entity.Player player) launchpadService.openMenu(player);
                return;
            }
            if (context.arg(0).equalsIgnoreCase("give") && context.args().length >= 3) {
                org.bukkit.entity.Player target = getServer().getPlayerExact(context.arg(1));
                if (target != null) launchpadService.give(target, context.arg(2));
            }
        }, context -> context.args().length == 0 ? List.of("menu", "give") : List.of());

        getServer().getPluginManager().registerEvents(joinQueueService, this);
        getServer().getPluginManager().registerEvents(new MenuListener(duelService, partyService, perkService, gemService, sapphireService, dailyRewardManager, fishingRewardManager, soulManager, duelLeaderboardService, launchpadService, warpManager, shopService, marketPlotManager), this);
        getServer().getPluginManager().registerEvents(launchpadService, this);
        getServer().getPluginManager().registerEvents(spawnProtectionService, this);
        getServer().getPluginManager().registerEvents(spawnService, this);
        getServer().getPluginManager().registerEvents(spawnZoneManager, this);
        getServer().getPluginManager().registerEvents(survivalService, this);
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
        getServer().getPluginManager().registerEvents(friendService, this);
        getServer().getPluginManager().registerEvents(socialTabService, this);
        getServer().getPluginManager().registerEvents(duelService, this);
        getServer().getPluginManager().registerEvents(commandSpyManager, this);
        getServer().getPluginManager().registerEvents(sellService, this);
        getServer().getPluginManager().registerEvents(shopService, this);
        getServer().getPluginManager().registerEvents(auctionHouseService, this);
        getServer().getPluginManager().registerEvents(afkZoneManager, this);
        getServer().getPluginManager().registerEvents(dungeonService, this);
        getServer().getPluginManager().registerEvents(marketProtectionListener, this);
        getServer().getPluginManager().registerEvents(shopChestManager, this);
        getServer().getPluginManager().registerEvents(fishingListener, this);
        getServer().getPluginManager().registerEvents(soulDropService, this);

        getServer().getPluginManager().registerEvents(zonePvpService, this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) { new SmpCoreExpansion(this, perkService, warpManager, spawnService, moneyService, sapphireService, partyService, dungeonService, friendService, socialTabService, chatFormatService).register(); new ThreeSmpCoreExpansion(this, perkService, warpManager, spawnService, moneyService, sapphireService, partyService, dungeonService, friendService, socialTabService, chatFormatService).register(); }
    }

    @Override
    public void onDisable() {
        if (particleManager != null) particleManager.shutdown();
        if (hologramManager != null) hologramManager.removeAll();
        if (spawnZoneManager != null) spawnZoneManager.reload();
        if (afkZoneManager != null) afkZoneManager.shutdown();
        if (fishingRewardManager != null) fishingRewardManager.shutdown();
        if (soulManager != null) soulManager.reload();
        if (clearLagManager != null) clearLagManager.shutdown();
        if (joinQueueService != null) joinQueueService.shutdown();
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
        if (hologramManager != null) hologramManager.reload();
        if (spawnZoneManager != null) spawnZoneManager.reload();
        if (afkZoneManager != null) afkZoneManager.reload();
        if (dailyRewardManager != null) dailyRewardManager.reload();
        if (clearLagManager != null) clearLagManager.reload();
        if (commandSpyManager != null) commandSpyManager.reload();
        if (dungeonService != null) dungeonService.reload();
        if (welcomeService != null) welcomeService.reload();
        if (joinQueueService != null) joinQueueService.reload();
        if (fishingRewardManager != null) fishingRewardManager.reload();
        if (soulManager != null) soulManager.reload();
        if (marketPlotManager != null) marketPlotManager.reload();
    }

    public LicenseManager getLicenseManager() {
        return licenseManager;
    }

    public Database getDatabase() {
        return database;
    }

    public PlayerDataRepository getRepository() {
        return repository;
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
        preserveExistingDuelMaps();
        for (String file : new String[]{
                "core/config.yml", "core/messages.yml", "core/help.yml", "core/join-queue.yml", "cosmetics/perks.yml", "cosmetics/prefixes.yml", "cosmetics/tags.yml", "cosmetics/colors.yml", "cosmetics/cosmetics.yml", "cosmetics/effects.yml",
                "economy/sapphires.yml", "economy/souls.yml", "gems/gems.yml", "gems/capsules.yml", "duels/duels.yml", "duels/kits.yml", "duels/maps.yml", "social/party.yml", "cosmetics/trims.yml",
                "duels/messages.yml", "menus/perks.yml", "menus/gems.yml", "menus/sapphires.yml", "menus/duels.yml", "menus/party.yml", "menus/dev.yml",
                "world/launchpads.yml", "world/warps.yml", "world/rtp.yml", "core/welcome.yml", "world/zonepvp.yml", "social/friends.yml", "license/license.yml", "dungeons/dungeons.yml", "dungeons/rooms.yml", "dungeons/templates.yml", "dungeons/traps.yml", "world/survival.yml", "economy/money.yml", "economy/sell.yml", "economy/shop.yml", "economy/auction-house.yml", "world/afk.yml", "world/clearlag.yml", "cosmetics/particles.yml", "admin/commandspy.yml", "cosmetics/badges.yml", "cosmetics/glow.yml", "world/holograms.yml", "admin/permissions.yml", "rewards/daily.yml", "menus/daily.yml"
                , "fishing/fishing.yml", "menus/fishing.yml", "menus/souls.yml", "world/market.yml", "menus/market.yml", "config.yml", "messages.yml", "gui.yml", "rewards.yml", "fishing.yml", "souls.yml", "market.yml"
        }) {
            saveResource(file, false);
        }
    }

    private void preserveExistingDuelMaps() {
        java.nio.file.Path mapsFile = new java.io.File(getDataFolder(), "duels/maps.yml").toPath();
        if (!java.nio.file.Files.exists(mapsFile)) return;
        java.nio.file.Path backup = mapsFile.resolveSibling("maps.yml.backup");
        try {
            java.nio.file.Files.createDirectories(backup.getParent());
            java.nio.file.Files.copy(mapsFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        } catch (Exception ignored) {
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







