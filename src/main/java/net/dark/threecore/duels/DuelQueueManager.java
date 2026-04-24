package net.dark.threecore.duels;

import net.dark.threecore.duels.model.DuelKit;
import net.dark.threecore.duels.model.DuelMode;
import net.dark.threecore.duels.model.DuelQueueEntry;

import java.util.*;

public final class DuelQueueManager {
    private final Map<UUID, DuelQueueEntry> queueByPlayer = new HashMap<>();
    private final Map<String, Deque<DuelQueueEntry>> queues = new HashMap<>();

    public void join(UUID uuid, DuelMode mode, String kitId, UUID partyId) {
        DuelQueueEntry entry = new DuelQueueEntry(uuid, mode, kitId, partyId, System.currentTimeMillis());
        queueByPlayer.put(uuid, entry);
        queues.computeIfAbsent(mode.name() + ":" + kitId.toLowerCase(Locale.ROOT), ignored -> new ArrayDeque<>()).add(entry);
    }

    public void leave(UUID uuid) {
        DuelQueueEntry entry = queueByPlayer.remove(uuid);
        if (entry == null) return;
        Deque<DuelQueueEntry> queue = queues.get(entry.mode().name() + ":" + entry.kitId().toLowerCase(Locale.ROOT));
        if (queue != null) queue.removeIf(existing -> existing.id().equals(entry.id()));
    }

    public DuelQueueEntry entry(UUID uuid) {
        return queueByPlayer.get(uuid);
    }

    public Collection<DuelQueueEntry> entries() {
        return Collections.unmodifiableCollection(queueByPlayer.values());
    }
}
