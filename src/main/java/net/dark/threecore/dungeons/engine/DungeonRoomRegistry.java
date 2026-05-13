package net.dark.threecore.dungeons.engine;

import net.dark.threecore.config.ConfigFiles;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class DungeonRoomRegistry {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final Map<String, DungeonRoomDefinition> rooms = new java.util.concurrent.ConcurrentHashMap<>();
    private final Random random = new Random();

    public DungeonRoomRegistry(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
        reload();
    }

    public void reload() {
        configs.reload();
        rooms.clear();
        YamlConfiguration yaml = configs.get("dungeons/rooms.yml");
        ConfigurationSection section = yaml.getConfigurationSection("rooms");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            DungeonRoomDefinition room = load(id, section.getConfigurationSection(id));
            if (room != null) rooms.put(id.toLowerCase(Locale.ROOT), room);
        }
    }

    public DungeonRoomDefinition room(String id) {
        return id == null ? null : rooms.get(id.toLowerCase(Locale.ROOT));
    }

    public List<DungeonRoomDefinition> all() {
        return rooms.values().stream().sorted(java.util.Comparator.comparing(DungeonRoomDefinition::id)).toList();
    }

    public List<DungeonRoomDefinition> byType(RoomType type) {
        return rooms.values().stream().filter(room -> room.type() == type).toList();
    }

    public DungeonRoomDefinition pick(RoomType preferred) {
        List<DungeonRoomDefinition> pool = byType(preferred);
        if (pool.isEmpty() && preferred == RoomType.START) pool = byType(RoomType.NORMAL);
        if (pool.isEmpty() && preferred == RoomType.BOSS) pool = byType(RoomType.DEAD_END);
        if (pool.isEmpty()) pool = byType(RoomType.NORMAL);
        if (pool.isEmpty()) pool = all();
        if (pool.isEmpty()) return null;
        int total = pool.stream().mapToInt(DungeonRoomDefinition::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        int seen = 0;
        for (DungeonRoomDefinition room : pool) {
            seen += room.weight();
            if (roll < seen) return room;
        }
        return pool.get(0);
    }

    public void register(DungeonRoomDefinition definition) {
        if (definition != null) rooms.put(definition.id().toLowerCase(Locale.ROOT), definition);
    }

    public Map<RoomType, Long> countsByType() {
        Map<RoomType, Long> counts = new EnumMap<>(RoomType.class);
        for (RoomType type : RoomType.values()) counts.put(type, rooms.values().stream().filter(room -> room.type() == type).count());
        return counts;
    }

    private DungeonRoomDefinition load(String id, ConfigurationSection section) {
        if (section == null) return null;
        try {
            Vector size = vector(section.getConfigurationSection("size"), new Vector(32, 16, 32));
            if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
                throw new IllegalArgumentException("Invalid room size for " + id);
            }
            List<DungeonConnector> entrances = connectors(section.getConfigurationSection("entrances"), ConnectorRole.ENTRANCE);
            List<DungeonConnector> exits = connectors(section.getConfigurationSection("exits"), ConnectorRole.EXIT);
            exits.addAll(connectors(section.getConfigurationSection("vertical-connectors"), ConnectorRole.VERTICAL));
            RoomType type = enumValue(RoomType.class, section.getString("type", "NORMAL"), RoomType.NORMAL);
            DungeonRoomDefinition room = new DungeonRoomDefinition(
                id,
                section.getString("schematic", id + ".schem"),
                type,
                section.getInt("weight", 1),
                size,
                entrances,
                exits,
                new HashSet<>(section.getStringList("required-tags")),
                new HashSet<>(section.getStringList("blocked-tags")),
                section.getInt("difficulty-weight", 1),
                spawns(section.getConfigurationSection("spawns.mobs")),
                traps(section.getConfigurationSection("traps")),
                boulderTraps(section.getConfigurationSection("boulder-traps")),
                enumValue(BlockFace.class, section.getString("base-facing", "SOUTH"), BlockFace.SOUTH),
                facingMarker(section.getConfigurationSection("facing-marker")),
                rotationOrigin(section.getConfigurationSection("rotation-origin"), size, section.getConfigurationSection("facing-marker"))
            );
            if (!validateConnectors(room, size)) {
                plugin.getLogger().warning("Skipping room " + id + " due to invalid connector metadata.");
                return null;
            }
            return room;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load dungeon room " + id + ": " + ex.getMessage());
            return null;
        }
    }

    private List<DungeonConnector> connectors(ConfigurationSection section, ConnectorRole role) {
        List<DungeonConnector> out = new ArrayList<>();
        if (section == null) return out;
        for (String id : section.getKeys(false)) {
            ConfigurationSection connector = section.getConfigurationSection(id);
            if (connector == null) continue;
            List<Integer> pos = connector.getIntegerList("pos");
            Vector vector = new Vector(pos.size() > 0 ? pos.get(0) : 0, pos.size() > 1 ? pos.get(1) : 0, pos.size() > 2 ? pos.get(2) : 0);
            ConnectorType type = enumValue(ConnectorType.class, connector.getString("connector", "NORMAL"), ConnectorType.NORMAL);
            List<Integer> anchor = connector.getIntegerList("anchor-pos");
            Vector anchorPos = new Vector(anchor.size() > 0 ? anchor.get(0) : vector.getBlockX(), anchor.size() > 1 ? anchor.get(1) : vector.getBlockY(), anchor.size() > 2 ? anchor.get(2) : vector.getBlockZ());
            out.add(new DungeonConnector(
                id,
                role,
                vector,
                enumValue(BlockFace.class, connector.getString("facing", "NORTH"), BlockFace.NORTH),
                connector.getInt("width", 3),
                connector.getInt("height", 4),
                type,
                enumValue(ConnectorType.class, connector.getString("required-match", type.name()), type),
                connector.getBoolean("required", false),
                enumValue(DungeonConnector.VerticalDirection.class, connector.getString("vertical-direction", type == ConnectorType.STAIRS_DOWN ? "DOWN" : type == ConnectorType.STAIRS_UP ? "UP" : "NONE"), DungeonConnector.VerticalDirection.NONE),
                connector.getInt("target-y-offset", 0),
                enumValue(DungeonConnector.SnapMode.class, connector.getString("snap-mode", type.vertical() ? "ANCHOR" : "CONNECTOR_FACE"), type.vertical() ? DungeonConnector.SnapMode.ANCHOR : DungeonConnector.SnapMode.CONNECTOR_FACE),
                anchorPos
            ));
        }
        return out;
    }

    private List<DungeonMobSpawn> spawns(ConfigurationSection section) {
        List<DungeonMobSpawn> out = new ArrayList<>();
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            ConfigurationSection spawn = section.isConfigurationSection(key) ? section.getConfigurationSection(key) : null;
            if (spawn == null) continue;
            List<Integer> pos = spawn.getIntegerList("pos");
            out.add(new DungeonMobSpawn(
                spawn.getString("id", key),
                new Vector(pos.size() > 0 ? pos.get(0) : 0, pos.size() > 1 ? pos.get(1) : 0, pos.size() > 2 ? pos.get(2) : 0),
                spawn.getInt("amount", 1),
                spawn.getInt("level", 1),
                enumValue(MobTrigger.class, spawn.getString("trigger", "ON_ROOM_ENTER"), MobTrigger.ON_ROOM_ENTER),
                enumValue(EntityType.class, spawn.getString("fallback", "ZOMBIE"), EntityType.ZOMBIE)
            ));
        }
        return out;
    }

    private List<DungeonTrapDefinition> traps(ConfigurationSection section) {
        List<DungeonTrapDefinition> out = new ArrayList<>();
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            ConfigurationSection trap = section.getConfigurationSection(key);
            if (trap == null || !trap.getBoolean("enabled", true)) continue;
            List<Integer> pos = trap.getIntegerList("pos");
            String type = trap.getString("type", key);
            String defaultMob = configs.get("dungeons/dungeons.yml").getString("traps.mythicmobs." + type.toLowerCase(Locale.ROOT), "");
            out.add(new DungeonTrapDefinition(
                key,
                type,
                new Vector(pos.size() > 0 ? pos.get(0) : 0, pos.size() > 1 ? pos.get(1) : 0, pos.size() > 2 ? pos.get(2) : 0),
                enumValue(BlockFace.class, trap.getString("facing", "SOUTH"), BlockFace.SOUTH),
                trap.getString("mythicmob-id", defaultMob)
            ));
        }
        return out;
    }

    private List<net.dark.threecore.dungeons.boulder.BoulderTrapDefinition> boulderTraps(ConfigurationSection section) {
        List<net.dark.threecore.dungeons.boulder.BoulderTrapDefinition> out = new ArrayList<>();
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            ConfigurationSection trap = section.getConfigurationSection(key);
            if (trap == null || !trap.getBoolean("enabled", true)) continue;
            ConfigurationSection spawnSection = trap.getConfigurationSection("spawn");
            List<Integer> spawn = spawnSection == null ? List.of() : spawnSection.getIntegerList("pos");
            ConfigurationSection trigger = trap.getConfigurationSection("trigger");
            List<Vector> path = new ArrayList<>();
            for (Object raw : trap.getList("path", List.of())) {
                if (raw instanceof List<?> list && list.size() >= 3) {
                    path.add(new Vector(intValue(list.get(0)), intValue(list.get(1)), intValue(list.get(2))));
                }
            }
            out.add(new net.dark.threecore.dungeons.boulder.BoulderTrapDefinition(
                    key,
                    new Vector(spawn.size() > 0 ? spawn.get(0) : 0, spawn.size() > 1 ? spawn.get(1) : 0, spawn.size() > 2 ? spawn.get(2) : 0),
                    spawnSection == null ? 0.0F : (float) spawnSection.getDouble("yaw", 0.0D),
                    spawnSection == null ? 0.0F : (float) spawnSection.getDouble("pitch", 0.0D),
                    vectorList(trigger == null ? List.of() : trigger.getIntegerList("min")),
                    vectorList(trigger == null ? List.of() : trigger.getIntegerList("max")),
                    path,
                    trap.getDouble("speed", 0.42D),
                    trap.getDouble("acceleration", 0.02D),
                    trap.getDouble("max-speed", 0.85D),
                    trap.getDouble("kill-radius", 1.6D),
                    trap.getDouble("vertical-radius", 1.8D),
                    trap.getBoolean("destroy-at-end", true),
                    trap.getString("mythicmob-id", ""),
                    trap.getString("modelengine-id", "")
            ));
        }
        return out;
    }

    private Vector vectorList(List<Integer> list) {
        return new Vector(list.size() > 0 ? list.get(0) : 0, list.size() > 1 ? list.get(1) : 0, list.size() > 2 ? list.get(2) : 0);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private DungeonRoomDefinition.FacingMarker facingMarker(ConfigurationSection section) {
        if (section == null) return null;
        List<Integer> pos = section.getIntegerList("pos");
        Vector vector = new Vector(pos.size() > 0 ? pos.get(0) : 0, pos.size() > 1 ? pos.get(1) : 0, pos.size() > 2 ? pos.get(2) : 0);
        return new DungeonRoomDefinition.FacingMarker(vector, enumValue(BlockFace.class, section.getString("facing", "SOUTH"), BlockFace.SOUTH));
    }

    private DungeonRoomDefinition.RotationOrigin rotationOrigin(ConfigurationSection section, Vector size, ConfigurationSection markerSection) {
        if (section == null) return DungeonRoomDefinition.RotationOrigin.roomMin();
        DungeonRoomDefinition.RotationOrigin.Mode mode = enumValue(DungeonRoomDefinition.RotationOrigin.Mode.class, section.getString("mode", "ROOM_MIN"), DungeonRoomDefinition.RotationOrigin.Mode.ROOM_MIN);
        List<Integer> pos = section.getIntegerList("pos");
        Vector vector;
        if (pos.size() >= 3) {
            vector = new Vector(pos.get(0), pos.get(1), pos.get(2));
        } else if (mode == DungeonRoomDefinition.RotationOrigin.Mode.ROOM_CENTER) {
            vector = new Vector(size.getX() / 2.0D, 0.0D, size.getZ() / 2.0D);
        } else if (mode == DungeonRoomDefinition.RotationOrigin.Mode.MARKER && markerSection != null) {
            List<Integer> markerPos = markerSection.getIntegerList("pos");
            vector = markerPos.size() >= 3 ? new Vector(markerPos.get(0), markerPos.get(1), markerPos.get(2)) : new Vector();
        } else {
            vector = new Vector();
        }
        return new DungeonRoomDefinition.RotationOrigin(mode, vector);
    }

    private Vector vector(ConfigurationSection section, Vector fallback) {
        if (section == null) return fallback;
        return new Vector(section.getInt("x", fallback.getBlockX()), section.getInt("y", fallback.getBlockY()), section.getInt("z", fallback.getBlockZ()));
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private boolean validateConnectors(DungeonRoomDefinition room, Vector size) {
        int roomWidth = Math.max(1, size.getBlockX());
        int roomHeight = Math.max(1, size.getBlockY());
        int roomLength = Math.max(1, size.getBlockZ());
        List<DungeonConnector> all = new ArrayList<>(room.entrances());
        all.addAll(room.exits());

        long entranceCount = all.stream().filter(connector -> connector.role() == ConnectorRole.ENTRANCE).count();
        long exitCount = all.stream().filter(connector -> connector.role() != ConnectorRole.ENTRANCE).count();
        if (room.type() == RoomType.START) {
            if (entranceCount != 0) {
                plugin.getLogger().warning("Room " + room.id() + " is START but has " + entranceCount + " entrances; expected 0.");
                return false;
            }
        } else {
            if (entranceCount < 1) {
                plugin.getLogger().warning("Room " + room.id() + " has " + entranceCount + " entrances; expected at least 1 for non-start rooms.");
                return false;
            }
        }
        if ((room.type() == RoomType.DEAD_END || room.type() == RoomType.CAP || room.type() == RoomType.END) && exitCount > 0) {
            plugin.getLogger().warning("Room " + room.id() + " is an ending room but has " + exitCount + " exits; expected 0.");
            return false;
        }

        for (DungeonConnector connector : all) {
            Vector p = connector.localPosition();
            if (p.getBlockX() < 0 || p.getBlockY() < 0 || p.getBlockZ() < 0 || p.getBlockX() >= roomWidth || p.getBlockY() >= roomHeight || p.getBlockZ() >= roomLength) {
                plugin.getLogger().warning("Room " + room.id() + " connector " + connector.id() + " is outside room bounds.");
                return false;
            }
            if (connector.facing() == null) {
                return false;
            }
            if (connector.facing() != org.bukkit.block.BlockFace.UP && connector.facing() != org.bukkit.block.BlockFace.DOWN) {
                switch (connector.facing()) {
                    case NORTH, SOUTH, EAST, WEST -> { }
                    default -> {
                        plugin.getLogger().warning("Room " + room.id() + " connector " + connector.id() + " has invalid facing.");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public List<String> deadEndConnectorRoomTypes() {
        YamlConfiguration yaml = configs.get("dungeons/dungeons.yml");
        var raw = yaml.getStringList("generation.room-engine.dead-end-room-types");
        if (raw == null || raw.isEmpty()) raw = yaml.getStringList("generation.room-engine.ending-room-types");
        if (raw == null || raw.isEmpty()) raw = yaml.getStringList("generation.dead-end-room-types");
        if (raw != null && !raw.isEmpty()) return raw;
        return List.of("DEAD_END", "TREASURE");
    }

    public DungeonGenerationSettings settings() {
        YamlConfiguration yaml = configs.get("dungeons/dungeons.yml");
        String path = "generation.room-engine.";
        DungeonGenerationSettings defaults = DungeonGenerationSettings.defaults();
        return new DungeonGenerationSettings(
            yaml.getInt(path + "max-attempts", defaults.maxAttempts()),
            yaml.getInt(path + "main-path-length.min", defaults.mainPathMin()),
            yaml.getInt(path + "main-path-length.max", defaults.mainPathMax()),
            yaml.getInt(path + "branch-count.min", defaults.branchMin()),
            yaml.getInt(path + "branch-count.max", defaults.branchMax()),
            yaml.getInt(path + "branch-length.min", defaults.branchLengthMin()),
            yaml.getInt(path + "branch-length.max", defaults.branchLengthMax()),
            yaml.getBoolean(path + "allow-vertical", defaults.allowVertical()),
            yaml.getInt(path + "max-y-level-difference", defaults.maxYLevelDifference()),
            yaml.getInt(path + "vertical.max-total-y-span", defaults.maxTotalYSpan()),
            yaml.getInt(path + "vertical.max-levels-up", defaults.maxVerticalUp()),
            yaml.getInt(path + "vertical.max-levels-down", defaults.maxVerticalDown()),
            yaml.getBoolean(path + "vertical.require-stair-connector-types", defaults.requireStairConnectorTypes()),
            yaml.getInt(path + "room-padding-blocks", defaults.roomPaddingBlocks()),
            Math.max(1, yaml.getInt(path + "connector-gap-blocks", defaults.connectorGapBlocks())),
            yaml.getBoolean(path + "allow-touching-at-connectors", defaults.allowTouchingAtConnectors()),
            yaml.getBoolean(path + "require-final-boss-room", defaults.requireFinalBossRoom()),
            yaml.getBoolean(path + "boss-room-required", defaults.bossRoomRequired()),
            yaml.getBoolean(path + "prevent-floating-connectors", defaults.preventFloatingConnectors()),
            yaml.getBoolean(path + "seal-open-optional-connectors", defaults.sealOpenOptionalConnectors()),
            yaml.getString(path + "required-open-connector-behavior", defaults.requiredOpenConnectorBehavior()),
            yaml.getString(path + "optional-open-connector-behavior", defaults.optionalOpenConnectorBehavior()),
            yaml.getBoolean(path + "paste.apply-physics", defaults.applyPhysics()),
            yaml.getBoolean(path + "paste.update-neighbors", defaults.updateNeighbors()),
            yaml.getBoolean(path + "paste.preserve-blockstates", defaults.preserveBlockstates()),
            yaml.getBoolean(path + "paste.preserve-block-entities", defaults.preserveBlockEntities()),
            yaml.getDouble(path + "treasure-room-chance", defaults.treasureChance()),
            yaml.getDouble(path + "mini-boss-room-chance", defaults.miniBossChance())
        );
    }
}
