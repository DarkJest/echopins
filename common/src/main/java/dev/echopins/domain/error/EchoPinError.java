package dev.echopins.domain.error;

/**
 * Every user-visible failure reason.
 *
 * <p>The server sends the stable {@link #id()} over the network and the client resolves the
 * translation key locally. That keeps server-side exception detail out of the player's UI while
 * still giving each player the message in their own language, and it means no user-facing string
 * is ever built on the server.
 *
 * <p>Ids are persisted in the wire protocol. Never renumber an existing constant.
 */
public enum EchoPinError {

    UNKNOWN(0, "error.unknown"),
    DISABLED(1, "error.disabled"),
    VOICE_CHAT_NOT_CONNECTED(2, "error.voice_chat_not_connected"),
    NOTHING_RECORDED(3, "error.nothing_recorded"),
    RECORDING_TOO_SHORT(4, "error.recording_too_short"),
    RECORDING_LIMIT_REACHED(5, "error.recording_limit_reached"),
    ALREADY_RECORDING(6, "error.already_recording"),
    NOT_RECORDING(7, "error.not_recording"),
    CANNOT_CREATE_HERE(8, "error.cannot_create_here"),
    PIN_NOT_FOUND(9, "error.pin_not_found"),
    NO_ACCESS(10, "error.no_access"),
    AUDIO_DAMAGED(11, "error.audio_damaged"),
    TOO_MANY_PINS_NEARBY(12, "error.too_many_pins_nearby"),
    CREATE_COOLDOWN(13, "error.create_cooldown"),
    PLAYBACK_COOLDOWN(14, "error.playback_cooldown"),
    TOO_MANY_PINS_OWNED(15, "error.too_many_pins_owned"),
    SERVER_PIN_LIMIT(16, "error.server_pin_limit"),
    STORAGE_FULL(17, "error.storage_full"),
    TOO_MANY_PLAYBACKS(18, "error.too_many_playbacks"),
    TOO_MANY_RECIPIENTS(19, "error.too_many_recipients"),
    RECORDING_EXPIRED(20, "error.recording_expired"),
    INTERNAL_ERROR(21, "error.internal_error"),
    /** The general per-player request limiter, as distinct from the create or playback cooldown. */
    RATE_LIMITED(22, "error.rate_limited"),
    /** Out of range to play a pin - distinct from not being able to *create* one here. */
    TOO_FAR_AWAY(23, "error.too_far_away");

    private static final EchoPinError[] BY_ID = buildIndex();

    private final int id;
    private final String translationSuffix;

    EchoPinError(int id, String translationSuffix) {
        this.id = id;
        this.translationSuffix = translationSuffix;
    }

    public int id() {
        return id;
    }

    /** Full translation key, for example {@code echopins.error.no_access}. */
    public String translationKey() {
        return "echopins." + translationSuffix;
    }

    public static EchoPinError byId(int id) {
        if (id < 0 || id >= BY_ID.length || BY_ID[id] == null) {
            return UNKNOWN;
        }
        return BY_ID[id];
    }

    private static EchoPinError[] buildIndex() {
        int max = 0;
        for (EchoPinError error : values()) {
            max = Math.max(max, error.id);
        }
        EchoPinError[] index = new EchoPinError[max + 1];
        for (EchoPinError error : values()) {
            index[error.id] = error;
        }
        return index;
    }
}
