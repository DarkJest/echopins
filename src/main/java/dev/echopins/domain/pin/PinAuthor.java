package dev.echopins.domain.pin;

import java.util.Objects;
import java.util.UUID;

/**
 * The creator of a pin.
 *
 * <p>{@code uuid} is the only identity used for any access decision. {@code lastKnownName} is a
 * display convenience that is refreshed when the player is seen again, and must never be used
 * for authorization - names are reusable and client-supplied names are not trustworthy.
 *
 * @param uuid          the author's player UUID
 * @param lastKnownName the name to show in UI when the author is offline
 */
public record PinAuthor(UUID uuid, String lastKnownName) {

    /** Minecraft names are at most 16 characters; the slack absorbs unusual display names. */
    public static final int MAX_NAME_LENGTH = 32;

    public PinAuthor {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        lastKnownName = sanitize(lastKnownName);
    }

    public PinAuthor withName(String newName) {
        return new PinAuthor(uuid, newName);
    }

    private static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_NAME_LENGTH));
        raw.codePoints()
                .filter(cp -> !Character.isISOControl(cp))
                .forEach(cp -> {
                    if (out.length() < MAX_NAME_LENGTH) {
                        out.appendCodePoint(cp);
                    }
                });
        String cleaned = out.toString().trim();
        return cleaned.isEmpty() ? "?" : cleaned;
    }
}
