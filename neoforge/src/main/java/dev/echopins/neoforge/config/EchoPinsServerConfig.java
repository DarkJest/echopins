package dev.echopins.neoforge.config;

import dev.echopins.application.EchoPinsServerDefaults;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-authoritative limits, security and storage settings.
 *
 * <p>Every value has a validated range. The client is never allowed to raise any of these; the
 * client config can only make its own view more restrictive.
 *
 * <p>Defaults are chosen so that an admin who installs the mod and never opens the config still
 * gets a server that cannot be filled up: pins expire after a week, storage is capped, and both
 * creation and playback are rate limited.
 */
public final class EchoPinsServerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;

    public static final ModConfigSpec.IntValue MAX_RECORDING_SECONDS;
    public static final ModConfigSpec.IntValue MIN_RECORDING_MILLIS;
    public static final ModConfigSpec.IntValue RECORDING_SESSION_TIMEOUT_SECONDS;
    public static final ModConfigSpec.BooleanValue SUPPRESS_PROXIMITY_WHILE_RECORDING;

    public static final ModConfigSpec.IntValue MAX_PINS_PER_PLAYER;
    public static final ModConfigSpec.IntValue MAX_TOTAL_PINS;
    public static final ModConfigSpec.IntValue MAX_PINS_NEARBY;
    public static final ModConfigSpec.IntValue MAX_CAPTION_LENGTH;
    public static final ModConfigSpec.IntValue MAX_PRIVATE_RECIPIENTS;

    public static final ModConfigSpec.DoubleValue DISCOVERY_RADIUS;
    public static final ModConfigSpec.DoubleValue INTERACTION_RADIUS;
    public static final ModConfigSpec.DoubleValue MAX_CREATION_DISTANCE;
    public static final ModConfigSpec.DoubleValue PLAYBACK_AUDIO_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_SYNCED_PINS_PER_PLAYER;
    public static final ModConfigSpec.IntValue SYNC_INTERVAL_TICKS;

    public static final ModConfigSpec.IntValue DEFAULT_EXPIRY_HOURS;
    public static final ModConfigSpec.BooleanValue ALLOW_PERMANENT_PINS;

    public static final ModConfigSpec.IntValue CREATE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue PLAYBACK_COOLDOWN_MILLIS;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_PLAYBACKS_PER_PLAYER;
    public static final ModConfigSpec.IntValue REQUEST_BURST_CAPACITY;
    public static final ModConfigSpec.DoubleValue REQUEST_REFILL_PER_SECOND;

    public static final ModConfigSpec.IntValue MAX_AUDIO_BYTES_PER_PIN;
    public static final ModConfigSpec.LongValue MAX_TOTAL_AUDIO_STORAGE_BYTES;

    public static final ModConfigSpec.BooleanValue ORPHAN_CLEANUP;
    public static final ModConfigSpec.IntValue ORPHAN_CLEANUP_INTERVAL_MINUTES;
    public static final ModConfigSpec.BooleanValue EXPIRED_PIN_CLEANUP;
    public static final ModConfigSpec.IntValue EXPIRED_PIN_CLEANUP_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue EXPIRED_PIN_CLEANUP_BATCH;

    public static final ModConfigSpec.BooleanValue OPERATOR_BYPASS_LIMITS;
    public static final ModConfigSpec.IntValue OPERATOR_PERMISSION_LEVEL;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("EchoPins server settings.",
                        "These are authoritative: clients cannot raise any of these limits.")
                .push("general");
        ENABLED = BUILDER
                .comment("Master switch. When false, EchoPins accepts no requests and renders nothing.")
                .translation("echopins.config.enabled")
                .define("enabled", EchoPinsServerDefaults.ENABLED);
        OPERATOR_PERMISSION_LEVEL = BUILDER
                .comment("Vanilla permission level required for EchoPins admin commands.")
                .translation("echopins.config.operator_permission_level")
                .defineInRange("operatorPermissionLevel", EchoPinsServerDefaults.OPERATOR_PERMISSION_LEVEL, 0, 4);
        OPERATOR_BYPASS_LIMITS = BUILDER
                .comment("Whether operators bypass pin count, cooldown and storage limits.")
                .translation("echopins.config.operator_bypass_limits")
                .define("operatorBypassLimits", EchoPinsServerDefaults.OPERATOR_BYPASS_LIMITS);
        BUILDER.pop();

        BUILDER.comment("Recording limits.").push("recording");
        MAX_RECORDING_SECONDS = BUILDER
                .comment("Longest recording a player may make, in seconds.")
                .translation("echopins.config.max_recording_seconds")
                .defineInRange("maxRecordingSeconds", EchoPinsServerDefaults.MAX_RECORDING_SECONDS, 1, 600);
        MIN_RECORDING_MILLIS = BUILDER
                .comment("Shortest recording that will be saved.",
                        "Stops an accidental key tap from creating an empty pin.")
                .translation("echopins.config.min_recording_millis")
                .defineInRange("minRecordingMillis", EchoPinsServerDefaults.MIN_RECORDING_MILLIS, 100, 10_000);
        RECORDING_SESSION_TIMEOUT_SECONDS = BUILDER
                .comment("A recording session with no incoming audio for this long is cancelled.",
                        "Protects against sessions leaking if a client stops responding.")
                .translation("echopins.config.recording_session_timeout_seconds")
                .defineInRange("recordingSessionTimeoutSeconds", EchoPinsServerDefaults.RECORDING_SESSION_TIMEOUT_SECONDS, 5, 900);
        SUPPRESS_PROXIMITY_WHILE_RECORDING = BUILDER
                .comment("While a player is recording an EchoPin, stop their voice from also being",
                        "broadcast to nearby players. Recommended: recording a note is not the same",
                        "as talking to whoever happens to be standing next to you.")
                .translation("echopins.config.suppress_proximity_while_recording")
                .define("suppressProximityBroadcastWhileRecording", EchoPinsServerDefaults.SUPPRESS_PROXIMITY_WHILE_RECORDING);
        BUILDER.pop();

        BUILDER.comment("How many pins may exist.").push("limits");
        MAX_PINS_PER_PLAYER = BUILDER
                .comment("Maximum pins a single player may own.")
                .translation("echopins.config.max_pins_per_player")
                .defineInRange("maxPinsPerPlayer", EchoPinsServerDefaults.MAX_PINS_PER_PLAYER, 1, 100_000);
        MAX_TOTAL_PINS = BUILDER
                .comment("Maximum pins on the whole server.")
                .translation("echopins.config.max_total_pins")
                .defineInRange("maxTotalPins", EchoPinsServerDefaults.MAX_TOTAL_PINS, 1, 1_000_000);
        MAX_PINS_NEARBY = BUILDER
                .comment("Maximum pins allowed within the interaction radius of a new pin.",
                        "Keeps one spot from being carpeted with markers.")
                .translation("echopins.config.max_pins_nearby")
                .defineInRange("maxPinsNearby", EchoPinsServerDefaults.MAX_PINS_NEARBY, 1, 512);
        MAX_CAPTION_LENGTH = BUILDER
                .comment("Maximum caption length in characters. 0 disables captions.")
                .translation("echopins.config.max_caption_length")
                .defineInRange("maxCaptionLength", EchoPinsServerDefaults.MAX_CAPTION_LENGTH, 0, 256);
        MAX_PRIVATE_RECIPIENTS = BUILDER
                .comment("Maximum explicit recipients on a private pin.")
                .translation("echopins.config.max_private_recipients")
                .defineInRange("maxPrivateRecipients", EchoPinsServerDefaults.MAX_PRIVATE_RECIPIENTS, 1, 64);
        BUILDER.pop();

        BUILDER.comment("Discovery and synchronisation.").push("discovery");
        DISCOVERY_RADIUS = BUILDER
                .comment("Distance in blocks at which a player starts to see a pin marker.")
                .translation("echopins.config.discovery_radius")
                .defineInRange("discoveryRadius", EchoPinsServerDefaults.DISCOVERY_RADIUS, 8.0D, 256.0D);
        INTERACTION_RADIUS = BUILDER
                .comment("Distance in blocks within which a player may play or create a pin.")
                .translation("echopins.config.interaction_radius")
                .defineInRange("interactionRadius", EchoPinsServerDefaults.INTERACTION_RADIUS, 1.0D, 64.0D);
        MAX_CREATION_DISTANCE = BUILDER
                .comment("Maximum distance between a player and the anchor they ask to create.",
                        "Validated server-side; a client claiming a further anchor is rejected.")
                .translation("echopins.config.max_creation_distance")
                .defineInRange("maxCreationDistance", EchoPinsServerDefaults.MAX_CREATION_DISTANCE, 1.0D, 64.0D);
        PLAYBACK_AUDIO_DISTANCE = BUILDER
                .comment("Distance in blocks over which EchoPin playback can be heard.")
                .translation("echopins.config.playback_audio_distance")
                .defineInRange("playbackAudioDistance", EchoPinsServerDefaults.PLAYBACK_AUDIO_DISTANCE, 2.0D, 128.0D);
        MAX_SYNCED_PINS_PER_PLAYER = BUILDER
                .comment("Maximum pins synchronised to one player at a time.",
                        "Bounds both network traffic and client-side rendering work.")
                .translation("echopins.config.max_synced_pins_per_player")
                .defineInRange("maxSyncedPinsPerPlayer", EchoPinsServerDefaults.MAX_SYNCED_PINS_PER_PLAYER, 8, 512);
        SYNC_INTERVAL_TICKS = BUILDER
                .comment("Minimum ticks between subscription recalculations for one player.")
                .translation("echopins.config.sync_interval_ticks")
                .defineInRange("syncIntervalTicks", EchoPinsServerDefaults.SYNC_INTERVAL_TICKS, 5, 200);
        BUILDER.pop();

        BUILDER.comment("Expiry.").push("expiry");
        DEFAULT_EXPIRY_HOURS = BUILDER
                .comment("Default pin lifetime in hours. 0 means pins do not expire by default.",
                        "The default of 168 hours is one real-world week.")
                .translation("echopins.config.default_expiry_hours")
                .defineInRange("defaultExpiryHours", EchoPinsServerDefaults.DEFAULT_EXPIRY_HOURS, 0, 8_760);
        ALLOW_PERMANENT_PINS = BUILDER
                .comment("Whether players may choose to make a pin permanent.")
                .translation("echopins.config.allow_permanent_pins")
                .define("allowPermanentPins", EchoPinsServerDefaults.ALLOW_PERMANENT_PINS);
        BUILDER.pop();

        BUILDER.comment("Rate limiting and abuse protection.").push("rate_limits");
        CREATE_COOLDOWN_SECONDS = BUILDER
                .comment("Minimum seconds between two pins created by the same player. 0 disables.")
                .translation("echopins.config.create_cooldown_seconds")
                .defineInRange("createCooldownSeconds", EchoPinsServerDefaults.CREATE_COOLDOWN_SECONDS, 0, 3_600);
        PLAYBACK_COOLDOWN_MILLIS = BUILDER
                .comment("Minimum milliseconds between two playback requests. 0 disables.")
                .translation("echopins.config.playback_cooldown_millis")
                .defineInRange("playbackCooldownMillis", EchoPinsServerDefaults.PLAYBACK_COOLDOWN_MILLIS, 0, 60_000);
        MAX_CONCURRENT_PLAYBACKS_PER_PLAYER = BUILDER
                .comment("How many EchoPins one player may have playing at once.")
                .translation("echopins.config.max_concurrent_playbacks_per_player")
                .defineInRange("maxConcurrentPlaybacksPerPlayer", EchoPinsServerDefaults.MAX_CONCURRENT_PLAYBACKS_PER_PLAYER, 1, 16);
        REQUEST_BURST_CAPACITY = BUILDER
                .comment("Burst size of the general per-player request limiter.",
                        "Applies to every EchoPins packet and is the first line of defence",
                        "against a client spamming the server.")
                .translation("echopins.config.request_burst_capacity")
                .defineInRange("requestBurstCapacity", EchoPinsServerDefaults.REQUEST_BURST_CAPACITY, 1, 500);
        REQUEST_REFILL_PER_SECOND = BUILDER
                .comment("How fast the general request limiter refills, in requests per second.")
                .translation("echopins.config.request_refill_per_second")
                .defineInRange("requestRefillPerSecond", EchoPinsServerDefaults.REQUEST_REFILL_PER_SECOND, 0.1D, 200.0D);
        BUILDER.pop();

        BUILDER.comment("Audio storage.").push("storage");
        MAX_AUDIO_BYTES_PER_PIN = BUILDER
                .comment("Maximum stored audio size for one pin, in bytes.")
                .translation("echopins.config.max_audio_bytes_per_pin")
                .defineInRange("maxAudioBytesPerPin", EchoPinsServerDefaults.MAX_AUDIO_BYTES_PER_PIN, 1_024, 8_388_608);
        MAX_TOTAL_AUDIO_STORAGE_BYTES = BUILDER
                .comment("Maximum total bytes of EchoPins audio in the world save.",
                        "New pins are refused once this is reached. Default is 1 GiB.")
                .translation("echopins.config.max_total_audio_storage_bytes")
                .defineInRange("maxTotalAudioStorageBytes", EchoPinsServerDefaults.MAX_TOTAL_AUDIO_STORAGE_BYTES, 1_048_576L, 1_099_511_627_776L);
        BUILDER.pop();

        BUILDER.comment("Background cleanup.").push("cleanup");
        EXPIRED_PIN_CLEANUP = BUILDER
                .comment("Whether expired pins are removed automatically.")
                .translation("echopins.config.expired_pin_cleanup")
                .define("expiredPinCleanup", EchoPinsServerDefaults.EXPIRED_PIN_CLEANUP);
        EXPIRED_PIN_CLEANUP_INTERVAL_SECONDS = BUILDER
                .comment("Seconds between expiry sweeps.")
                .translation("echopins.config.expired_pin_cleanup_interval_seconds")
                .defineInRange("expiredPinCleanupIntervalSeconds", EchoPinsServerDefaults.EXPIRED_PIN_CLEANUP_INTERVAL_SECONDS, 5, 3_600);
        EXPIRED_PIN_CLEANUP_BATCH = BUILDER
                .comment("Maximum pins removed per sweep, so cleanup stays incremental.")
                .translation("echopins.config.expired_pin_cleanup_batch")
                .defineInRange("expiredPinCleanupBatch", EchoPinsServerDefaults.EXPIRED_PIN_CLEANUP_BATCH, 1, 1_000);
        ORPHAN_CLEANUP = BUILDER
                .comment("Whether audio files with no matching pin are deleted automatically.")
                .translation("echopins.config.orphan_cleanup")
                .define("orphanCleanup", EchoPinsServerDefaults.ORPHAN_CLEANUP);
        ORPHAN_CLEANUP_INTERVAL_MINUTES = BUILDER
                .comment("Minutes between orphan audio sweeps. These walk the audio directory,",
                        "so they run far less often than the expiry sweep.")
                .translation("echopins.config.orphan_cleanup_interval_minutes")
                .defineInRange("orphanCleanupIntervalMinutes", EchoPinsServerDefaults.ORPHAN_CLEANUP_INTERVAL_MINUTES, 1, 1_440);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private EchoPinsServerConfig() {
    }
}
