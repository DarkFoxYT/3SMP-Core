package net.dark.threecore.duels.model;

import java.util.UUID;

public record DuelQueueEntry(UUID id, DuelMode mode, String kitId, UUID partyId, long joinedAt) {
}
