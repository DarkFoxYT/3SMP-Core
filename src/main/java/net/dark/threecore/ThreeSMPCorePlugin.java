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
import net.dark.threecore.command.CommandRestrictionService;
import net.dark.threecore.command.CommandRegistrar;
import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.placeholder.SmpCoreExpansion;
import net.dark.threecore.placeholder.ThreeSmpCoreExpansion;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.crates.CrateService;
import net.dark.threecore.data.Database;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.duels.DuelLeaderboardService;
import net.dark.threecore.daily.DailyRewardManager;
import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.welcome.WelcomeService;
import net.dark.threecore.joinqueue.JoinQueueService;
import net.dark.threecore.essentials.BackLocationService;
import net.dark.threecore.essentials.BackpackService;
import net.dark.threecore.essentials.EssentialCommandService;
import net.dark.threecore.items.ItemPowerService;
import net.dark.threecore.items.VeilcutterService;
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
import net.dark.threecore.logging.TrapCommandLogSilencer;
import net.dark.threecore.mythic.TrapMythicMechanics;
import net.dark.threecore.zonepvp.ZonePvpService;
import net.dark.threecore.social.FriendService;
import net.dark.threecore.social.SocialTabService;
import net.dark.threecore.visual.VisualManager;
import net.dark.threecore.screentext.ScreenTextManager;
import net.dark.threecore.screentext.ScreenTextPlaceholderExpansion;
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
import net.dark.threecore.survival.ThirdLifeService;
import net.dark.threecore.ranks.RankService;
import org.bukkit.GameRule;
import org.bukkit.World;
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
    private BackLocationService backLocationService;
    private BackpackService backpackService;
    private CommandRestrictionService commandRestrictionService;
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
    private VisualManager visualManager;
    private ScreenTextManager screenTextManager;
    private TrapCommandLogSilencer trapCommandLogSilencer;
    private ThirdLifeService thirdLifeService;
    private RankService rankService;
    private VeilcutterService veilcutterService;
    private ItemPowerService itemPowerService;
    private CrateService crateService;

    @Override
    public void onEnable() {
        this.trapCommandLogSilencer = TrapCommandLogSilencer.install();
        silenceTrapCommandFeedback();
        saveDefaultFiles();

        this.licenseManager = new LicenseManager(this);
        licenseManager.ensureTemplate();
        if (!licenseManager.validate()) {
            getLogger().severe("3SMPCore license validation failed. Gameplay systems will not be enabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        licenseManager.startRemoteMonitor();

        this.configs = new ConfigFiles(this);
        this.rankService = new RankService(this, configs);
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
        this.thirdLifeService = new ThirdLifeService(this);
        this.veilcutterService = new VeilcutterService(this);
        this.itemPowerService = new ItemPowerService(this);
        this.crateService = new CrateService(this, configs);
        this.sellService = new SellService(this, configs, moneyService);
        this.shopService = new ShopService(this, configs, moneyService);
        this.auctionHouseService = new AuctionHouseService(this, configs, moneyService);
        this.dailyRewardManager = new DailyRewardManager(this, configs, database, menuService, moneyService);
        this.fishingRewardManager = null;
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
        this.fishingListener = null;
        this.socialTabService = new SocialTabService(this, configs, chatFormatService);
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
        this.backLocationService = new BackLocationService(this, configs);
        this.backpackService = new BackpackService(this, configs);
        this.commandRestrictionService = new CommandRestrictionService(this, configs);
        this.zonePvpService = new ZonePvpService(this, configs, repository, partyService);
        this.zonePvpService.setCosmeticsItemRefresher(perkService::giveCosmeticsItem);
        this.spawnZoneManager.zonePvpService(zonePvpService);
        this.soulDropService = new SoulDropService(this, configs, soulManager, duelService, dungeonService, zonePvpService);
        this.commandSpyManager = new CommandSpyManager(this, configs);
        this.glowManager = new GlowManager();
        this.hologramManager = new HologramManager(this, configs, repository);
        if (hologramManager != null) hologramManager.reload();
        this.visualManager = new VisualManager(this, configs);
        this.visualManager.duelService(duelService);
        this.chatFormatService.setVisualManager(visualManager);
        this.visualManager.start();
        this.screenTextManager = new ScreenTextManager(this, configs, moneyService);
        this.screenTextManager.start();
        this.crateService.start();
        this.rankService.start();
        this.commandManager = new CoreCommandManager(this, configs, rankService, perkService, sapphireService, gemService, chatFormatService, spawnService, launchpadService, commandSpyManager, warpManager, moneyService, clearLagManager, duelService, dungeonService, afkZoneManager, dailyRewardManager, soulManager, marketPlotManager, hologramManager);
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
        registerDirectCommand("visuals", context -> { if (context.sender() instanceof org.bukkit.entity.Player player && visualManager != null) visualManager.open(player); else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>"); }, context -> List.of());
        registerDirectCommand("devpanel", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) duelService.openDevMenu(player); else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>"); }, context -> List.of());
        registerDirectCommand("kiteditor", context -> { if (context.sender() instanceof org.bukkit.entity.Player player) duelService.openLoadoutEditor(player); else net.dark.threecore.text.Text.send(context.sender(), "<red>Players only.</red>"); }, context -> List.of());
        registerDirectCommand("veilcutter", veilcutterService::handle, veilcutterService::complete);
        registerDirectCommand("itempower", itemPowerService::handle, itemPowerService::complete);
        registerDirectCommand("crate", context -> crateService.handle(context), context -> crateService.complete(context.args()));
        registerDirectCommand("prophecycrate", context -> crateService.handle(context), context -> crateService.complete(context.args()));
        registerDirectCommand("storerank", context -> handleStoreRank(context), context -> rankService.rankIds());
        registerDirectCommand("storesap", context -> handleStoreSapphires(context), context -> List.of("give", "set", "remove", "take"));
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
        registerDirectCommand("fishdebug", context -> net.dark.threecore.text.Text.send(context.sender(), "<yellow>Fishing is disabled.</yellow>"), context -> List.of());
        registerDirectCommand("fishingdebug", context -> net.dark.threecore.text.Text.send(context.sender(), "<yellow>Fishing is disabled.</yellow>"), context -> List.of());
        registerDirectCommand("fishing", context -> net.dark.threecore.text.Text.send(context.sender(), "<yellow>Fishing is disabled.</yellow>"), context -> List.of());
        registerDirectCommand("market", context -> marketPlotManager.handle(context.sender(), context.args()), context -> marketPlotManager.complete(context.args()));
        registerDirectCommand("warp", context -> {
            if (context.sender() instanceof org.bukkit.entity.Player player) {
                if (context.args().length == 0) warpManager.open(player);
                else if (context.arg(0).equalsIgnoreCase("set") && context.args().length >= 2 && player.hasPermission("3smpcore.admin")) warpManager.setWarp(player, context.arg(1), player.getLocation());
                else warpManager.teleport(player, context.arg(0));
            }
        }, context -> context.args().length == 0 ? warpManager.ids() : List.of("set"));
        registerDirectCommand("back", context -> backLocationService.handle(context.sender()), context -> List.of());
        registerDirectCommand("backpack", context -> backpackService.handle(context.sender()), context -> List.of());
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
        getServer().getPluginManager().registerEvents(new MenuListener(duelService, partyService, perkService, gemService, sapphireService, dailyRewardManager, fishingRewardManager, soulManager, duelLeaderboardService, launchpadService, rtpManager, warpManager, shopService, marketPlotManager), this);
        getServer().getPluginManager().registerEvents(launchpadService, this);
        getServer().getPluginManager().registerEvents(spawnProtectionService, this);
        getServer().getPluginManager().registerEvents(spawnService, this);
        getServer().getPluginManager().registerEvents(spawnZoneManager, this);
        getServer().getPluginManager().registerEvents(survivalService, this);
        getServer().getPluginManager().registerEvents(thirdLifeService, this);
        getServer().getPluginManager().registerEvents(veilcutterService, this);
        getServer().getPluginManager().registerEvents(itemPowerService, this);
        getServer().getPluginManager().registerEvents(crateService, this);
        getServer().getPluginManager().registerEvents(backLocationService, this);
        getServer().getPluginManager().registerEvents(backpackService, this);
        getServer().getPluginManager().registerEvents(commandRestrictionService, this);
        getServer().getPluginManager().registerEvents(rtpManager, this);
        getServer().getPluginManager().registerEvents(gemAutoApplyListener, this);
        getServer().getPluginManager().registerEvents(perkService, this);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                syncParticle(event.getPlayer().getUniqueId());
            }
        }, this);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
                silenceTrapCommandFeedback(event.getWorld());
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
        if (fishingListener != null) getServer().getPluginManager().registerEvents(fishingListener, this);
        getServer().getPluginManager().registerEvents(soulDropService, this);
        getServer().getPluginManager().registerEvents(soulManager, this);
        if (getServer().getPluginManager().getPlugin("MythicMobs") != null) {
            getServer().getPluginManager().registerEvents(new TrapMythicMechanics(), this);
        }

        getServer().getPluginManager().registerEvents(zonePvpService, this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SmpCoreExpansion(this, perkService, warpManager, spawnService, moneyService, sapphireService, partyService, dungeonService, friendService, socialTabService, chatFormatService, duelService, visualManager).register();
            new ThreeSmpCoreExpansion(this, perkService, warpManager, spawnService, moneyService, sapphireService, partyService, dungeonService, friendService, socialTabService, chatFormatService, duelService, visualManager).register();
            new ScreenTextPlaceholderExpansion(this, screenTextManager).register();
        }
    }

    @Override
    public void onDisable() {
        if (trapCommandLogSilencer != null) trapCommandLogSilencer.shutdown();
        if (licenseManager != null) licenseManager.shutdown();
        if (particleManager != null) particleManager.shutdown();
        if (hologramManager != null) hologramManager.removeAll();
        if (visualManager != null) visualManager.shutdown();
        if (screenTextManager != null) screenTextManager.shutdown();
        if (crateService != null) crateService.shutdown();
        if (duelService != null) duelService.shutdown();
        if (partyService != null) partyService.shutdown();
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
        if (shopService != null) shopService.reload();
        if (clearLagManager != null) clearLagManager.reload();
        if (commandSpyManager != null) commandSpyManager.reload();
        if (dungeonService != null) dungeonService.reload();
        if (welcomeService != null) welcomeService.reload();
        if (joinQueueService != null) joinQueueService.reload();
        if (rankService != null) rankService.reload();
        if (fishingRewardManager != null) fishingRewardManager.reload();
        if (soulManager != null) soulManager.reload();
        if (marketPlotManager != null) marketPlotManager.reload();
        if (visualManager != null) visualManager.reload();
        if (screenTextManager != null) screenTextManager.reload();
        if (crateService != null) crateService.refresh();
    }

    private void silenceTrapCommandFeedback() {
        for (World world : getServer().getWorlds()) silenceTrapCommandFeedback(world);
    }

    private void silenceTrapCommandFeedback(World world) {
        if (world == null) return;
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, false);
        world.setGameRule(GameRule.COMMAND_BLOCK_OUTPUT, false);
        world.setGameRule(GameRule.LOG_ADMIN_COMMANDS, false);
    }

    public VisualManager visualManager() {
        return visualManager;
    }

    public ScreenTextManager screenTextManager() {
        return screenTextManager;
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

    private void handleStoreRank(CommandContext context) {
        if (!context.sender().hasPermission("3smpcore.rank.admin")) {
            net.dark.threecore.text.Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        if (context.args().length < 2) {
            net.dark.threecore.text.Text.send(context.sender(), "<yellow>/storerank [give|sub|remove] <player> <rank></yellow>");
            return;
        }
        StoreRankArgs parsed = parseStoreRankArgs(context.args());
        String playerName = parsed.playerName();
        String rank = parsed.rank();
        String mode = parsed.mode();
        if (mode.equals("remove") || mode.equals("take") || mode.equals("revoke")) {
            if (!rankService.remove(context.sender(), playerName, rank)) net.dark.threecore.text.Text.send(context.sender(), "<red>Unknown rank.</red>");
            return;
        }
        if (!rankService.deliverStore(context.sender(), mode, playerName, rank)) {
            net.dark.threecore.text.Text.send(context.sender(), "<red>Unknown rank.</red>");
            return;
        }
    }

    private void handleStoreSapphires(CommandContext context) {
        if (!context.sender().hasPermission("3smpcore.sapphires.admin")) {
            net.dark.threecore.text.Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        if (context.args().length < 2) {
            net.dark.threecore.text.Text.send(context.sender(), "<yellow>/storesap [give|set|remove] <player> <amount></yellow>");
            return;
        }
        StoreSapphireArgs parsed = parseStoreSapphireArgs(context.args());
        if (parsed.amount() <= 0L || parsed.playerName().isBlank()) {
            net.dark.threecore.text.Text.send(context.sender(), "<red>Invalid sapphire delivery. Use /storesap [give|set|remove] <player> <amount>.</red>");
            return;
        }
        sapphireService.executeConfigured(parsed.action(), context.sender(), parsed.playerName(), parsed.amount());
    }

    private StoreRankArgs parseStoreRankArgs(String[] args) {
        String mode = "give";
        java.util.List<String> values = new java.util.ArrayList<>();
        for (String raw : args) {
            String value = raw == null ? "" : raw.trim();
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            if (isRankMode(lower)) mode = lower;
            else if (!value.isBlank()) values.add(value);
        }
        String playerName = values.isEmpty() ? "" : values.get(0);
        String rank = values.size() >= 2 ? values.get(1).toLowerCase(java.util.Locale.ROOT) : "";
        if (!rankService.rankIds().contains(rank) && rankService.rankIds().contains(playerName.toLowerCase(java.util.Locale.ROOT))) {
            String swapped = playerName;
            playerName = values.size() >= 2 ? values.get(1) : "";
            rank = swapped.toLowerCase(java.util.Locale.ROOT);
        }
        return new StoreRankArgs(playerName, rank, mode);
    }

    private StoreSapphireArgs parseStoreSapphireArgs(String[] args) {
        String action = "give";
        String playerName = "";
        long amount = 0L;
        for (String raw : args) {
            String value = raw == null ? "" : raw.trim();
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            if (isSapphireAction(lower)) {
                action = lower;
                continue;
            }
            long parsedAmount = sapphireService.parseAmountInput(value);
            if (parsedAmount > 0L && amount <= 0L) {
                amount = parsedAmount;
                continue;
            }
            if (playerName.isBlank() && !value.isBlank()) playerName = value;
        }
        return new StoreSapphireArgs(playerName, amount, action);
    }

    private boolean isRankMode(String value) {
        return value.equals("give") || value.equals("sub") || value.equals("subscription") || value.equals("subscriptions") || value.equals("remove") || value.equals("take") || value.equals("revoke");
    }

    private boolean isSapphireAction(String value) {
        return value.equals("give") || value.equals("add") || value.equals("grant") || value.equals("set") || value.equals("remove") || value.equals("take") || value.equals("reset");
    }

    private record StoreRankArgs(String playerName, String rank, String mode) {}
    private record StoreSapphireArgs(String playerName, long amount, String action) {}

    private void saveDefaultFiles() {
        preserveExistingDuelMaps();
        for (String file : new String[]{
                "core/config.yml", "core/messages.yml", "core/help.yml", "core/join-queue.yml", "cosmetics/perks.yml", "cosmetics/prefixes.yml", "cosmetics/tags.yml", "cosmetics/colors.yml", "cosmetics/cosmetics.yml", "cosmetics/effects.yml", "cosmetics/join_quit_messages.yml",
                "economy/sapphires.yml", "economy/souls.yml", "gems/gems.yml", "gems/capsules.yml", "duels/duels.yml", "duels/kits.yml", "duels/maps.yml", "social/party.yml", "cosmetics/trims.yml",
                "duels/messages.yml", "menus/perks.yml", "menus/gems.yml", "menus/sapphires.yml", "menus/duels.yml", "menus/deluxemenus/sap_vault.yml", "menus/deluxemenus/sap_vault_shop.yml", "menus/party.yml", "menus/dev.yml", "crates/crates.yml",
                "world/launchpads.yml", "world/warps.yml", "world/rtp.yml", "core/welcome.yml", "world/zonepvp.yml", "social/friends.yml", "social/visual-players.yml", "itemsadder/ranks.yml", "itemsadder/prophecy_crate.yml", "screen/screen-texts.yml", "dungeons/dungeons.yml", "dungeons/rooms.yml", "dungeons/templates.yml", "dungeons/traps.yml", "world/survival.yml", "economy/money.yml", "economy/sell.yml", "economy/shop.yml", "economy/auction-house.yml", "world/afk.yml", "world/clearlag.yml", "cosmetics/particles.yml", "admin/commandspy.yml", "cosmetics/badges.yml", "cosmetics/glow.yml", "world/holograms.yml", "world/npcs.yml", "admin/permissions.yml", "rewards/daily.yml", "menus/daily.yml"
                , "admin/ranks.yml", "menus/souls.yml", "world/market.yml", "menus/market.yml", "config.yml", "messages.yml", "gui.yml", "rewards.yml", "souls.yml", "market.yml"
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







