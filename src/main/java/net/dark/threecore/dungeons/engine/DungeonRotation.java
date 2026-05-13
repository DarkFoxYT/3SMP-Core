package net.dark.threecore.dungeons.engine;

import org.bukkit.block.BlockFace;

import java.util.List;

public enum DungeonRotation {
    NONE(0),
    CLOCKWISE_90(90),
    CLOCKWISE_180(180),
    COUNTERCLOCKWISE_90(270);

    private static final List<BlockFace> CARDINAL_FACES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private final int degrees;

    DungeonRotation(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }

    public static DungeonRotation of(int degrees) {
        return switch (((degrees % 360) + 360) % 360) {
            case 90 -> CLOCKWISE_90;
            case 180 -> CLOCKWISE_180;
            case 270 -> COUNTERCLOCKWISE_90;
            default -> NONE;
        };
    }

    public static DungeonRotation fromBaseFacing(BlockFace baseFacing) {
        return switch (baseFacing == null ? BlockFace.SOUTH : baseFacing) {
            case NORTH -> CLOCKWISE_180;
            case EAST -> CLOCKWISE_90;
            case WEST -> COUNTERCLOCKWISE_90;
            default -> NONE;
        };
    }

    public DungeonRotation add(DungeonRotation other) {
        return of(this.degrees + (other == null ? 0 : other.degrees));
    }

    public DungeonRotation opposite() {
        return switch (this) {
            case NONE -> CLOCKWISE_180;
            case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
            case CLOCKWISE_180 -> NONE;
            case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
        };
    }

    public BlockFace rotate(BlockFace face) {
        if (face == BlockFace.UP || face == BlockFace.DOWN) return face;
        int index = CARDINAL_FACES.indexOf(face);
        if (index < 0) return face;
        int turns = switch (this) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return CARDINAL_FACES.get((index + turns) % CARDINAL_FACES.size());
    }

    public int quarterTurns() {
        return switch (this) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }
}
