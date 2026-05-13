package net.dark.threecore.dungeons.boulder;

import org.bukkit.util.Vector;

import java.util.List;

public record BoulderTrapDefinition(
        String id,
        Vector spawn,
        float yaw,
        float pitch,
        Vector triggerMin,
        Vector triggerMax,
        List<Vector> path,
        double speed,
        double acceleration,
        double maxSpeed,
        double killRadius,
        double verticalRadius,
        boolean destroyAtEnd,
        String mythicMobId,
        String modelEngineId
) {
    public BoulderTrapDefinition {
        spawn = spawn == null ? new Vector() : spawn.clone();
        triggerMin = triggerMin == null ? spawn.clone() : triggerMin.clone();
        triggerMax = triggerMax == null ? spawn.clone() : triggerMax.clone();
        path = path == null ? List.of() : path.stream().map(Vector::clone).toList();
        speed = Math.max(0.01D, speed);
        acceleration = Math.max(0.0D, acceleration);
        maxSpeed = Math.max(speed, maxSpeed);
        killRadius = Math.max(0.2D, killRadius);
        verticalRadius = Math.max(0.2D, verticalRadius);
        mythicMobId = mythicMobId == null ? "" : mythicMobId;
        modelEngineId = modelEngineId == null ? "" : modelEngineId;
    }
}
