package net.dark.threecore.duels.event;

import net.dark.threecore.duels.model.DuelMatch;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class DuelGateFullyOpenedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DuelMatch match;

    public DuelGateFullyOpenedEvent(DuelMatch match) {
        this.match = match;
    }

    public DuelMatch match() {
        return match;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
