package dev.echopins.domain.repository;

import dev.echopins.domain.pin.PinId;

import java.util.Collection;
import java.util.UUID;

/**
 * Tracks which pins a player has already listened to.
 *
 * <p>Read state is kept per player rather than as a listener set on each pin. Storing listeners
 * on the pin would make every pin grow with the size of the player base; storing read pins per
 * player bounds the data by the number of pins that actually exist, which is already capped by
 * {@code maxTotalPins} and shrinks as pins expire.
 *
 * <p>Implementations must drop entries for deleted pins via {@link #forgetPin}, otherwise the
 * set would accumulate ids of pins that no longer exist.
 */
public interface ReadStateRepository {

    boolean isRead(UUID player, PinId pin);

    void markRead(UUID player, PinId pin);

    /** Counts how many of {@code candidates} the player has not listened to yet. */
    int countUnread(UUID player, Collection<PinId> candidates);

    /** Removes this pin from every player's read set. Called when a pin is deleted or expires. */
    void forgetPin(PinId pin);

    /** Drops all read state for a player. */
    void forgetPlayer(UUID player);

    void markDirty();
}
