package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.runtime.DungeonSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonSession session;
    public DungeonEndEvent(DungeonSession session) { this.session = session; }
    public DungeonSession session() { return session; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
