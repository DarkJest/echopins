package dev.echopins.domain.repository;

import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage and lookup of pins.
 *
 * <p>Callers never learn how pins are indexed. {@link #findNearby} is the only proximity query
 * the rest of the mod uses, which is what allows the implementation to keep a chunk index
 * without any caller iterating the full pin set - the difference between a server that handles
 * a handful of pins and one that handles thousands.
 */
public interface PinRepository {

    Optional<EchoPin> find(PinId id);

    /** Inserts or replaces a pin and keeps every index consistent. */
    void save(EchoPin pin);

    /**
     * Removes a pin.
     *
     * @return the removed pin, or empty if it was already gone. Deletion is idempotent.
     */
    Optional<EchoPin> remove(PinId id);

    /**
     * Pins within {@code radius} of {@code pos} in {@code dimension}.
     *
     * <p>Must not scan pins outside the searched chunk span.
     */
    List<EchoPin> findNearby(DimensionId dimension, WorldPos pos, double radius);

    List<EchoPin> findByAuthor(UUID authorUuid);

    int countByAuthor(UUID authorUuid);

    int totalCount();

    int countInDimension(DimensionId dimension);

    /**
     * Up to {@code limit} pins that expired at or before {@code nowMillis}. Bounded so cleanup
     * can run incrementally instead of stalling a tick on a huge sweep.
     */
    List<EchoPin> findExpired(long nowMillis, int limit);

    /** Every stored pin. Only for admin reporting and startup validation. */
    Collection<EchoPin> all();

    /** Marks backing storage dirty so the world save persists it. */
    void markDirty();
}
