package net.dark.threecore.dungeons;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DungeonSession {
    public final UUID ownerId;
    public final DungeonLevel level;
    public final DungeonDifficulty difficulty;
    public final World world;
    public final Location origin;
    public final List<Location> spawns = new ArrayList<>();
    public final List<Location> triggers = new ArrayList<>();
    public final List<Location> exits = new ArrayList<>();
    public final List<RoomInstance> rooms = new ArrayList<>();
    public final List<String> pendingRoomIds = new ArrayList<>();
    public final Set<String> insideTriggers = new HashSet<>();
    public Location starterCenter;
    public Location returnLocation;
    public long startTick;
    public double starterRadius;
    public long freezeUntilTick;
    public boolean started;
    public boolean completed;
    public Scoreboard scoreboard;
    public Objective objective;
    public int roomCount;
    public int clearedRooms;
    public int totalCombatRooms;
    public int generatedCombatRooms;

    public DungeonSession(UUID ownerId, DungeonLevel level, DungeonDifficulty difficulty, World world, Location origin) {
        this.ownerId = ownerId;
        this.level = level;
        this.difficulty = difficulty;
        this.world = world;
        this.origin = origin;
    }

    public boolean isFrozen(long tick) {
        return tick < freezeUntilTick;
    }

    public static final class RoomInstance {
        public final Location origin;
        public final Location min;
        public final Location max;
        public final List<DoorMarker> doors;
        public final List<Location> enemySpawns;
        public final boolean bossRoom;
        public final Set<UUID> activeMobs = new HashSet<>();
        public boolean activated;
        public boolean cleared;

        public RoomInstance(Location origin, Location min, Location max, List<DoorMarker> doors, List<Location> enemySpawns, boolean bossRoom) {
            this.origin = origin;
            this.min = min;
            this.max = max;
            this.doors = doors;
            this.enemySpawns = enemySpawns;
            this.bossRoom = bossRoom;
        }

        public boolean contains(Location location) {
            return location.getX() >= min.getX() && location.getX() <= max.getX() + 1
                && location.getY() >= min.getY() && location.getY() <= max.getY() + 1
                && location.getZ() >= min.getZ() && location.getZ() <= max.getZ() + 1;
        }

        public boolean hasLivingMobs() {
            for (UUID uuid : activeMobs) {
                Entity entity = origin.getWorld().getEntity(uuid);
                if (entity != null && entity.isValid() && !entity.isDead()) {
                    return true;
                }
            }
            return false;
        }
    }

    public record DoorMarker(Location location, org.bukkit.block.BlockFace facing) {}
}
