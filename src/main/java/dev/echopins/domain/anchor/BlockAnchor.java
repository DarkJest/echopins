package dev.echopins.domain.anchor;

import java.util.Objects;

/**
 * A pin attached to a specific face of a specific block.
 *
 * @param dimension the dimension the block is in
 * @param blockX    block X
 * @param blockY    block Y
 * @param blockZ    block Z
 * @param face      the face the pin is attached to
 */
public record BlockAnchor(DimensionId dimension, int blockX, int blockY, int blockZ, BlockFace face)
        implements WorldAnchor {

    /** How far off the block face the marker floats, in blocks. */
    private static final double FACE_OFFSET = 0.55D;

    public BlockAnchor {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(face, "face");
        if (Math.abs((double) blockX) > WorldPos.MAX_ABS_COORDINATE
                || Math.abs((double) blockZ) > WorldPos.MAX_ABS_COORDINATE) {
            throw new IllegalArgumentException("Block coordinate out of range");
        }
        // Y is bounded far more tightly than X/Z in every vanilla dimension; this range is
        // generous enough for datapack dimensions while still rejecting nonsense.
        if (blockY < -4096 || blockY > 4096) {
            throw new IllegalArgumentException("Block Y out of range: " + blockY);
        }
    }

    @Override
    public Kind kind() {
        return Kind.BLOCK;
    }

    @Override
    public WorldPos renderPos() {
        return new WorldPos(
                blockX + 0.5D + face.stepX() * FACE_OFFSET,
                blockY + 0.5D + face.stepY() * FACE_OFFSET,
                blockZ + 0.5D + face.stepZ() * FACE_OFFSET);
    }

    /** Centre of the attached block, used for reach and distance validation. */
    public WorldPos blockCenter() {
        return new WorldPos(blockX + 0.5D, blockY + 0.5D, blockZ + 0.5D);
    }
}
