package dev.echopins.fabric.config;

import dev.echopins.application.EchoPinsServerDefaults;
import dev.echopins.application.ServerLimits;

/**
 * {@link ServerLimits} backed by {@code config/echopins-server.json}.
 *
 * <p>Fabric has no config-reload event to hook, so values are read once into an immutable
 * {@link Snapshot} and held. {@code /echopins admin reload} swaps the snapshot for a freshly read
 * one.
 *
 * <p>The swap is why this class is a facade rather than simply being replaced wholesale:
 * {@code EchoPinsServer} captures one {@code ServerLimits} at start-up and holds that reference for
 * the server's life, so replacing the object would leave the running server reading the old values
 * for ever. The identity stays put and only the snapshot inside it moves.
 */
public final class FabricServerLimits implements ServerLimits {

    public static final FabricServerLimits INSTANCE = new FabricServerLimits();

    /** Volatile so a reload on the command thread is visible to the server thread. */
    private volatile Snapshot snapshot = new Snapshot(FabricConfig.load(FabricConfig.Files_.SERVER));

    private FabricServerLimits() {
    }

    public static FabricServerLimits get() {
        return INSTANCE;
    }

    /** Re-reads the file. Wired to {@code /echopins admin reload} through {@link ServerLimits}. */
    @Override
    public void reload() {
        snapshot = new Snapshot(FabricConfig.load(FabricConfig.Files_.SERVER));
    }

    @Override
    public boolean enabled() {
        return snapshot.enabled;
    }

    @Override
    public int maxRecordingSeconds() {
        return snapshot.maxRecordingSeconds;
    }

    @Override
    public int minRecordingMillis() {
        return snapshot.minRecordingMillis;
    }

    @Override
    public int recordingSessionTimeoutSeconds() {
        return snapshot.recordingSessionTimeoutSeconds;
    }

    @Override
    public boolean suppressProximityBroadcastWhileRecording() {
        return snapshot.suppressProximity;
    }

    @Override
    public int maxPinsPerPlayer() {
        return snapshot.maxPinsPerPlayer;
    }

    @Override
    public int maxTotalPins() {
        return snapshot.maxTotalPins;
    }

    @Override
    public int maxPinsNearby() {
        return snapshot.maxPinsNearby;
    }

    @Override
    public int maxCaptionLength() {
        return snapshot.maxCaptionLength;
    }

    @Override
    public int maxPrivateRecipients() {
        return snapshot.maxPrivateRecipients;
    }

    @Override
    public double discoveryRadius() {
        return snapshot.discoveryRadius;
    }

    @Override
    public double interactionRadius() {
        return snapshot.interactionRadius;
    }

    @Override
    public double maxCreationDistance() {
        return snapshot.maxCreationDistance;
    }

    @Override
    public double playbackAudioDistance() {
        return snapshot.playbackAudioDistance;
    }

    @Override
    public int maxSyncedPinsPerPlayer() {
        return snapshot.maxSyncedPinsPerPlayer;
    }

    @Override
    public int syncIntervalTicks() {
        return snapshot.syncIntervalTicks;
    }

    @Override
    public int defaultExpiryHours() {
        return snapshot.defaultExpiryHours;
    }

    @Override
    public boolean allowPermanentPins() {
        return snapshot.allowPermanentPins;
    }

    @Override
    public int createCooldownSeconds() {
        return snapshot.createCooldownSeconds;
    }

    @Override
    public int playbackCooldownMillis() {
        return snapshot.playbackCooldownMillis;
    }

    @Override
    public int maxConcurrentPlaybacksPerPlayer() {
        return snapshot.maxConcurrentPlaybacksPerPlayer;
    }

    @Override
    public int requestBurstCapacity() {
        return snapshot.requestBurstCapacity;
    }

    @Override
    public double requestRefillPerSecond() {
        return snapshot.requestRefillPerSecond;
    }

    @Override
    public int maxAudioBytesPerPin() {
        return snapshot.maxAudioBytesPerPin;
    }

    @Override
    public long maxTotalAudioStorageBytes() {
        return snapshot.maxTotalAudioStorageBytes;
    }

