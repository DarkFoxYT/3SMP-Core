package net.dark.threecore.fishing;

import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.duels.DuelService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class FishingListener implements Listener {
    private final FishingRewardManager rewardManager;
    private final DuelService duelService;
    private final DungeonService dungeonService;

    public FishingListener(FishingRewardManager rewardManager, DuelService duelService, DungeonService dungeonService) {
        this.rewardManager = rewardManager;
        this.duelService = duelService;
        this.dungeonService = dungeonService;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (duelService.isPlayerInDuel(player.getUniqueId()) || DuelService.isDuelPlayer(player)) return;
        if (dungeonService != null && DungeonService.isDungeonPlayer(player)) return;
        if (event.getState() != PlayerFishEvent.State.FISHING && event.getState() != PlayerFishEvent.State.BITE) return;
        if (!rewardManager.isFishingRod(player.getInventory().getItemInMainHand()) && !rewardManager.isFishingRod(player.getInventory().getItemInOffHand())) return;
        if (!isWaterCast(event)) return;
        event.setCancelled(true);
        if (event.getHook() != null) event.getHook().remove();
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f, 1.25f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) rewardManager.open(player);
            }
        }.runTaskLater(rewardManager.plugin(), 1L);
    }

    private boolean isWaterCast(PlayerFishEvent event) {
        if (event.getHook() == null) return false;
        Material hit = event.getHook().getLocation().getBlock().getType();
        Material below = event.getHook().getLocation().subtract(0.0D, 0.35D, 0.0D).getBlock().getType();
        return hit == Material.WATER || below == Material.WATER;
    }

    @EventHandler
    public void onRodUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (duelService.isPlayerInDuel(player.getUniqueId()) || DuelService.isDuelPlayer(player)) return;
        if (dungeonService != null && DungeonService.isDungeonPlayer(player)) return;
        if (!rewardManager.isFishingRod(event.getItem())) return;
        if (event.getClickedBlock() != null && !event.getClickedBlock().isLiquid()) return;
        event.setCancelled(true);
        player.swingMainHand();
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f, 1.25f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) rewardManager.open(player);
            }
        }.runTaskLater(rewardManager.plugin(), 1L);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (rewardManager.isActive(player) && rewardManager.handleClick(player, event.getRawSlot())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rewardManager.close(event.getPlayer());
    }
}
