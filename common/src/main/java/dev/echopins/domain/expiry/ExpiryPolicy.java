package dev.echopins.domain.expiry;

import dev.echopins.domain.pin.EchoPin;

/**
 * Turns a player's {@link ExpiryChoice} into a concrete expiry timestamp.
 *
 * <p>Separated from the pin service so the "how long do pins live" rule can be swapped (for
 * example per-permission-group lifetimes) without touching creation logic.
 */
public interface ExpiryPolicy {

    /**
     * @param choice the requested expiry
     * @param now    current time, epoch millis
     * @return the expiry timestamp in epoch millis, or {@link EchoPin#NEVER_EXPIRES}
     */
    long resolveExpiry(ExpiryChoice choice, long now);

    /** Whether the server currently permits permanent pins. */
    boolean allowsPermanent();
}