    @Override
    public boolean expiredPinCleanup() {
        return snapshot.expiredPinCleanup;
    }

    @Override
    public int expiredPinCleanupIntervalSeconds() {
        return snapshot.expiredPinCleanupIntervalSeconds;
    }

    @Override
    public int expiredPinCleanupBatch() {
        return snapshot.expiredPinCleanupBatch;
    }

    @Override
    public boolean orphanCleanup() {
        return snapshot.orphanCleanup;
    }

    @Override
    public int orphanCleanupIntervalMinutes() {
        return snapshot.orphanCleanupIntervalMinutes;
    }

    @Override
    public boolean operatorBypassLimits() {
        return snapshot.operatorBypassLimits;
    }

    @Override
    public int operatorPermissionLevel() {
        return snapshot.operatorPermissionLevel;
    }

    /** One consistent reading of the file. Every field is final, so a snapshot cannot tear. */
    private static final class Snapshot {

        private final boolean enabled;
        private final int operatorPermissionLevel;
        private final boolean operatorBypassLimits;
        private final int maxRecordingSeconds;
        private final int minRecordingMillis;
        private final int recordingSessionTimeoutSeconds;
        private final boolean suppressProximity;
        private final int maxPinsPerPlayer;
        private final int maxTotalPins;
        private final int maxPinsNearby;
        private final int maxCaptionLength;
        private final int maxPrivateRecipients;
        private final double discoveryRadius;
        private final double interactionRadius;
        private final double maxCreationDistance;
        private final double playbackAudioDistance;
        private final int maxSyncedPinsPerPlayer;
        private final int syncIntervalTicks;
        private final int defaultExpiryHours;
        private final boolean allowPermanentPins;
        private final int createCooldownSeconds;
        private final int playbackCooldownMillis;
        private final int maxConcurrentPlaybacksPerPlayer;
        private final int requestBurstCapacity;
        private final double requestRefillPerSecond;
        private final int maxAudioBytesPerPin;
        private final long maxTotalAudioStorageBytes;
        private final boolean expiredPinCleanup;
        private final int expiredPinCleanupIntervalSeconds;
        private final int expiredPinCleanupBatch;
        private final boolean orphanCleanup;
        private final int orphanCleanupIntervalMinutes;

