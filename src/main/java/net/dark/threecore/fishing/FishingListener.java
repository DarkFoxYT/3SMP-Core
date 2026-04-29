package net.dark.threecore.fishing;

import net.dark.threecore.dungeons.DungeonService;
import net.dark.threecore.duels.DuelService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
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
        if (!rewardManager.isFishingRod(player.getInventory().getItemInMainHand()) && !rewardManager.isFishingRod(player.getInventory().getItemInOffHand())) return;
        if (event.getState() == PlayerFishEvent.State.FISHING && event.getHook() != null) {
            watchHookLanding(player, event.getHook());
            return;
        }
        if (event.getState() == PlayerFishEvent.State.BITE && event.getHook() != null && isWaterHook(event.getHook())) {
            event.setCancelled(true);
            openForCaster(player, event.getHook());
        }
    }

    private void watchHookLanding(Player player, org.bukkit.entity.FishHook hook) {
        new BukkitRunnable() {
            private int ticks;
            private int waterTicks;

            @Override
            public void run() {
                if (!player.isOnline() || hook.isDead() || !hook.isValid() || rewardManager.isActive(player)) {
                    cancel();
                    return;
                }
                if (isWaterHook(hook)) {
                    if (++waterTicks >= 2) {
                        openForCaster(player, hook);
                        cancel();
                        return;
                    }
                } else {
                    waterTicks = 0;
                }
                if (++ticks > 100) cancel();
            }
        }.runTaskTimer(rewardManager.plugin(), 1L, 1L);
    }

    private void openForCaster(Player player, org.bukkit.entity.FishHook hook) {
        if (rewardManager.isActive(player)) return;
        hook.remove();
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.25f);
        rewardManager.open(player);
    }

    private boolean isWaterHook(org.bukkit.entity.FishHook hook) {
        var location = hook.getLocation();
        Material hit = location.getBlock().getType();
        Material below = location.clone().subtract(0.0D, 0.45D, 0.0D).getBlock().getType();
        Material deepBelow = location.clone().subtract(0.0D, 1.05D, 0.0D).getBlock().getType();
        return hit == Material.WATER || below == Material.WATER || deepBelow == Material.WATER || hook.isInWater();
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
