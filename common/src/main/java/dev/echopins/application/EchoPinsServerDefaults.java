package dev.echopins.application;

/**
 * Default values for every server option.
 *
 * <p>Single source of truth for both loaders. NeoForge stores configuration through its own config
 * system and Fabric through a JSON file; keeping the numbers here means neither can quietly
 * disagree with the other about what an untouched install does.
 *
 * <p>Chosen so that an admin who never opens the config still gets a server that cannot be filled
 * up: pins expire after a week, storage is capped, and creating and playing are both rate limited.
 */
public final class EchoPinsServerDefaults {

    public static final boolean ENABLED = true;
    public static final int OPERATOR_PERMISSION_LEVEL = 2;
    public static final boolean OPERATOR_BYPASS_LIMITS = true;

    public static final int MAX_RECORDING_SECONDS = 30;
    public static final int MIN_RECORDING_MILLIS = 700;
    public static final int RECORDING_SESSION_TIMEOUT_SECONDS = 60;
    public static final boolean SUPPRESS_PROXIMITY_WHILE_RECORDING = true;

    public static final int MAX_PINS_PER_PLAYER = 64;
    public static final int MAX_TOTAL_PINS = 5_000;
    public static final int MAX_PINS_NEARBY = 16;
    public static final int MAX_CAPTION_LENGTH = 96;
    public static final int MAX_PRIVATE_RECIPIENTS = 16;

    public static final double DISCOVERY_RADIUS = 56.0D;
    public static final double INTERACTION_RADIUS = 6.0D;
    public static final double MAX_CREATION_DISTANCE = 8.0D;
    public static final double PLAYBACK_AUDIO_DISTANCE = 16.0D;
    public static final int MAX_SYNCED_PINS_PER_PLAYER = 64;
    public static final int SYNC_INTERVAL_TICKS = 20;

    /** One real-world week. */
    public static final int DEFAULT_EXPIRY_HOURS = 168;
    public static final boolean ALLOW_PERMANENT_PINS = true;

    public static final int CREATE_COOLDOWN_SECONDS = 5;
    public static final int PLAYBACK_COOLDOWN_MILLIS = 400;
    public static final int MAX_CONCURRENT_PLAYBACKS_PER_PLAYER = 3;
    public static final int REQUEST_BURST_CAPACITY = 30;
    public static final double REQUEST_REFILL_PER_SECOND = 10.0D;

    public static final int MAX_AUDIO_BYTES_PER_PIN = 262_144;
    /** 1 GiB. */
    public static final long MAX_TOTAL_AUDIO_STORAGE_BYTES = 1_073_741_824L;

    public static final boolean EXPIRED_PIN_CLEANUP = true;
    public static final int EXPIRED_PIN_CLEANUP_INTERVAL_SECONDS = 60;
    public static final int EXPIRED_PIN_CLEANUP_BATCH = 32;
    public static final boolean ORPHAN_CLEANUP = true;
    public static final int ORPHAN_CLEANUP_INTERVAL_MINUTES = 30;

    private EchoPinsServerDefaults() {
    }
}
