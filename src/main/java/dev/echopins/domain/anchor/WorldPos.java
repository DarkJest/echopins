package dev.echopins.domain.anchor;

/**
 * An immutable world-space point. Exists so the domain can reason about distance without
 * depending on Minecraft's {@code Vec3}.
 *
 * @param x world X
 * @param y world Y
 * @param z world Z
 */
public record WorldPos(double x, double y, double z) {

    /**
     * Largest absolute coordinate a pin may use. Minecraft's world border caps out at
     * 29,999,984 blocks, so anything beyond this is either a broken client or an attempt to
     * push absurd values into persistence.
     */
    public static final double MAX_ABS_COORDINATE = 30_000_000.0D;

    public WorldPos {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Non-finite coordinate");
        }
        if (Math.abs(x) > MAX_ABS_COORDINATE
                || Math.abs(y) > MAX_ABS_COORDINATE
                || Math.abs(z) > MAX_ABS_COORDINATE) {
            throw new IllegalArgumentException("Coordinate out of range");
        }
    }

    public double distanceSquaredTo(WorldPos other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(WorldPos other) {
        return Math.sqrt(distanceSquaredTo(other));
    }

    /** Chunk X of the containing chunk, used by the spatial index. */
    public int chunkX() {
        return Math.floorDiv((int) Math.floor(x), 16);
    }

    /** Chunk Z of the containing chunk, used by the spatial index. */
    public int chunkZ() {
        return Math.floorDiv((int) Math.floor(z), 16);
    }
}
