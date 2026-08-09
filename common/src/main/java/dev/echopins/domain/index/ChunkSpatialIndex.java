package dev.echopins.domain.index;

import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.pin.PinId;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * Buckets pin ids by dimension and 16x16 chunk column.
 *
 * <p>A proximity query only visits the chunk columns that overlap the search radius, so cost
 * scales with the searched area rather than with the number of stored pins. This is what keeps
 * discovery viable on a server holding thousands of pins.
 *
 * <p>Y is intentionally not indexed. Dividing further by height would add a dimension to every
 * bucket key while the interesting query - "what is near me" - is dominated by horizontal
 * spread; the exact distance filter afterwards handles Y.
 *
 * <p>Not thread-safe. It is owned by the repository, which is only touched from the server
 * thread.
 */
public final class ChunkSpatialIndex {

    /** Guards against a pathological radius turning one query into millions of bucket lookups. */
    public static final int MAX_CHUNK_RADIUS = 32;

    private final Map<DimensionId, Map<Long, Set<PinId>>> byDimension = new HashMap<>();

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFF_FFFFL) | (((long) chunkZ & 0xFFFF_FFFFL) << 32);
    }

    public static int chunkXFromKey(long key) {
        return (int) (key & 0xFFFF_FFFFL);
    }

    public static int chunkZFromKey(long key) {
        return (int) ((key >>> 32) & 0xFFFF_FFFFL);
    }

    public static int toChunk(double worldCoordinate) {
        return Math.floorDiv((int) Math.floor(worldCoordinate), 16);
    }

    public void add(DimensionId dimension, int chunkX, int chunkZ, PinId pin) {
        byDimension
                .computeIfAbsent(dimension, d -> new HashMap<>())
                .computeIfAbsent(chunkKey(chunkX, chunkZ), k -> new HashSet<>())
                .add(pin);
    }

    public void remove(DimensionId dimension, int chunkX, int chunkZ, PinId pin) {
        Map<Long, Set<PinId>> chunks = byDimension.get(dimension);
        if (chunks == null) {
            return;
        }
        long key = chunkKey(chunkX, chunkZ);
        Set<PinId> pins = chunks.get(key);
        if (pins == null) {
            return;
        }
        pins.remove(pin);
        // Empty buckets are pruned so a player who creates and deletes pins while travelling
        // does not leave a permanent trail of empty maps behind.
        if (pins.isEmpty()) {
            chunks.remove(key);
            if (chunks.isEmpty()) {
                byDimension.remove(dimension);
            }
        }
    }

    /**
     * Visits every pin id in the chunk columns overlapping the axis-aligned square of
     * {@code blockRadius} around the given world position.
     *
     * <p>Callers must still apply an exact distance test - this returns a superset.
     */
    public void forEachNear(DimensionId dimension, double x, double z, double blockRadius,
                            java.util.function.Consumer<PinId> consumer) {
        Map<Long, Set<PinId>> chunks = byDimension.get(dimension);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        int centerX = toChunk(x);
        int centerZ = toChunk(z);
        int chunkRadius = Math.min(MAX_CHUNK_RADIUS, (int) Math.ceil(Math.max(0.0D, blockRadius) / 16.0D) + 1);

        // When the search box covers more buckets than the dimension actually has pins in,
        // walking the stored buckets is cheaper than probing every coordinate in the box.
        long boxBuckets = (2L * chunkRadius + 1L) * (2L * chunkRadius + 1L);
        if (boxBuckets > chunks.size()) {
            for (Map.Entry<Long, Set<PinId>> entry : chunks.entrySet()) {
                int cx = chunkXFromKey(entry.getKey());
                int cz = chunkZFromKey(entry.getKey());
                if (Math.abs(cx - centerX) <= chunkRadius && Math.abs(cz - centerZ) <= chunkRadius) {
                    entry.getValue().forEach(consumer);
                }
            }
            return;
        }

        for (int cx = centerX - chunkRadius; cx <= centerX + chunkRadius; cx++) {
            for (int cz = centerZ - chunkRadius; cz <= centerZ + chunkRadius; cz++) {
                Set<PinId> pins = chunks.get(chunkKey(cx, cz));
                if (pins != null) {
                    pins.forEach(consumer);
                }
            }
        }
    }

    /** Number of pin ids indexed in a dimension. */
    public int countIn(DimensionId dimension) {
        Map<Long, Set<PinId>> chunks = byDimension.get(dimension);
        if (chunks == null) {
            return 0;
        }
        int total = 0;
        for (Set<PinId> pins : chunks.values()) {
            total += pins.size();
        }
        return total;
    }

    /** Number of non-empty chunk buckets, exposed for the admin stats command. */
    public int bucketCount() {
        int total = 0;
        for (Map<Long, Set<PinId>> chunks : byDimension.values()) {
            total += chunks.size();
        }
        return total;
    }

    public Set<DimensionId> dimensions() {
        return Collections.unmodifiableSet(byDimension.keySet());
    }

    public void clear() {
        byDimension.clear();
    }

    /** Visits the keys of every populated bucket in a dimension. Used by diagnostics. */
    public void forEachBucket(DimensionId dimension, LongConsumer consumer) {
        Map<Long, Set<PinId>> chunks = byDimension.get(dimension);
        if (chunks != null) {
            chunks.keySet().forEach(consumer::accept);
        }
    }
}
