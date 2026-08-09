package dev.echopins.domain.expiry;

import dev.echopins.domain.pin.EchoPin;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Expiry driven by the server config.
 *
 * <p>The suppliers are read on every call rather than captured once, so a config reload takes
 * effect without rebuilding the object graph.
 */
public final class ConfiguredExpiryPolicy implements ExpiryPolicy {

    /** A "short" pin is a fixed fraction of the default lifetime, floored at one hour. */
    private static final int SHORT_DIVISOR = 8;
    private static final long MIN_SHORT_HOURS = 1L;

    private final IntSupplier defaultExpiryHours;
    private final BooleanSupplier allowPermanent;

    public ConfiguredExpiryPolicy(IntSupplier defaultExpiryHours, BooleanSupplier allowPermanent) {
        this.defaultExpiryHours = Objects.requireNonNull(defaultExpiryHours, "defaultExpiryHours");
        this.allowPermanent = Objects.requireNonNull(allowPermanent, "allowPermanent");
    }

    @Override
    public long resolveExpiry(ExpiryChoice choice, long now) {
        int configuredHours = defaultExpiryHours.getAsInt();

        // A default of 0 hours means "pins do not expire unless the player asks for it".
        if (configuredHours <= 0 && choice != ExpiryChoice.SHORT) {
            return EchoPin.NEVER_EXPIRES;
        }

        return switch (choice) {
            case PERMANENT -> allowsPermanent()
                    ? EchoPin.NEVER_EXPIRES
                    // Server disallows permanent pins, so fall back to the default rather than
                    // rejecting the save outright and losing the player's recording.
                    : now + Duration.ofHours(Math.max(1, configuredHours)).toMillis();
            case SHORT -> {
                long hours = Math.max(MIN_SHORT_HOURS, Math.max(1, configuredHours) / SHORT_DIVISOR);
                yield now + Duration.ofHours(hours).toMillis();
            }
            case DEFAULT -> now + Duration.ofHours(configuredHours).toMillis();
        };
    }

    @Override
    public boolean allowsPermanent() {
        return allowPermanent.getAsBoolean();
    }
}
