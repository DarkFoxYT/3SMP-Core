package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.engine.PlacedDungeonRoom;
import net.dark.threecore.dungeons.runtime.DungeonSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonRoomClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonSession session;
    private final PlacedDungeonRoom room;
    public DungeonRoomClearEvent(DungeonSession session, PlacedDungeonRoom room) { this.session = session; this.room = room; }
    public DungeonSession session() { return session; }
    public PlacedDungeonRoom room() { return room; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
