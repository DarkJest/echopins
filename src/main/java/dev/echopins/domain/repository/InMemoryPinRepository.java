package dev.echopins.domain.repository;

import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.index.ChunkSpatialIndex;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The authoritative in-memory pin store, including every index.
 *
 * <p>Deliberately free of Minecraft types. Persistence wraps this rather than reimplementing it,
 * which means the indexing and query behaviour that matters for correctness and performance is
 * exercised by plain unit tests instead of only inside a running server.
 *
 * <p>Not thread-safe; owned by the server thread.
 */
public class InMemoryPinRepository implements PinRepository {

    private final Map<PinId, EchoPin> pins = new LinkedHashMap<>();
    private final ChunkSpatialIndex spatialIndex = new ChunkSpatialIndex();
    private final Map<UUID, Set<PinId>> byAuthor = new HashMap<>();
    private final Map<DimensionId, Integer> countByDimension = new HashMap<>();

    /**
     * Earliest expiry among stored pins, used to skip the expiry sweep entirely when nothing can
     * have expired yet. {@link Long#MAX_VALUE} means "no expiring pin known".
     */
    private long earliestExpiry = Long.MAX_VALUE;

    @Override
    public Optional<EchoPin> find(PinId id) {
        return Optional.ofNullable(pins.get(id));
    }

    @Override
    public void save(EchoPin pin) {
        EchoPin previous = pins.put(pin.id(), pin);
        if (previous != null) {
            unindex(previous);
        }
        index(pin);
        markDirty();
    }

    @Override
    public Optional<EchoPin> remove(PinId id) {
        EchoPin removed = pins.remove(id);
        if (removed == null) {
            return Optional.empty();
        }
        unindex(removed);
        markDirty();
        return Optional.of(removed);
    }

    @Override
    public List<EchoPin> findNearby(DimensionId dimension, WorldPos pos, double radius) {
        double radiusSq = radius * radius;
        List<EchoPin> result = new ArrayList<>();
        Set<PinId> visited = new HashSet<>();
        spatialIndex.forEachNear(dimension, pos.x(), pos.z(), radius, pinId -> {
            if (!visited.add(pinId)) {
                return;
            }
            EchoPin pin = pins.get(pinId);
            if (pin == null) {
                return;
            }
            if (pin.anchor().renderPos().distanceSquaredTo(pos) <= radiusSq) {
                result.add(pin);
            }
        });
        return result;
    }

    @Override
    public List<EchoPin> findByAuthor(UUID authorUuid) {
        Set<PinId> ids = byAuthor.get(authorUuid);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<EchoPin> result = new ArrayList<>(ids.size());
        for (PinId id : ids) {
            EchoPin pin = pins.get(id);
            if (pin != null) {
                result.add(pin);
            }
        }
        return result;
    }

    @Override
    public int countByAuthor(UUID authorUuid) {
        Set<PinId> ids = byAuthor.get(authorUuid);
        return ids == null ? 0 : ids.size();
    }

    @Override
    public int totalCount() {
        return pins.size();
    }

    @Override
    public int countInDimension(DimensionId dimension) {
        return countByDimension.getOrDefault(dimension, 0);
    }

    @Override
    public List<EchoPin> findExpired(long nowMillis, int limit) {
        if (limit <= 0 || nowMillis < earliestExpiry) {
            return List.of();
        }
        List<EchoPin> expired = new ArrayList<>();
        long nextEarliest = Long.MAX_VALUE;
        for (EchoPin pin : pins.values()) {
            if (pin.isPermanent()) {
                continue;
            }
            // The hint is computed over every pin still stored, including the ones being
            // returned. Excluding them would assume the caller always deletes what it is
            // handed; if a deletion ever failed, those pins would be skipped by the early-out
            // on every later sweep and would never expire.
            nextEarliest = Math.min(nextEarliest, pin.expiresAt());
            if (expired.size() < limit && pin.isExpiredAt(nowMillis)) {
                expired.add(pin);
            }
        }
        earliestExpiry = nextEarliest;
        return expired;
    }

    @Override
    public Collection<EchoPin> all() {
        return Collections.unmodifiableCollection(pins.values());
    }

    @Override
    public void markDirty() {
        // No-op in memory. Persistence overrides this to flag the SavedData.
    }

    /** Drops all state. Used when loading a fresh snapshot from disk. */
    protected void clearAll() {
        pins.clear();
        spatialIndex.clear();
        byAuthor.clear();
        countByDimension.clear();
        earliestExpiry = Long.MAX_VALUE;
    }

    /**
     * Inserts without marking dirty. Used while loading from disk, where flagging every pin
     * would immediately re-dirty freshly loaded data.
     */
    protected void loadPin(EchoPin pin) {
        EchoPin previous = pins.put(pin.id(), pin);
        if (previous != null) {
            unindex(previous);
        }
        index(pin);
    }

    public ChunkSpatialIndex spatialIndex() {
        return spatialIndex;
    }

    private void index(EchoPin pin) {
        WorldPos pos = pin.anchor().renderPos();
        spatialIndex.add(pin.anchor().dimension(), pos.chunkX(), pos.chunkZ(), pin.id());
        byAuthor.computeIfAbsent(pin.authorUuid(), k -> new HashSet<>()).add(pin.id());
        countByDimension.merge(pin.anchor().dimension(), 1, Integer::sum);
        if (!pin.isPermanent()) {
            earliestExpiry = Math.min(earliestExpiry, pin.expiresAt());
        }
    }

    private void unindex(EchoPin pin) {
        WorldPos pos = pin.anchor().renderPos();
        spatialIndex.remove(pin.anchor().dimension(), pos.chunkX(), pos.chunkZ(), pin.id());
        Set<PinId> authored = byAuthor.get(pin.authorUuid());
        if (authored != null) {
            authored.remove(pin.id());
            if (authored.isEmpty()) {
                byAuthor.remove(pin.authorUuid());
            }
        }
        countByDimension.computeIfPresent(pin.anchor().dimension(),
                (dim, count) -> count <= 1 ? null : count - 1);
        // earliestExpiry is only ever lowered here; findExpired recomputes it on each sweep,
        // so a stale-low value costs at most one extra scan and never misses an expiry.
    }
}
