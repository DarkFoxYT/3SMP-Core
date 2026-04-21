package net.dark.threecore.gui;

import net.dark.threecore.duels.DuelLeaderboardService;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.gems.GemService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.launchpads.LaunchpadService;
import net.dark.threecore.warp.WarpManager;
import net.dark.threecore.party.PartyService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.sapphires.SapphireService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class MenuListener implements Listener {
    private final DuelService duelService;
    private final PartyService partyService;
    private final PerkService perkService;
    private final GemService gemService;
    private final SapphireService sapphireService;
    private final DuelLeaderboardService leaderboardService;
    private final LaunchpadService launchpadService;
    private final WarpManager warpManager;

    public MenuListener(DuelService duelService, PartyService partyService, PerkService perkService, GemService gemService, SapphireService sapphireService, DuelLeaderboardService leaderboardService, LaunchpadService launchpadService, WarpManager warpManager) {
        this.duelService = duelService;
        this.partyService = partyService;
        this.perkService = perkService;
        this.gemService = gemService;
        this.sapphireService = sapphireService;
        this.leaderboardService = leaderboardService;
        this.launchpadService = launchpadService;
        this.warpManager = warpManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CoreMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        int slot = event.getRawSlot();
        switch (holder.type()) {
            case DUEL_MAIN -> {
                if (holder.context().equalsIgnoreCase("summary")) duelService.handleSummaryClick(player, slot);
                else duelService.handleMainMenuClick(player, slot);
            }
            case DUEL_KITS -> duelService.handleKitMenuClick(player, slot);
            case DUEL_DEV -> {
                if (holder.context().equalsIgnoreCase("map-editor")) duelService.handleMapEditorClick(player, slot);
                else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("launchpad-direction:") && launchpadService != null) launchpadService.handleDirectionClick(player, holder.context().substring("launchpad-direction:".length()), slot);
                else if (holder.context().toLowerCase(java.util.Locale.ROOT).startsWith("launchpad:") && launchpadService != null) launchpadService.handleDetailClick(player, holder.context().substring("launchpad:".length()), slot);
                else if (holder.context().equalsIgnoreCase("launchpads") && launchpadService != null) launchpadService.handleMenuClick(player, slot);
                else if (holder.context().equalsIgnoreCase("warps") && warpManager != null) warpManager.handleClick(player, slot);
                else duelService.handleDevMenuClick(player, slot);
            }
            case DUEL_LEADERBOARD -> leaderboardService.handleClick(player, slot);
            case PARTY_MAIN -> {
                if (holder.context().equalsIgnoreCase("summary")) partyService.handleSummaryClick(player, slot);
                else partyService.handleMenuClick(player, slot);
            }
            case PERKS_MAIN -> perkService.handleMenuClick(player, slot);
            case PERKS_CATEGORY -> perkService.handleCategoryClick(player, holder.context(), slot);
            case GEMS_MAIN -> {
                String ctx = holder.context().toLowerCase(java.util.Locale.ROOT);
                if (ctx.equals("combine")) gemService.handleCombineClick(player, slot);
                else if (ctx.equals("browse")) gemService.handleBrowseClick(player, slot);
                else gemService.handleMenuClick(player, slot);
            }
            case GEMS_STATS -> gemService.handleStatsClick(player, slot);
            case SAPPHIRES_MAIN -> {
                if (holder.context().equalsIgnoreCase("summary")) sapphireService.handleSummaryClick(player, slot);
                else sapphireService.handleMenuClick(player, slot);
            }
        }
    }
}
