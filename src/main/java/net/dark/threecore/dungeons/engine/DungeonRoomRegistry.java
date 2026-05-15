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
            if (type == RoomType.BOSS && exits.isEmpty() && !entrances.isEmpty()) {
                exits.add(inferredBossExit(entrances.get(0), size));
            }
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
                traps(id, section.getConfigurationSection("traps")),
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

    private DungeonConnector inferredBossExit(DungeonConnector entrance, Vector size) {
        int maxX = Math.max(1, size.getBlockX()) - 1;
        int maxZ = Math.max(1, size.getBlockZ()) - 1;
        int x = clamp(entrance.localPosition().getBlockX(), 0, maxX);
        int y = Math.max(0, entrance.localPosition().getBlockY());
        int z = clamp(entrance.localPosition().getBlockZ(), 0, maxZ);
        BlockFace facing = entrance.facing().getOppositeFace();
        switch (entrance.facing()) {
            case NORTH -> z = maxZ;
            case SOUTH -> z = 0;
            case EAST -> x = 0;
            case WEST -> x = maxX;
            default -> {
                z = 0;
                facing = BlockFace.NORTH;
            }
        }
        Vector pos = new Vector(x, y, z);
        return new DungeonConnector(
            "boss_exit",
            ConnectorRole.EXIT,
            pos,
            facing,
            entrance.width(),
            entrance.height(),
            ConnectorType.NORMAL,
            ConnectorType.NORMAL,
            true,
            DungeonConnector.VerticalDirection.NONE,
            0,
            DungeonConnector.SnapMode.CONNECTOR_FACE,
            pos
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<DungeonMobSpawn> spawns(ConfigurationSection section) {
        List<DungeonMobSpawn> out = new ArrayList<>();
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            ConfigurationSection spawn = section.isConfigurationSection(key) ? section.getConfigurationSection(key) : null;
            if (spawn == null) continue;
            Vector pos = numberVector(spawn.getList("pos", List.of()));
            out.add(new DungeonMobSpawn(
                spawn.getString("id", key),
                pos,
                spawn.getInt("amount", 1),
                spawn.getInt("level", 1),
                enumValue(MobTrigger.class, spawn.getString("trigger", "ON_ROOM_ENTER"), MobTrigger.ON_ROOM_ENTER),
                enumValue(EntityType.class, spawn.getString("fallback", "ZOMBIE"), EntityType.ZOMBIE)
            ));
        }
        return out;
    }

    private List<DungeonTrapDefinition> traps(String roomId, ConfigurationSection section) {
        List<DungeonTrapDefinition> out = new ArrayList<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection trap = section.getConfigurationSection(key);
                if (trap == null || !trap.getBoolean("enabled", true)) continue;
                Vector pos = numberVector(trap.getList("pos", List.of()));
                String type = trap.getString("type", key);
                String defaultMob = configs.get("dungeons/dungeons.yml").getString("traps.mythicmobs." + type.toLowerCase(Locale.ROOT), "");
                out.add(new DungeonTrapDefinition(
                    key,
                    type,
                    pos,
                    enumValue(BlockFace.class, trap.getString("facing", "SOUTH"), BlockFace.SOUTH),
                    trap.getString("mythicmob-id", defaultMob)
                ));
            }
        }
        addTemplateMarkerTraps(roomId, out);
        return out;
    }

    private void addTemplateMarkerTraps(String roomId, List<DungeonTrapDefinition> out) {
        if (roomId == null || roomId.isBlank()) return;
        YamlConfiguration templates = configs.get("dungeons/templates.yml");
        String base = "templates." + roomId;
        if (!templates.isConfigurationSection(base)) return;
        Set<String> existing = new HashSet<>();
        for (DungeonTrapDefinition trap : out) {
            existing.add(trap.type() + "@" + round(trap.localPosition().getX()) + "," + round(trap.localPosition().getY()) + "," + round(trap.localPosition().getZ()));
        }
        int index = out.size();
        for (String raw : templates.getStringList(base + ".precise-markers")) {
            MarkerData marker = parsePreciseMarker(raw);
            if (marker == null) continue;
            String type = trapType(marker.id());
            if (type == null) continue;
            String key = type + "@" + round(marker.x()) + "," + round(marker.y()) + "," + round(marker.z());
            if (!existing.add(key)) continue;
            out.add(new DungeonTrapDefinition(
                type + "_" + (++index),
                type,
                new Vector(marker.x(), marker.y(), marker.z()),
                enumValue(BlockFace.class, marker.facing(), BlockFace.SOUTH),
                trapMobId(type)
            ));
        }
        for (String raw : templates.getStringList(base + ".markers")) {
            MarkerData marker = parseLegacyMarker(raw);
            if (marker == null) continue;
            String type = trapType(marker.id());
            if (type == null) continue;
            String key = type + "@" + round(marker.x()) + "," + round(marker.y()) + "," + round(marker.z());
            if (!existing.add(key)) continue;
            out.add(new DungeonTrapDefinition(
                type + "_" + (++index),
                type,
                new Vector(marker.x(), marker.y(), marker.z()),
                enumValue(BlockFace.class, marker.facing(), BlockFace.SOUTH),
                trapMobId(type)
            ));
        }
    }

    private MarkerData parsePreciseMarker(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("\\|");
        if (parts.length < 5) return null;
        try {
            return new MarkerData(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                parts[3],
                parts[4]
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private MarkerData parseLegacyMarker(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(":");
        if (parts.length < 2) return null;
        String[] xyz = parts[0].split(",");
        if (xyz.length != 3) return null;
        try {
            return new MarkerData(
                Integer.parseInt(xyz[0]) + 0.5D,
                Integer.parseInt(xyz[1]),
                Integer.parseInt(xyz[2]) + 0.5D,
                parts[1],
                parts.length >= 3 ? parts[2] : "SOUTH"
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String trapType(String markerId) {
        if (markerId == null) return null;
        if ("trap_boulder".equalsIgnoreCase(markerId)) return "boulder";
        if ("trap_spike".equalsIgnoreCase(markerId)) return "spike";
        if ("trap_bridge".equalsIgnoreCase(markerId)) return "bridge";
        return null;
    }

    private String trapMobId(String trapType) {
        return configs.get("dungeons/dungeons.yml").getString("traps.mythicmobs." + trapType.toLowerCase(Locale.ROOT), switch (trapType.toLowerCase(Locale.ROOT)) {
            case "spike" -> "Spike_Trap";
            case "bridge" -> "Bridge_Trap";
            default -> "DungeonBoulder";
        });
    }

    private double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
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

    private Vector numberVector(List<?> list) {
        return new Vector(numberValue(list, 0), numberValue(list, 1), numberValue(list, 2));
    }

    private double numberValue(List<?> list, int index) {
        if (list == null || list.size() <= index) return 0.0D;
        Object value = list.get(index);
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0.0D;
        }
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
            if (!connectorOpensOutOfRoom(connector, roomWidth, roomHeight, roomLength)) {
                plugin.getLogger().warning("Room " + room.id() + " connector " + connector.id() + " is not on the outside face it points toward.");
                return false;
            }
        }
        return true;
    }

    private boolean connectorOpensOutOfRoom(DungeonConnector connector, int roomWidth, int roomHeight, int roomLength) {
        Vector p = connector.localPosition();
        int width = Math.max(1, connector.width());
        int height = Math.max(1, connector.height());
        if (p.getBlockY() + height > roomHeight) return false;
        return switch (connector.facing()) {
            case NORTH -> p.getBlockZ() == 0 && p.getBlockX() + width <= roomWidth;
            case SOUTH -> p.getBlockZ() == roomLength - 1 && p.getBlockX() + width <= roomWidth;
            case EAST -> p.getBlockX() == roomWidth - 1 && p.getBlockZ() + width <= roomLength;
            case WEST -> p.getBlockX() == 0 && p.getBlockZ() + width <= roomLength;
            case UP -> p.getBlockY() == roomHeight - 1;
            case DOWN -> p.getBlockY() == 0;
            default -> false;
        };
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
            Math.max(0.0D, yaml.getDouble(path + "connector-overlap-tolerance-blocks", defaults.connectorOverlapToleranceBlocks())),
            yaml.getBoolean(path + "allow-touching-at-connectors", defaults.allowTouchingAtConnectors()),
            yaml.getBoolean(path + "require-final-boss-room", defaults.requireFinalBossRoom()),
            yaml.getBoolean(path + "boss-room-required", defaults.bossRoomRequired()),
            Math.max(1, yaml.getInt(path + "minimum-complete-rooms", defaults.minimumCompleteRooms())),
            yaml.getBoolean(path + "prevent-floating-connectors", defaults.preventFloatingConnectors()),
            yaml.getBoolean(path + "seal-open-optional-connectors", defaults.sealOpenOptionalConnectors()),
            yaml.getBoolean(path + "allow-extra-ending-rooms", defaults.allowExtraEndingRooms()),
            Math.max(0, yaml.getInt(path + "max-extra-ending-rooms", defaults.maxExtraEndingRooms())),
            yaml.getString(path + "required-open-connector-behavior", defaults.requiredOpenConnectorBehavior()),
            yaml.getString(path + "optional-open-connector-behavior", defaults.optionalOpenConnectorBehavior()),
            yaml.getBoolean(path + "paste.apply-physics", defaults.applyPhysics()),
            yaml.getBoolean(path + "paste.update-neighbors", defaults.updateNeighbors()),
            yaml.getBoolean(path + "paste.preserve-blockstates", defaults.preserveBlockstates()),
            yaml.getBoolean(path + "paste.preserve-block-entities", defaults.preserveBlockEntities()),
            Math.max(4, yaml.getInt(path + "max-placement-candidates", defaults.maxPlacementCandidates())),
            yaml.getDouble(path + "treasure-room-chance", defaults.treasureChance()),
            yaml.getDouble(path + "mini-boss-room-chance", defaults.miniBossChance())
        );
    }

    private record MarkerData(double x, double y, double z, String id, String facing) {
    }
}
