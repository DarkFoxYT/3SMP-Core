package net.dark.threecore.dungeons.engine;

import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public final class DungeonGraphGenerator {
    private final DungeonRoomRegistry roomRegistry;
    private final Random random = new Random();

    public DungeonGraphGenerator(DungeonRoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
    }

    public DungeonLayout generate(String dungeonId, String worldName, int baseY, boolean debugEnabled) {
        DungeonGenerationSettings settings = roomRegistry.settings();
        long startedAt = System.currentTimeMillis();
        List<String> failures = new ArrayList<>();
        List<String> debug = new ArrayList<>();

        for (int attempt = 1; attempt <= Math.max(1, settings.maxAttempts()); attempt++) {
            GenerationAttempt built = build(attempt, dungeonId, worldName, baseY, debugEnabled);
            if (built.successful()) {
                DungeonLayout layout = new DungeonLayout(
                    dungeonId,
                    worldName,
                    built.rooms(),
                    built.connections(),
                    Math.max(1L, System.currentTimeMillis() - startedAt),
                    built.debugLines()
                );
                DungeonValidationResult validation = new DungeonValidator().validate(layout, settings.bossRoomRequired());
                if (validation.valid()) {
                    return layout;
                }
                failures.add("Attempt " + attempt + " failed final validation: " + String.join("; ", validation.failures()));
            } else {
                failures.add("Attempt " + attempt + " failed: " + String.join("; ", built.failures()));
            }
            if (debugEnabled) debug.addAll(built.debugLines());
        }

        List<String> output = debugEnabled && !debug.isEmpty() ? debug : failures;
        if (output.isEmpty()) output = List.of("Failed to generate a valid dungeon layout.");
        return new DungeonLayout(dungeonId, worldName, List.of(), List.of(), Math.max(1L, System.currentTimeMillis() - startedAt), output);
    }

    private GenerationAttempt build(int attempt, String dungeonId, String worldName, int baseY, boolean debugEnabled) {
        DungeonGenerationSettings settings = roomRegistry.settings();
        List<PlacedDungeonRoom> rooms = new ArrayList<>();
        List<DungeonConnection> connections = new ArrayList<>();
        Map<Integer, Set<String>> usedConnectors = new HashMap<>();
        List<String> debug = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        DungeonRoomDefinition startDefinition = weightedPick(roomRegistry.byType(RoomType.START));
        if (startDefinition == null) {
            failures.add("No START room configured.");
            return GenerationAttempt.failed(failures, debug);
        }
        if (!validEntranceContract(startDefinition, failures) || !validConnectorBounds(startDefinition, failures)) {
            return GenerationAttempt.failed(failures, debug);
        }

        PlacedDungeonRoom startRoom = PlacedDungeonRoom.place(startDefinition, worldName, new Vector(0, baseY, 0), DungeonRotation.NONE, 0);
        rooms.add(startRoom);
        usedConnectors.put(startRoom.graphIndex(), new HashSet<>());
        debug.add("Attempt " + attempt + ": placed START " + startDefinition.id() + " at " + format(startRoom.origin()) + " bounds=" + format(startRoom.box()));

        int targetDepth = randomInt(settings.mainPathMin(), settings.mainPathMax());
        boolean bossPlaced = false;
        PlacedDungeonRoom cursor = startRoom;

        for (int depth = 1; depth < targetDepth; depth++) {
            PlacedConnector exit = pickOpenExit(cursor, usedConnectors, true);
            if (exit == null) {
                failures.add("Main path stopped at " + cursor.definition().id() + " because it has no open exit.");
                return GenerationAttempt.failed(failures, debug);
            }

            RoomType type = mainPathType(depth, targetDepth, bossPlaced, settings);
            PlacementCandidate candidate = bestPlacement(exit, type, rooms, usedConnectors, depth, targetDepth, debugEnabled, debug);
            if (candidate == null && type != RoomType.NORMAL && type != RoomType.BOSS) {
                candidate = bestPlacement(exit, RoomType.NORMAL, rooms, usedConnectors, depth, targetDepth, debugEnabled, debug);
            }
            if (candidate == null) {
                failures.add("No valid " + type + " placement from " + describe(exit) + ".");
                return GenerationAttempt.failed(failures, debug);
            }

            apply(candidate, exit, rooms, connections, usedConnectors);
            cursor = candidate.room();
            bossPlaced = bossPlaced || cursor.definition().type() == RoomType.BOSS;
            debug.add("MAIN " + candidate.score().debugLine());
            if (bossPlaced) break;
        }

        if (!bossPlaced && settings.requireFinalBossRoom()) {
            PlacedConnector exit = pickOpenExit(cursor, usedConnectors, true);
            if (exit == null) {
                failures.add("Boss required, but " + cursor.definition().id() + " has no open exit.");
                return GenerationAttempt.failed(failures, debug);
            }
            PlacementCandidate boss = bestPlacement(exit, RoomType.BOSS, rooms, usedConnectors, targetDepth, targetDepth, debugEnabled, debug);
            if (boss == null) {
                failures.add("Boss required, but no boss room could attach to " + describe(exit) + ".");
                return GenerationAttempt.failed(failures, debug);
            }
            apply(boss, exit, rooms, connections, usedConnectors);
            bossPlaced = true;
            debug.add("BOSS " + boss.score().debugLine());
        }

        addBranches(rooms, connections, usedConnectors, debugEnabled, debug);

        if (settings.preventFloatingConnectors() && !sealOpenConnectors(rooms, connections, usedConnectors, failures, debugEnabled, debug)) {
            return GenerationAttempt.failed(failures, debug);
        }

        if (settings.bossRoomRequired() && !bossPlaced) {
            failures.add("Boss room required but no boss was placed.");
            return GenerationAttempt.failed(failures, debug);
        }
        debug.add("Generated in attempt " + attempt + " with " + rooms.size() + " rooms and " + connections.size() + " connections.");
        return new GenerationAttempt(true, List.copyOf(rooms), List.copyOf(connections), failures, List.copyOf(debug));
    }

    private void addBranches(
        List<PlacedDungeonRoom> rooms,
        List<DungeonConnection> connections,
        Map<Integer, Set<String>> usedConnectors,
        boolean debugEnabled,
        List<String> debug
    ) {
        DungeonGenerationSettings settings = roomRegistry.settings();
        int branches = randomInt(settings.branchMin(), settings.branchMax());
        int guard = Math.max(4, branches * 10);
        while (branches > 0 && guard-- > 0) {
            PlacedConnector seed = randomOpenExit(rooms, usedConnectors);
            if (seed == null) return;
            int length = randomInt(settings.branchLengthMin(), settings.branchLengthMax());
            PlacedConnector exit = seed;
            for (int i = 0; i < length; i++) {
                RoomType type = branchType(settings);
                PlacementCandidate candidate = bestPlacement(exit, type, rooms, usedConnectors, i, length, debugEnabled, debug);
                if (candidate == null) break;
                apply(candidate, exit, rooms, connections, usedConnectors);
                debug.add("BRANCH " + candidate.score().debugLine());
                exit = pickOpenExit(candidate.room(), usedConnectors, false);
                if (exit == null) break;
            }
            branches--;
        }
    }

    private boolean sealOpenConnectors(
        List<PlacedDungeonRoom> rooms,
        List<DungeonConnection> connections,
        Map<Integer, Set<String>> usedConnectors,
        List<String> failures,
        boolean debugEnabled,
        List<String> debug
    ) {
        boolean changed;
        do {
            changed = false;
            List<PlacedConnector> openRequired = openConnectors(rooms, usedConnectors).stream()
                .filter(connector -> connector.connector().required())
                .toList();
            for (PlacedConnector connector : openRequired) {
                PlacementCandidate cap = bestDeadEndPlacement(connector, rooms, usedConnectors, debugEnabled, debug);
                if (cap == null) {
                    failures.add("Required connector left open: " + describe(connector));
                    return false;
                }
                apply(cap, connector, rooms, connections, usedConnectors);
                debug.add("SEALED " + describe(connector) + " with " + cap.room().definition().id());
                changed = true;
            }
        } while (changed);
        if (roomRegistry.settings().sealOpenOptionalConnectors()) {
            for (PlacedConnector connector : openConnectors(rooms, usedConnectors)) {
                if (connector.connector().required()) continue;
                markUsed(usedConnectors, connector.worldRoomIndex(), connector.connector().id());
                if (debugEnabled) debug.add("CAPPED optional connector " + describe(connector) + " using configured wall/door cap behavior.");
            }
        }
        return true;
    }

    private PlacementCandidate bestDeadEndPlacement(
        PlacedConnector connector,
        List<PlacedDungeonRoom> rooms,
        Map<Integer, Set<String>> usedConnectors,
        boolean debugEnabled,
        List<String> debug
    ) {
        List<PlacementCandidate> candidates = new ArrayList<>();
        for (String rawType : roomRegistry.deadEndConnectorRoomTypes()) {
            Optional<RoomType> type = parseRoomType(rawType);
            if (type.isEmpty()) continue;
            candidates.addAll(placements(connector, type.get(), rooms, usedConnectors, 999, 999, debugEnabled, debug));
        }
        return candidates.stream().max(Comparator.comparingInt(candidate -> candidate.score().score())).orElse(null);
    }

    private PlacementCandidate bestPlacement(
        PlacedConnector from,
        RoomType preferred,
        List<PlacedDungeonRoom> rooms,
        Map<Integer, Set<String>> usedConnectors,
        int depth,
        int targetDepth,
        boolean debugEnabled,
        List<String> debug
    ) {
        return placements(from, preferred, rooms, usedConnectors, depth, targetDepth, debugEnabled, debug).stream()
            .max(Comparator.comparingInt(candidate -> candidate.score().score()))
            .orElse(null);
    }

    private List<PlacementCandidate> placements(
        PlacedConnector from,
        RoomType preferred,
        List<PlacedDungeonRoom> rooms,
        Map<Integer, Set<String>> usedConnectors,
        int depth,
        int targetDepth,
        boolean debugEnabled,
        List<String> debug
    ) {
        List<PlacementCandidate> out = new ArrayList<>();
        List<DungeonRoomDefinition> definitions = new ArrayList<>(roomPool(preferred, from));
        java.util.Collections.shuffle(definitions, random);
        for (DungeonRoomDefinition definition : definitions) {
            if (!validEntranceContract(definition, null) || !validConnectorBounds(definition, null)) continue;
            List<DungeonConnector> entrances = new ArrayList<>(definition.entrances());
            java.util.Collections.shuffle(entrances, random);
            List<DungeonRotation> rotations = new ArrayList<>(List.of(DungeonRotation.values()));
            java.util.Collections.shuffle(rotations, random);
            for (DungeonConnector entrance : entrances) {
                for (DungeonRotation rotation : rotations) {
                    PlacementCandidate candidate = placement(from, definition, entrance, rotation, rooms, usedConnectors, preferred, depth, targetDepth);
                    if (candidate == null) {
                        if (debugEnabled) debug.add("REJECT " + definition.id() + " entrance=" + entrance.id() + " rot=" + rotation + " from=" + describe(from));
                        continue;
                    }
                    out.add(candidate);
                }
            }
        }
        return out;
    }

    private PlacementCandidate placement(
        PlacedConnector from,
        DungeonRoomDefinition definition,
        DungeonConnector entrance,
        DungeonRotation rotation,
        List<PlacedDungeonRoom> rooms,
        Map<Integer, Set<String>> usedConnectors,
        RoomType preferred,
        int depth,
        int targetDepth
    ) {
        Vector size = definition.size();
        RoomTransform zeroTransform = new RoomTransform(new Vector(), definition.effectiveRotation(rotation), size.getBlockX(), size.getBlockY(), size.getBlockZ(), definition.rotationOrigin().resolve(size, definition.facingMarker()));
        DungeonConnector rotatedEntrance = zeroTransform.rotatedConnector(entrance);

        if (rotatedEntrance.role() != ConnectorRole.ENTRANCE) return null;
        if (!from.connector().compatibleWith(rotatedEntrance)) return null;
        if (!verticalAllowed(from.connector(), rotatedEntrance)) return null;

        Vector targetCenter = openingTarget(from, rotatedEntrance);
        Vector origin = targetCenter.clone().subtract(anchorFor(zeroTransform, entrance));
        origin = new Vector(Math.round(origin.getX()), Math.round(origin.getY()), Math.round(origin.getZ()));
        PlacedDungeonRoom room = PlacedDungeonRoom.place(definition, from.world(), origin, rotation, rooms.size());
        PlacedConnector placedEntrance = findConnector(room, entrance.id(), ConnectorRole.ENTRANCE);
        if (placedEntrance == null) return null;
        if (!centersMatch(targetCenter, placedEntrance.center())) return null;
        if (!from.compatibleWith(placedEntrance)) return null;
        if (!withinYRules(from, placedEntrance, room)) return null;

        Collision collision = collision(room, rooms, from);
        if (collision.collides()) return null;

        PlacementScore score = score(from, room, placedEntrance, preferred, depth, targetDepth, collision.reason());
        return new PlacementCandidate(room, placedEntrance.connector().id(), score);
    }

    private Vector anchorFor(RoomTransform transform, DungeonConnector connector) {
        if (connector.snapMode() == DungeonConnector.SnapMode.ANCHOR || connector.snapMode() == DungeonConnector.SnapMode.FLOOR_ANCHOR) {
            return transform.rotateConnector(new DungeonConnector(connector.id(), connector.role(), connector.anchor(), connector.facing(), connector.width(), connector.height(), connector.type(), connector.requiredMatch(), connector.required(), connector.verticalDirection(), connector.targetYOffset(), connector.snapMode(), connector.anchor())).localPosition();
        }
        return transform.localConnectorCenter(connector);
    }

    private Vector openingTarget(PlacedConnector from, DungeonConnector rotatedEntrance) {
        Vector target = from.center().add(facingOffset(from.facing(), roomRegistry.settings().connectorGapBlocks()));
        int yOffset = connectorYOffset(from.connector(), rotatedEntrance);
        if (yOffset != 0) target.add(new Vector(0, yOffset, 0));
        return target;
    }

    private int connectorYOffset(DungeonConnector from, DungeonConnector to) {
        if (!from.type().vertical() && !to.type().vertical()) return 0;
        if (from.targetYOffset() != 0) return from.targetYOffset();
        if (to.targetYOffset() != 0) return -to.targetYOffset();
        return switch (from.verticalDirection()) {
            case UP -> Math.max(1, roomRegistry.settings().maxVerticalUp());
            case DOWN -> -Math.max(1, roomRegistry.settings().maxVerticalDown());
            default -> 0;
        };
    }

    private PlacementScore score(
        PlacedConnector from,
        PlacedDungeonRoom room,
        PlacedConnector entrance,
        RoomType preferred,
        int depth,
        int targetDepth,
        String collisionReason
    ) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        score += 1000;
        reasons.add("+1000 connector match");
        if (from.connector().type() == entrance.connector().type()) {
            score += 1000;
            reasons.add("+1000 exact type");
        }
        if (room.definition().type() == preferred) {
            score += 800;
            reasons.add("+800 preferred type " + preferred);
        }
        if (room.definition().type() == RoomType.BOSS) {
            int bossProgress = Math.max(0, depth - (targetDepth - 2));
            score += 300 + bossProgress * 120;
            reasons.add("+boss path");
        }
        if (room.definition().type() == RoomType.NORMAL || room.definition().type() == RoomType.CONNECTOR) {
            score += 300;
            reasons.add("+300 path continues");
        }
        long openExits = room.connectors().stream().filter(connector -> connector.connector().role() != ConnectorRole.ENTRANCE).count();
        score += Math.min(3, openExits) * 100;
        if (openExits > 3) {
            score -= 500;
            reasons.add("-500 too many exits");
        }
        if (continuesForward(from, room)) {
            score += 200;
            reasons.add("+200 forward space");
        } else {
            score -= 80;
            reasons.add("-80 sharp turn");
        }
        score += room.definition().difficultyWeight() * 5;
        if (collisionReason != null && !collisionReason.isBlank()) reasons.add(collisionReason);
        return new PlacementScore(score, room.definition().id(), room.rotation(), room.origin(), room.box(), reasons);
    }

    private boolean continuesForward(PlacedConnector from, PlacedDungeonRoom room) {
        return switch (from.facing()) {
            case NORTH -> room.box().getCenterZ() < from.room().size().getZ();
            case SOUTH -> room.box().getCenterZ() > from.worldPosition().getZ();
            case EAST -> room.box().getCenterX() > from.worldPosition().getX();
            case WEST -> room.box().getCenterX() < from.worldPosition().getX();
            default -> true;
        };
    }

    private boolean verticalAllowed(DungeonConnector from, DungeonConnector to) {
        DungeonGenerationSettings settings = roomRegistry.settings();
        if (!from.type().vertical() && !to.type().vertical()) return true;
        if (!settings.allowVertical()) return false;
        if (from.verticalDirection() == DungeonConnector.VerticalDirection.UP && to.verticalDirection() != DungeonConnector.VerticalDirection.DOWN) return false;
        if (from.verticalDirection() == DungeonConnector.VerticalDirection.DOWN && to.verticalDirection() != DungeonConnector.VerticalDirection.UP) return false;
        if (to.verticalDirection() == DungeonConnector.VerticalDirection.UP && from.verticalDirection() != DungeonConnector.VerticalDirection.DOWN) return false;
        if (to.verticalDirection() == DungeonConnector.VerticalDirection.DOWN && from.verticalDirection() != DungeonConnector.VerticalDirection.UP) return false;
        if (settings.requireStairConnectorTypes()) return from.type().matches(to.type()) && to.type().matches(from.type());
        return from.type().vertical() == to.type().vertical();
    }

    private boolean withinYRules(PlacedConnector from, PlacedConnector to, PlacedDungeonRoom candidate) {
        DungeonGenerationSettings settings = roomRegistry.settings();
        int dy = to.worldPosition().getBlockY() - from.worldPosition().getBlockY();
        int expectedOffset = from.connector().targetYOffset() != 0 ? from.connector().targetYOffset() : -to.connector().targetYOffset();
        if (expectedOffset != 0 && dy != expectedOffset) return false;
        if (from.connector().type() == ConnectorType.STAIRS_UP || to.connector().type() == ConnectorType.STAIRS_UP) {
            if (dy < 0 || dy > Math.max(1, settings.maxVerticalUp())) return false;
        } else if (from.connector().type() == ConnectorType.STAIRS_DOWN || to.connector().type() == ConnectorType.STAIRS_DOWN) {
            if (dy > 0 || Math.abs(dy) > Math.max(1, settings.maxVerticalDown())) return false;
        } else if (Math.abs(dy) > settings.maxYLevelDifference()) {
            return false;
        }
        double minY = candidate.box().getMinY();
        double maxY = candidate.box().getMaxY();
        for (PlacedDungeonRoom room : reachableRooms(candidate, List.of())) {
            minY = Math.min(minY, room.box().getMinY());
            maxY = Math.max(maxY, room.box().getMaxY());
        }
        return (maxY - minY) <= settings.maxTotalYSpan();
    }

    private Collision collision(PlacedDungeonRoom candidate, List<PlacedDungeonRoom> existing, PlacedConnector sourceConnector) {
        DungeonGenerationSettings settings = roomRegistry.settings();
        BoundingBox reserved = expand(candidate.box(), Math.max(0, settings.roomPaddingBlocks()));
        for (PlacedDungeonRoom room : existing) {
            if (!reserved.overlaps(room.box())) continue;
            if (settings.allowTouchingAtConnectors() && touchesOnlyAtSourceConnector(candidate, room, sourceConnector)) {
                continue;
            }
            return new Collision(true, "clips " + room.definition().id() + " candidateBounds=" + format(candidate.box()) + " otherBounds=" + format(room.box()));
        }
        return new Collision(false, "no overlap");
    }

    private boolean touchesOnlyAtSourceConnector(PlacedDungeonRoom candidate, PlacedDungeonRoom existing, PlacedConnector sourceConnector) {
        if (sourceConnector == null || existing.graphIndex() != sourceConnector.worldRoomIndex()) return false;
        PlacedConnector entrance = candidate.connectors().stream()
            .filter(connector -> connector.connector().role() == ConnectorRole.ENTRANCE)
            .findFirst()
            .orElse(null);
        if (entrance == null || !sourceConnector.compatibleWith(entrance)) return false;
        BoundingBox a = candidate.box();
        BoundingBox b = existing.box();
        double xOverlap = Math.min(a.getMaxX(), b.getMaxX()) - Math.max(a.getMinX(), b.getMinX());
        double yOverlap = Math.min(a.getMaxY(), b.getMaxY()) - Math.max(a.getMinY(), b.getMinY());
        double zOverlap = Math.min(a.getMaxZ(), b.getMaxZ()) - Math.max(a.getMinZ(), b.getMinZ());
        return switch (sourceConnector.facing()) {
            case NORTH, SOUTH -> xOverlap > 0 && yOverlap > 0 && zOverlap <= 0.0001D;
            case EAST, WEST -> zOverlap > 0 && yOverlap > 0 && xOverlap <= 0.0001D;
            case UP, DOWN -> xOverlap > 0 && zOverlap > 0 && yOverlap <= 0.0001D;
            default -> false;
        };
    }

    private void apply(
        PlacementCandidate candidate,
        PlacedConnector from,
        List<PlacedDungeonRoom> rooms,
        List<DungeonConnection> connections,
        Map<Integer, Set<String>> usedConnectors
    ) {
        rooms.add(candidate.room());
        connections.add(new DungeonConnection(from.worldRoomIndex(), from.connector().id(), candidate.room().graphIndex(), candidate.toConnectorId()));
        markUsed(usedConnectors, from.worldRoomIndex(), from.connector().id());
        markUsed(usedConnectors, candidate.room().graphIndex(), candidate.toConnectorId());
        for (PlacedConnector connector : candidate.room().connectors()) {
            if (connector.connector().role() == ConnectorRole.ENTRANCE && !connector.connector().id().equalsIgnoreCase(candidate.toConnectorId())) {
                markUsed(usedConnectors, candidate.room().graphIndex(), connector.connector().id());
            }
        }
    }

    private List<DungeonRoomDefinition> roomPool(RoomType preferred, PlacedConnector from) {
        List<DungeonRoomDefinition> direct = roomRegistry.byType(preferred);
        if (!direct.isEmpty()) return direct;
        if (from.connector().type().vertical()) {
            List<DungeonRoomDefinition> vertical = roomRegistry.all().stream()
                .filter(room -> room.entrances().stream().anyMatch(connector -> connector.type().vertical() || connector.requiredMatch().vertical()))
                .toList();
            if (!vertical.isEmpty()) return vertical;
        }
        List<DungeonRoomDefinition> normals = roomRegistry.byType(RoomType.NORMAL);
        return normals.isEmpty() ? roomRegistry.all() : normals;
    }

    private RoomType mainPathType(int depth, int targetDepth, boolean bossPlaced, DungeonGenerationSettings settings) {
        if (!bossPlaced && settings.requireFinalBossRoom() && depth >= targetDepth - 1) return RoomType.BOSS;
        if (random.nextDouble() < settings.miniBossChance()) return RoomType.MINI_BOSS;
        if (random.nextDouble() < settings.treasureChance() * 0.35D) return RoomType.TREASURE;
        return RoomType.NORMAL;
    }

    private RoomType branchType(DungeonGenerationSettings settings) {
        double roll = random.nextDouble();
        if (roll < settings.treasureChance()) return RoomType.TREASURE;
        if (roll < settings.treasureChance() + settings.miniBossChance()) return RoomType.MINI_BOSS;
        return RoomType.NORMAL;
    }

    private PlacedConnector pickOpenExit(PlacedDungeonRoom room, Map<Integer, Set<String>> usedConnectors, boolean mainPath) {
        List<PlacedConnector> open = room.connectors().stream()
            .filter(connector -> connector.connector().role() != ConnectorRole.ENTRANCE)
            .filter(connector -> !isUsed(usedConnectors, room.graphIndex(), connector.connector().id()))
            .filter(connector -> !mainPath || connector.connector().role() == ConnectorRole.EXIT || connector.connector().role() == ConnectorRole.VERTICAL)
            .toList();
        if (open.isEmpty()) return null;
        return open.get(random.nextInt(open.size()));
    }

    private PlacedConnector randomOpenExit(List<PlacedDungeonRoom> rooms, Map<Integer, Set<String>> usedConnectors) {
        List<PlacedConnector> open = new ArrayList<>();
        for (PlacedDungeonRoom room : rooms) {
            for (PlacedConnector connector : room.connectors()) {
                if (connector.connector().role() == ConnectorRole.ENTRANCE) continue;
                if (!isUsed(usedConnectors, room.graphIndex(), connector.connector().id())) open.add(connector);
            }
        }
        return open.isEmpty() ? null : open.get(random.nextInt(open.size()));
    }

    private List<PlacedConnector> openConnectors(List<PlacedDungeonRoom> rooms, Map<Integer, Set<String>> usedConnectors) {
        List<PlacedConnector> open = new ArrayList<>();
        for (PlacedDungeonRoom room : rooms) {
            for (PlacedConnector connector : room.connectors()) {
                if (!isUsed(usedConnectors, room.graphIndex(), connector.connector().id())) open.add(connector);
            }
        }
        return open;
    }

    private boolean validEntranceContract(DungeonRoomDefinition room, List<String> failures) {
        long entrances = room.entrances().size();
        if (room.type() == RoomType.START) {
            if (entrances != 0) {
                if (failures != null) failures.add("START room " + room.id() + " has entrances.");
                return false;
            }
        } else if (entrances < 1) {
            if (failures != null) failures.add("Room " + room.id() + " has no entrances.");
            return false;
        }
        return true;
    }

    private boolean validConnectorBounds(DungeonRoomDefinition room, List<String> failures) {
        int maxX = Math.max(1, room.size().getBlockX());
        int maxY = Math.max(1, room.size().getBlockY());
        int maxZ = Math.max(1, room.size().getBlockZ());
        for (DungeonConnector connector : room.connectors()) {
            Vector pos = connector.localPosition();
            if (pos.getBlockX() < 0 || pos.getBlockY() < 0 || pos.getBlockZ() < 0 || pos.getBlockX() >= maxX || pos.getBlockY() >= maxY || pos.getBlockZ() >= maxZ) {
                if (failures != null) failures.add("Connector " + room.id() + "." + connector.id() + " is outside room bounds.");
                return false;
            }
            if (!validFacing(connector.facing())) {
                if (failures != null) failures.add("Connector " + room.id() + "." + connector.id() + " has invalid facing " + connector.facing() + ".");
                return false;
            }
        }
        return true;
    }

    private boolean validFacing(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST || face == BlockFace.UP || face == BlockFace.DOWN;
    }

    private PlacedConnector findConnector(PlacedDungeonRoom room, String id, ConnectorRole role) {
        return room.connectors().stream()
            .filter(connector -> connector.connector().id().equalsIgnoreCase(id))
            .filter(connector -> role == null || connector.connector().role() == role)
            .findFirst()
            .orElse(null);
    }

    private boolean centersMatch(Vector a, Vector b) {
        return a.distanceSquared(b) < 0.0001D;
    }

    private Vector facingOffset(BlockFace face, int gap) {
        int amount = Math.max(1, gap);
        return switch (face) {
            case NORTH -> new Vector(0, 0, -amount);
            case SOUTH -> new Vector(0, 0, amount);
            case EAST -> new Vector(amount, 0, 0);
            case WEST -> new Vector(-amount, 0, 0);
            case UP -> new Vector(0, amount, 0);
            case DOWN -> new Vector(0, -amount, 0);
            default -> new Vector();
        };
    }

    private boolean isUsed(Map<Integer, Set<String>> usedConnectors, int room, String connector) {
        return usedConnectors.getOrDefault(room, Set.of()).contains(connector.toLowerCase(Locale.ROOT));
    }

    private void markUsed(Map<Integer, Set<String>> usedConnectors, int room, String connector) {
        usedConnectors.computeIfAbsent(room, ignored -> new HashSet<>()).add(connector.toLowerCase(Locale.ROOT));
    }

    private DungeonRoomDefinition weightedPick(List<DungeonRoomDefinition> roomPool) {
        if (roomPool == null || roomPool.isEmpty()) return null;
        int total = roomPool.stream().mapToInt(DungeonRoomDefinition::weight).sum();
        int roll = random.nextInt(Math.max(1, total));
        int seen = 0;
        for (DungeonRoomDefinition room : roomPool) {
            seen += room.weight();
            if (roll < seen) return room;
        }
        return roomPool.get(0);
    }

    private int randomInt(int min, int max) {
        if (max <= min) return Math.max(0, min);
        return min + random.nextInt(max - min + 1);
    }

    private BoundingBox expand(BoundingBox box, int padding) {
        if (padding <= 0) return box.clone();
        return new BoundingBox(
            box.getMinX() - padding,
            box.getMinY() - padding,
            box.getMinZ() - padding,
            box.getMaxX() + padding,
            box.getMaxY() + padding,
            box.getMaxZ() + padding
        );
    }

    private List<PlacedDungeonRoom> reachableRooms(PlacedDungeonRoom start, List<DungeonConnection> connections) {
        return List.of(start);
    }

    private Optional<RoomType> parseRoomType(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(RoomType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String describe(PlacedConnector connector) {
        return connector.room().id() + "." + connector.connector().id() + "@" + format(connector.worldPosition()) + " facing=" + connector.facing();
    }

    private String format(Vector vector) {
        return vector.getBlockX() + "," + vector.getBlockY() + "," + vector.getBlockZ();
    }

    private String format(BoundingBox box) {
        return "[" + Math.round(box.getMinX()) + "," + Math.round(box.getMinY()) + "," + Math.round(box.getMinZ()) + " -> " + Math.round(box.getMaxX()) + "," + Math.round(box.getMaxY()) + "," + Math.round(box.getMaxZ()) + "]";
    }

    private record PlacementCandidate(PlacedDungeonRoom room, String toConnectorId, PlacementScore score) {
    }

    public record PlacementScore(int score, String roomId, DungeonRotation rotation, Vector origin, BoundingBox bounds, List<String> reasons) {
        public String debugLine() {
            return "candidate=" + roomId
                + " rotation=" + rotation
                + " origin=" + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ()
                + " bounds=[" + Math.round(bounds.getMinX()) + "," + Math.round(bounds.getMinY()) + "," + Math.round(bounds.getMinZ()) + " -> " + Math.round(bounds.getMaxX()) + "," + Math.round(bounds.getMaxY()) + "," + Math.round(bounds.getMaxZ()) + "]"
                + " score=" + score
                + " reasons=" + String.join(", ", reasons);
        }
    }

    private record Collision(boolean collides, String reason) {
    }

    private record GenerationAttempt(boolean successful, List<PlacedDungeonRoom> rooms, List<DungeonConnection> connections, List<String> failures, List<String> debugLines) {
        private static GenerationAttempt failed(List<String> failures, List<String> debugLines) {
            return new GenerationAttempt(false, List.of(), List.of(), List.copyOf(failures), List.copyOf(debugLines));
        }
    }
}
