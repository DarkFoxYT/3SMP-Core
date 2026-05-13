package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.boulder.BoulderTrapService;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BoulderTrapKillEntityEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BoulderTrapService.Instance instance;
    private final Entity entity;
    public BoulderTrapKillEntityEvent(BoulderTrapService.Instance instance, Entity entity) { this.instance = instance; this.entity = entity; }
    public BoulderTrapService.Instance instance() { return instance; }
    public Entity entity() { return entity; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
