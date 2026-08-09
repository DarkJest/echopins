package dev.echopins.domain.visibility;

/**
 * Who is allowed to discover and play a pin.
 *
 * <p>Hiding a pin on the client is presentation only. Every decision that depends on this enum
 * is re-evaluated on the server; see {@link AccessPolicy}.
 */
public enum Visibility {
    /** Anyone on the server may discover and play the pin. */
    PUBLIC(0),
    /** Only the author and the explicitly listed recipients. */
    PRIVATE(1);

    private final int id;

    Visibility(int id) {
        this.id = id;
    }

    /** Stable persisted identifier. Never renumber. */
    public int id() {
        return id;
    }

    public static Visibility byId(int id) {
        for (Visibility v : values()) {
            if (v.id == id) {
                return v;
            }
        }
        // A pin whose visibility byte is corrupt must not silently become public.
        return PRIVATE;
    }
}
