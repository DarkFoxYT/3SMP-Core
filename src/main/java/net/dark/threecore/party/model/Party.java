package net.dark.threecore.party.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {
    private final UUID owner;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> invites = new LinkedHashSet<>();

    public Party(UUID owner) {
        this.owner = owner;
        this.members.add(owner);
    }

    public UUID owner() { return owner; }
    public Set<UUID> members() { return members; }
    public Set<UUID> invites() { return invites; }
}