        private Snapshot(FabricConfig c) {
            // Bounds mirror the NeoForge config exactly; a hand-edited file is no more trustworthy
            // than a hand-edited TOML.
            enabled = c.getBoolean("enabled", EchoPinsServerDefaults.ENABLED);
            operatorPermissionLevel = c.getInt("operatorPermissionLevel",
                    EchoPinsServerDefaults.OPERATOR_PERMISSION_LEVEL, 0, 4);
            operatorBypassLimits = c.getBoolean("operatorBypassLimits",
                    EchoPinsServerDefaults.OPERATOR_BYPASS_LIMITS);

            maxRecordingSeconds = c.getInt("maxRecordingSeconds",
                    EchoPinsServerDefaults.MAX_RECORDING_SECONDS, 1, 600);
            minRecordingMillis = c.getInt("minRecordingMillis",
                    EchoPinsServerDefaults.MIN_RECORDING_MILLIS, 100, 10_000);
            recordingSessionTimeoutSeconds = c.getInt("recordingSessionTimeoutSeconds",
                    EchoPinsServerDefaults.RECORDING_SESSION_TIMEOUT_SECONDS, 5, 900);
            suppressProximity = c.getBoolean("suppressProximityBroadcastWhileRecording",
                    EchoPinsServerDefaults.SUPPRESS_PROXIMITY_WHILE_RECORDING);

            maxPinsPerPlayer = c.getInt("maxPinsPerPlayer",
                    EchoPinsServerDefaults.MAX_PINS_PER_PLAYER, 1, 100_000);
            maxTotalPins = c.getInt("maxTotalPins", EchoPinsServerDefaults.MAX_TOTAL_PINS, 1, 1_000_000);
            maxPinsNearby = c.getInt("maxPinsNearby", EchoPinsServerDefaults.MAX_PINS_NEARBY, 1, 512);
            maxCaptionLength = c.getInt("maxCaptionLength",
                    EchoPinsServerDefaults.MAX_CAPTION_LENGTH, 0, 256);
            maxPrivateRecipients = c.getInt("maxPrivateRecipients",
                    EchoPinsServerDefaults.MAX_PRIVATE_RECIPIENTS, 1, 64);

            discoveryRadius = c.getDouble("discoveryRadius",
                    EchoPinsServerDefaults.DISCOVERY_RADIUS, 8.0D, 256.0D);
            interactionRadius = c.getDouble("interactionRadius",
                    EchoPinsServerDefaults.INTERACTION_RADIUS, 1.0D, 64.0D);
            maxCreationDistance = c.getDouble("maxCreationDistance",
                    EchoPinsServerDefaults.MAX_CREATION_DISTANCE, 1.0D, 64.0D);
            playbackAudioDistance = c.getDouble("playbackAudioDistance",
                    EchoPinsServerDefaults.PLAYBACK_AUDIO_DISTANCE, 2.0D, 128.0D);
            maxSyncedPinsPerPlayer = c.getInt("maxSyncedPinsPerPlayer",
                    EchoPinsServerDefaults.MAX_SYNCED_PINS_PER_PLAYER, 8, 512);
            syncIntervalTicks = c.getInt("syncIntervalTicks",
                    EchoPinsServerDefaults.SYNC_INTERVAL_TICKS, 5, 200);

            defaultExpiryHours = c.getInt("defaultExpiryHours",
                    EchoPinsServerDefaults.DEFAULT_EXPIRY_HOURS, 0, 8_760);
            allowPermanentPins = c.getBoolean("allowPermanentPins",
                    EchoPinsServerDefaults.ALLOW_PERMANENT_PINS);

            createCooldownSeconds = c.getInt("createCooldownSeconds",
                    EchoPinsServerDefaults.CREATE_COOLDOWN_SECONDS, 0, 3_600);
            playbackCooldownMillis = c.getInt("playbackCooldownMillis",
                    EchoPinsServerDefaults.PLAYBACK_COOLDOWN_MILLIS, 0, 60_000);
            maxConcurrentPlaybacksPerPlayer = c.getInt("maxConcurrentPlaybacksPerPlayer",
                    EchoPinsServerDefaults.MAX_CONCURRENT_PLAYBACKS_PER_PLAYER, 1, 16);
            requestBurstCapacity = c.getInt("requestBurstCapacity",
                    EchoPinsServerDefaults.REQUEST_BURST_CAPACITY, 1, 500);
            requestRefillPerSecond = c.getDouble("requestRefillPerSecond",
                    EchoPinsServerDefaults.REQUEST_REFILL_PER_SECOND, 0.1D, 200.0D);

            maxAudioBytesPerPin = c.getInt("maxAudioBytesPerPin",
                    EchoPinsServerDefaults.MAX_AUDIO_BYTES_PER_PIN, 1_024, 8_388_608);
            maxTotalAudioStorageBytes = c.getLong("maxTotalAudioStorageBytes",
                    EchoPinsServerDefaults.MAX_TOTAL_AUDIO_STORAGE_BYTES,
                    1_048_576L, 1_099_511_627_776L);

            expiredPinCleanup = c.getBoolean("expiredPinCleanup",
                    EchoPinsServerDefaults.EXPIRED_PIN_CLEANUP);
            expiredPinCleanupIntervalSeconds = c.getInt("expiredPinCleanupIntervalSeconds",
                    EchoPinsServerDefaults.EXPIRED_PIN_CLEANUP_INTERVAL_SECONDS, 5, 3_600);
            expiredPinCleanupBatch = c.getInt("expiredPinCleanupBatch",
                    EchoPinsServerDefaults.EXPIRED_PIN_CLEANUP_BATCH, 1, 1_000);
            orphanCleanup = c.getBoolean("orphanCleanup", EchoPinsServerDefaults.ORPHAN_CLEANUP);
            orphanCleanupIntervalMinutes = c.getInt("orphanCleanupIntervalMinutes",
                    EchoPinsServerDefaults.ORPHAN_CLEANUP_INTERVAL_MINUTES, 1, 1_440);

            c.flush();
        }
    }
}
