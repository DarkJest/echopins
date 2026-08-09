package dev.echopins.domain.anchor;

/**
 * The face of a block an EchoPin was attached to. Mirrors Minecraft's {@code Direction} but is
 * declared here so the domain owns its own closed set of values, and so the persisted ordinal
 * is stable regardless of what Minecraft does to its own enum ordering.
 */
public enum BlockFace {
    DOWN(0, 0, -1, 0),
    UP(1, 0, 1, 0),
    NORTH(2, 0, 0, -1),
    SOUTH(3, 0, 0, 1),
    WEST(4, -1, 0, 0),
    EAST(5, 1, 0, 0);

    private static final BlockFace[] BY_ID = new BlockFace[6];

    static {
        for (BlockFace face : values()) {
            BY_ID[face.id] = face;
        }
    }

    private final int id;
    private final int stepX;
    private final int stepY;
    private final int stepZ;

    BlockFace(int id, int stepX, int stepY, int stepZ) {
        this.id = id;
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    /** Stable persisted identifier. Never renumber these. */
    public int id() {
        return id;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    /** Returns {@link #UP} for an unknown id so a corrupted value degrades instead of throwing. */
    public static BlockFace byId(int id) {
        if (id < 0 || id >= BY_ID.length) {
            return UP;
        }
        return BY_ID[id];
    }
}
