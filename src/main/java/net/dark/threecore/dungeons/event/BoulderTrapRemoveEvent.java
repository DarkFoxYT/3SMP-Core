package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.boulder.BoulderTrapService;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BoulderTrapRemoveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BoulderTrapService.Instance instance;
    public BoulderTrapRemoveEvent(BoulderTrapService.Instance instance) { this.instance = instance; }
    public BoulderTrapService.Instance instance() { return instance; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
