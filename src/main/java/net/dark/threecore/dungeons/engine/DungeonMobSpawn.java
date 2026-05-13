package net.dark.threecore.dungeons.engine;

import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;

public record DungeonMobSpawn(
    String id,
    Vector localPosition,
    int amount,
    int level,
    MobTrigger trigger,
    EntityType fallback
) {
    public DungeonMobSpawn {
        amount = Math.max(1, amount);
        level = Math.max(1, level);
        trigger = trigger == null ? MobTrigger.ON_ROOM_ENTER : trigger;
        fallback = fallback == null ? EntityType.ZOMBIE : fallback;
    }
}
