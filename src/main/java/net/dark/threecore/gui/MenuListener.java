package net.dark.threecore.gui;

import net.dark.threecore.duels.DuelLeaderboardService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.daily.DailyRewardManager;
import net.dark.threecore.fishing.FishingRewardManager;
import net.dark.threecore.gems.GemService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.launchpads.LaunchpadService;
import net.dark.threecore.rtp.RtpManager;
import net.dark.threecore.warp.WarpManager;
import net.dark.threecore.shop.ShopService;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import net.dark.threecore.souls.SoulManager;
import net.dark.threecore.market.MarketPlotManager;
import net.dark.threecore.gui.menu.CoreMenuType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class MenuListener implements Listener {
    private final DuelService duelService;
    private final PartyService partyService;
    private final PerkService perkService;
    private final GemService gemService;
    private final SapphireService sapphireService;
    private final DailyRewardManager dailyRewardManager;
    private final FishingRewardManager fishingRewardManager;
    private final SoulManager soulManager;
    private final DuelLeaderboardService leaderboardService;
    private final LaunchpadService launchpadService;
    private final RtpManager rtpManager;
    private final WarpManager warpManager;
    private final ShopService shopService;
    private final MarketPlotManager marketPlotManager;

    public MenuListener(DuelService duelService, PartyService partyService, PerkService perkService, GemService gemService, SapphireService sapphireService, DailyRewardManager dailyRewardManager, FishingRewardManager fishingRewardManager, SoulManager soulManager, DuelLeaderboardService leaderboardService, LaunchpadService launchpadService, RtpManager rtpManager, WarpManager warpManager, ShopService shopService, MarketPlotManager marketPlotManager) {
        this.duelService = duelService;
        this.partyService = partyService;
        this.perkService = perkService;
        this.gemService = gemService;
        this.sapphireService = sapphireService;
        this.dailyRewardManager = dailyRewardManager;
        this.fishingRewardManager = fishingRewardManager;
        this.soulManager = soulManager;
        this.leaderboardService = leaderboardService;
        this.launchpadService = launchpadService;
        this.rtpManager = rtpManager;
        this.warpManager = warpManager;
        this.shopService = shopService;
        this.marketPlotManager = marketPlotManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CoreMenuHolder holder)) return;
        if (holder.type() == CoreMenuType.DUEL_DEV && holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("kit-editor:")) { duelService.handleKitEditorClick(event); return; }
        if (holder.type() == CoreMenuType.DUEL_LOADOUT) { duelService.handleLoadoutClick(event); return; }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem();
        int slot = event.getRawSlot();
        switch (holder.type()) {
            case DUEL_MAIN -> {
                if (holder.context().equalsIgnoreCase("summary")) duelService.handleSummaryClick(player, slot);
                else duelService.handleMainMenuClick(player, slot);
            }
            case DUEL_KITS -> duelService.handleKitMenuClick(player, slot);
            case DUEL_SPECTATE -> duelService.handleSpectatorMenuClick(player, slot);
            case DUEL_DEV -> {
                if (holder.context().equalsIgnoreCase("map-editor")) duelService.handleMapEditorClick(player, slot);
                else if (holder.context().equalsIgnoreCase("kit-selector")) duelService.handleKitEditorSelectorClick(player, slot);
                else if (holder.context().equalsIgnoreCase("arena-selector")) duelService.handleArenaSelectorClick(player, slot);
                else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("launchpad-direction:") && launchpadService != null) launchpadService.handleDirectionClick(player, holder.context().substring("launchpad-direction:".length()), slot);
                else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("launchpad:") && launchpadService != null) launchpadService.handleDetailClick(player, holder.context().substring("launchpad:".length()), slot);
                else if (holder.context().equalsIgnoreCase("launchpads") && launchpadService != null) launchpadService.handleMenuClick(player, slot);
                else if (holder.context().equalsIgnoreCase("warps") && warpManager != null) warpManager.handleClick(player, slot);
                else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("shop:") && shopService != null) shopService.handleClick(player, holder.context(), slot, event.getClick());
                else duelService.handleDevMenuClick(player, slot);
            }
            case DUEL_LEADERBOARD -> leaderboardService.handleClick(player, slot);
            case PARTY_MAIN -> {
                if (item == null) return;
                if (holder.context().equalsIgnoreCase("summary")) partyService.handleSummaryClick(player, slot);
                else if (holder.context().equalsIgnoreCase("party-duel")) partyService.handlePartyDuelClick(player, slot);
                else if (holder.context().equalsIgnoreCase("party-duel-kits")) partyService.handlePartyDuelKitPickerClick(player, slot);
                else if (holder.context().equalsIgnoreCase("party-duel-maps")) partyService.handlePartyDuelMapPickerClick(player, slot);
                else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("party-duel-members:")) partyService.handlePartyDuelMemberPickerClick(player, holder.context(), slot);
                else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("invite:")) partyService.handleInvitePickerClick(player, holder.context(), slot);
                else if (holder.context().equalsIgnoreCase("members")) partyService.handleMembersClick(player, slot);
                else partyService.handleMenuClick(player, slot);
            }
            case PERKS_MAIN -> { if (item == null) return; if (holder.context().equalsIgnoreCase("summary")) perkService.handleSummaryMenuClick(player, slot); else perkService.handleMenuClick(player, slot); }
            case PERKS_CATEGORY -> { if (item == null) return; perkService.handleCategoryClick(player, holder.context(), slot); }
            case GEMS_MAIN -> {
                if (item == null) return;
                String ctx = holder.context().toLowerCase(java.util.Locale.ROOT);
                if (ctx.equals("combine")) gemService.handleCombineClick(player, slot);
                else if (ctx.equals("browse")) gemService.handleBrowseClick(player, slot);
                else if (ctx.equals("capsules")) gemService.handleCapsuleClick(player, slot);
                else gemService.handleMenuClick(player, slot);
            }
            case GEMS_STATS -> { if (item == null) return; gemService.handleStatsClick(player, slot); }
            case SAPPHIRES_MAIN -> {
                if (item == null) return;
                if (holder.context().equalsIgnoreCase("summary")) sapphireService.handleSummaryClick(player, slot);
                else sapphireService.handleMenuClick(player, holder.context(), slot);
            }
            case DAILY_MAIN -> { if (item == null) return; dailyRewardManager.handleClick(player, slot); }
            case FISHING_MAIN -> { if (item == null || fishingRewardManager == null) return; fishingRewardManager.handleClick(player, slot); }
            case SOULS_MAIN -> {
                if (item == null) return;
                String ctx = holder.context().toLowerCase(java.util.Locale.ROOT);
                if (ctx.equals("souls-forge")) { soulManager.handleForgeClick(event); return; }
                if (ctx.equals("souls-rewards")) soulManager.handleRewardsClick(player, slot);
                else soulManager.handleClick(player, slot);
            }
            case RTP_MAIN -> { if (rtpManager != null) rtpManager.handleClick(player, slot); }
            case MARKET_MAIN -> { if (item == null) return; marketPlotManager.handle(player, slot); }
            case SHOP_CHEST -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CoreMenuHolder holder)) return;
        if (holder.type() == CoreMenuType.DUEL_DEV && holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("kit-editor:")) {
            duelService.handleKitEditorDrag(event);
            return;
        }
        if (holder.type() == CoreMenuType.DUEL_LOADOUT) {
            duelService.handleLoadoutDrag(event);
            return;
        }
        event.setCancelled(true);
    }
}



