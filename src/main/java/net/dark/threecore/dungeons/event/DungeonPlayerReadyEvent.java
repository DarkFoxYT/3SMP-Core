package net.dark.threecore.dungeons.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonPlayerReadyEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final boolean ready;
    public DungeonPlayerReadyEvent(Player player, boolean ready) { this.player = player; this.ready = ready; }
    public Player player() { return player; }
    public boolean ready() { return ready; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
