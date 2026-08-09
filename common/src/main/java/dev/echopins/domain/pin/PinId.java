package dev.echopins.domain.pin;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an EchoPin.
 *
 * <p>Wrapping the {@link UUID} keeps pin ids from being accidentally interchanged with player
 * ids, which matters because both flow through the same network payloads.
 */
public record PinId(UUID value) {

    public PinId {
        Objects.requireNonNull(value, "value");
    }

    public static PinId random() {
        return new PinId(UUID.randomUUID());
    }

    public static PinId of(UUID value) {
        return new PinId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
