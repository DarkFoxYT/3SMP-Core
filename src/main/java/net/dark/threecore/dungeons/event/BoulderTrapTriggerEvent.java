package net.dark.threecore.dungeons.event;

import net.dark.threecore.dungeons.boulder.BoulderTrapService;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BoulderTrapTriggerEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final BoulderTrapService.Instance instance;
    private final Player player;
    public BoulderTrapTriggerEvent(BoulderTrapService.Instance instance, Player player) { this.instance = instance; this.player = player; }
    public BoulderTrapService.Instance instance() { return instance; }
    public Player player() { return player; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
