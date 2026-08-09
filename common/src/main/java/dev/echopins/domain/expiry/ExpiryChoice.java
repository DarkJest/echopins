package dev.echopins.domain.expiry;

/**
 * The expiry options a player may pick when saving a pin. The concrete durations come from
 * server config, so the client never chooses a raw timestamp.
 */
public enum ExpiryChoice {
    /** A deliberately short-lived note, for example "I'm heading left, back in a minute". */
    SHORT(0),
    /** The server's configured default. */
    DEFAULT(1),
    /** Never expires. Only honoured if the server allows permanent pins. */
    PERMANENT(2);

    private final int id;

    ExpiryChoice(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static ExpiryChoice byId(int id) {
        for (ExpiryChoice choice : values()) {
            if (choice.id == id) {
                return choice;
            }
        }
        return DEFAULT;
    }
}
