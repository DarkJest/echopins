package dev.echopins.application;

/**
 * The server's tunable limits, as the application layer sees them.
 *
 * <p>An interface rather than direct config reads so services can be exercised in tests with
 * fixed values, and so nothing outside the infrastructure layer depends on NeoForge's config
 * types.
 */
public interface ServerLimits {

    /**
     * Re-reads the underlying configuration, if the implementation caches it.
     *
     * <p>Called by {@code /echopins admin reload}. NeoForge's config is read live and needs no
     * action, so the default does nothing; the Fabric implementation holds a snapshot and swaps it.
     * Whatever an implementation does here, the object identity must survive, because the server
     * captures one {@code ServerLimits} at start-up and holds it for its whole life.
     */
    default void reload() {
    }

    boolean enabled();

    int maxRecordingSeconds();

    int minRecordingMillis();

    int recordingSessionTimeoutSeconds();

    boolean suppressProximityBroadcastWhileRecording();

    int maxPinsPerPlayer();

    int maxTotalPins();

    int maxPinsNearby();

    int maxCaptionLength();

    int maxPrivateRecipients();

    double discoveryRadius();

    double interactionRadius();

    double maxCreationDistance();

    double playbackAudioDistance();

    int maxSyncedPinsPerPlayer();

    int syncIntervalTicks();

    int defaultExpiryHours();

    boolean allowPermanentPins();

    int createCooldownSeconds();

    int playbackCooldownMillis();

    int maxConcurrentPlaybacksPerPlayer();

    int requestBurstCapacity();

    double requestRefillPerSecond();

    int maxAudioBytesPerPin();

    long maxTotalAudioStorageBytes();

    boolean expiredPinCleanup();

    int expiredPinCleanupIntervalSeconds();

    int expiredPinCleanupBatch();

    boolean orphanCleanup();

    int orphanCleanupIntervalMinutes();

    boolean operatorBypassLimits();

    int operatorPermissionLevel();

    /** Maximum Opus frames a single recording may hold, derived from the duration cap. */
    default int maxRecordingFrames() {
        return dev.echopins.domain.audio.AudioConstants.framesForMillis(maxRecordingSeconds() * 1000);
    }
}
