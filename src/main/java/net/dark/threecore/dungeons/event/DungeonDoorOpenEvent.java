package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.runtime.DungeonDoor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DungeonDoorOpenEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DungeonDoor door;
    public DungeonDoorOpenEvent(DungeonDoor door) { this.door = door; }
    public DungeonDoor door() { return door; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
