package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.engine.DungeonLayout;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonGenerateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonLayout layout;
    public DungeonGenerateEvent(DungeonLayout layout) { this.layout = layout; }
    public DungeonLayout layout() { return layout; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
