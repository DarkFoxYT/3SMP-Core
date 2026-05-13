package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.engine.PlacedDungeonRoom;
import net.dark.threecore.dungeons.runtime.DungeonSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonRoomEnterEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonSession session;
    private final Player player;
    private final PlacedDungeonRoom room;
    public DungeonRoomEnterEvent(DungeonSession session, Player player, PlacedDungeonRoom room) { this.session = session; this.player = player; this.room = room; }
    public DungeonSession session() { return session; }
    public Player player() { return player; }
    public PlacedDungeonRoom room() { return room; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
