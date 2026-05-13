package net.dark.threecore.duels.event;

import net.dark.threecore.duels.model.DuelMatch;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;
import java.util.UUID;

public final class DuelRoundWinEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final DuelMatch match;
    private final Set<UUID> winners;

    public DuelRoundWinEvent(DuelMatch match, Set<UUID> winners) {
        this.match = match;
        this.winners = winners == null ? Set.of() : Set.copyOf(winners);
    }

    public DuelMatch match() {
        return match;
    }

    public Set<UUID> winners() {
        return winners;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
