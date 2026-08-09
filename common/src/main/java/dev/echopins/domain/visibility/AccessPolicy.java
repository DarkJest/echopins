package dev.echopins.domain.visibility;

import dev.echopins.domain.pin.EchoPin;

import java.util.UUID;

/**
 * Decides what a given player may do with a pin.
 *
 * <p>This is the single place access rules live. Every server-side entry point - discovery,
 * playback, deletion, inbox listing - funnels through it, so there is no code path where a rule
 * is applied in one place and forgotten in another.
 */
public interface AccessPolicy {

    /**
     * Whether the player may know the pin exists: see its marker, and see it in listings.
     *
     * @param pin      the pin
     * @param viewer   the player's UUID
     * @param operator whether the player has operator privileges
     */
    boolean canDiscover(EchoPin pin, UUID viewer, boolean operator);

    /**
     * Whether the player may play the recording back. Checked again server-side at playback
     * time, not just at discovery time, because a pin's visibility or the player's operator
     * status can change between the two.
     */
    boolean canPlay(EchoPin pin, UUID viewer, boolean operator);

    /** Whether the player may delete the pin. */
    boolean canDelete(EchoPin pin, UUID viewer, boolean operator);

    /** Whether the player may edit the pin's caption, visibility or expiry. */
    boolean canEdit(EchoPin pin, UUID viewer, boolean operator);
}
