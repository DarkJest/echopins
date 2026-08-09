package dev.echopins.domain.event;

import dev.echopins.domain.anchor.DimensionId;
import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.pin.EchoPin;
import dev.echopins.domain.pin.PinId;

import java.util.UUID;

/**
 * Facts about pins that other subsystems react to.
 *
 * <p>The pin service publishes these instead of calling the sync manager, the audio store and
 * the read-state repository directly. That keeps creation and deletion logic from accumulating a
 * dependency on every consumer, and makes it possible to add a listener - a future map-mod
 * integration, say - without editing the service.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** Marker for everything published on the {@link DomainEventBus}. */
    public interface DomainEvent {
    }

    public record PinCreated(EchoPin pin) implements DomainEvent {
    }

    public record PinUpdated(EchoPin pin) implements DomainEvent {
    }

    /**
     * Carries the removed pin's location and author because listeners need them <em>after</em>
     * the pin is already gone from the repository.
     */
    public record PinRemoved(PinId id, UUID authorUuid, DimensionId dimension, WorldPos position,
                             java.util.UUID audioId) implements DomainEvent {
    }
}
