package dev.echopins.domain.pin;

import java.util.Objects;
import java.util.Optional;

/**
 * An optional short text label shown next to a pin, and the accessible fallback for players who
 * cannot or do not want to listen to audio.
 *
 * <p>{@link #HARD_MAX_LENGTH} is a domain-level ceiling that holds regardless of what the server
 * config allows. The configurable limit is applied on top of it by the application layer, so a
 * misconfigured server still cannot write unbounded strings into persistence or into a network
 * payload.
 */
public record Caption(String text) {

    public static final int HARD_MAX_LENGTH = 256;

    public Caption {
        Objects.requireNonNull(text, "text");
        text = sanitize(text);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Caption is empty after sanitization");
        }
    }

    /**
     * Builds a caption from untrusted input, returning empty rather than throwing when the input
     * is blank or becomes blank once control characters are removed.
     *
     * @param raw     untrusted text, may be {@code null}
     * @param maxLength the configured maximum, clamped to {@link #HARD_MAX_LENGTH}
     */
    public static Optional<Caption> ofNullable(String raw, int maxLength) {
        if (raw == null) {
            return Optional.empty();
        }
        String cleaned = sanitize(raw);
        int limit = Math.max(0, Math.min(maxLength, HARD_MAX_LENGTH));
        if (cleaned.length() > limit) {
            cleaned = cleaned.substring(0, limit).trim();
        }
        return cleaned.isEmpty() ? Optional.empty() : Optional.of(new Caption(cleaned));
    }

    /**
     * Removes control characters (including newlines, which would let a caption break out of a
     * single-line tooltip) and collapses runs of whitespace.
     */
    private static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(Math.min(raw.length(), HARD_MAX_LENGTH));
        boolean lastWasSpace = true;
        for (int i = 0; i < raw.length() && out.length() < HARD_MAX_LENGTH; ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                // Drop the section sign together with the character it would have coloured,
                // matching how Minecraft itself strips formatting. Removing only the sign would
                // leave the stray format letter visible in the caption.
                if (i < raw.length()) {
                    i += Character.charCount(raw.codePointAt(i));
                }
                cp = ' ';
            } else if (Character.isISOControl(cp)) {
                cp = ' ';
            }
            if (Character.isWhitespace(cp)) {
                if (!lastWasSpace) {
                    out.append(' ');
                    lastWasSpace = true;
                }
            } else {
                out.appendCodePoint(cp);
                lastWasSpace = false;
            }
        }
        return out.toString().trim();
    }

    @Override
    public String toString() {
        return text;
    }
}
