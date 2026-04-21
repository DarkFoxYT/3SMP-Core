package net.dark.threecore.dungeons;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public final class DungeonListener implements Listener {
    private final DungeonManager manager;

    public DungeonListener(DungeonManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.giveMenuItem(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item != null && manager.items().isMenuItem(item)) {
            event.setCancelled(true);
            if (manager.hasActiveDungeon(event.getPlayer())) {
                event.getPlayer().sendMessage(ChatColor.RED + "You cannot use the dungeon selector while a dungeon is active.");
                return;
            }
            manager.openMenu(event.getPlayer());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(DungeonMenu.TITLE) && !title.equals(DungeonMenu.LEVELS_TITLE)) return;
        event.setCancelled(true);
        if (manager.hasActiveDungeon(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use the selector while a dungeon is active.");
            player.closeInventory();
            return;
        }
        manager.handleMenuClick(player, event.getRawSlot(), title);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.items().isMenuItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (manager.shouldProtectDungeonBlocks(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (manager.shouldProtectDungeonBlocks(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        manager.handleMove(event);
    }
}