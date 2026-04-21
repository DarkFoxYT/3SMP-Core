package net.dark.threecore.duels.model;

import java.util.Set;
import java.util.UUID;

public record DuelMatch(UUID id, DuelMode mode, String kitId, String mapId, Set<UUID> teamOne, Set<UUID> teamTwo, long startedAt) {
}
