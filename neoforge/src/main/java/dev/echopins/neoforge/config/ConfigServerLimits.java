package dev.echopins.neoforge.config;

import dev.echopins.application.ServerLimits;

/**
 * Reads {@link ServerLimits} straight from the NeoForge config spec.
 *
 * <p>Values are read on every call rather than cached, so {@code /echopins admin reload} and any
 * external config edit take effect immediately without invalidating anything.
 */
public final class ConfigServerLimits implements ServerLimits {

    public static final ConfigServerLimits INSTANCE = new ConfigServerLimits();

    private ConfigServerLimits() {
    }

    @Override
    public boolean enabled() {
        return EchoPinsServerConfig.ENABLED.get();
    }

    @Override
    public int maxRecordingSeconds() {
        return EchoPinsServerConfig.MAX_RECORDING_SECONDS.get();
    }

    @Override
    public int minRecordingMillis() {
        return EchoPinsServerConfig.MIN_RECORDING_MILLIS.get();
    }

    @Override
    public int recordingSessionTimeoutSeconds() {
        return EchoPinsServerConfig.RECORDING_SESSION_TIMEOUT_SECONDS.get();
    }

    @Override
    public boolean suppressProximityBroadcastWhileRecording() {
        return EchoPinsServerConfig.SUPPRESS_PROXIMITY_WHILE_RECORDING.get();
    }

    @Override
    public int maxPinsPerPlayer() {
        return EchoPinsServerConfig.MAX_PINS_PER_PLAYER.get();
    }

    @Override
    public int maxTotalPins() {
        return EchoPinsServerConfig.MAX_TOTAL_PINS.get();
    }

    @Override
    public int maxPinsNearby() {
        return EchoPinsServerConfig.MAX_PINS_NEARBY.get();
    }

    @Override
    public int maxCaptionLength() {
        return EchoPinsServerConfig.MAX_CAPTION_LENGTH.get();
    }

    @Override
    public int maxPrivateRecipients() {
        return EchoPinsServerConfig.MAX_PRIVATE_RECIPIENTS.get();
    }

    @Override
    public double discoveryRadius() {
        return EchoPinsServerConfig.DISCOVERY_RADIUS.get();
    }

    @Override
    public double interactionRadius() {
        return EchoPinsServerConfig.INTERACTION_RADIUS.get();
    }

    @Override
    public double maxCreationDistance() {
        return EchoPinsServerConfig.MAX_CREATION_DISTANCE.get();
    }

    @Override
    public double playbackAudioDistance() {
        return EchoPinsServerConfig.PLAYBACK_AUDIO_DISTANCE.get();
    }

    @Override
    public int maxSyncedPinsPerPlayer() {
        return EchoPinsServerConfig.MAX_SYNCED_PINS_PER_PLAYER.get();
    }

    @Override
    public int syncIntervalTicks() {
        return EchoPinsServerConfig.SYNC_INTERVAL_TICKS.get();
    }

    @Override
    public int defaultExpiryHours() {
        return EchoPinsServerConfig.DEFAULT_EXPIRY_HOURS.get();
    }

    @Override
    public boolean allowPermanentPins() {
        return EchoPinsServerConfig.ALLOW_PERMANENT_PINS.get();
    }

    @Override
    public int createCooldownSeconds() {
        return EchoPinsServerConfig.CREATE_COOLDOWN_SECONDS.get();
    }

    @Override
    public int playbackCooldownMillis() {
        return EchoPinsServerConfig.PLAYBACK_COOLDOWN_MILLIS.get();
    }

    @Override
    public int maxConcurrentPlaybacksPerPlayer() {
        return EchoPinsServerConfig.MAX_CONCURRENT_PLAYBACKS_PER_PLAYER.get();
    }

    @Override
    public int requestBurstCapacity() {
        return EchoPinsServerConfig.REQUEST_BURST_CAPACITY.get();
    }

    @Override
    public double requestRefillPerSecond() {
        return EchoPinsServerConfig.REQUEST_REFILL_PER_SECOND.get();
    }

    @Override
    public int maxAudioBytesPerPin() {
        return EchoPinsServerConfig.MAX_AUDIO_BYTES_PER_PIN.get();
    }

    @Override
    public long maxTotalAudioStorageBytes() {
        return EchoPinsServerConfig.MAX_TOTAL_AUDIO_STORAGE_BYTES.get();
    }

    @Override
    public boolean expiredPinCleanup() {
        return EchoPinsServerConfig.EXPIRED_PIN_CLEANUP.get();
    }

    @Override
    public int expiredPinCleanupIntervalSeconds() {
        return EchoPinsServerConfig.EXPIRED_PIN_CLEANUP_INTERVAL_SECONDS.get();
    }

    @Override
    public int expiredPinCleanupBatch() {
        return EchoPinsServerConfig.EXPIRED_PIN_CLEANUP_BATCH.get();
    }

    @Override
    public boolean orphanCleanup() {
        return EchoPinsServerConfig.ORPHAN_CLEANUP.get();
    }

    @Override
    public int orphanCleanupIntervalMinutes() {
        return EchoPinsServerConfig.ORPHAN_CLEANUP_INTERVAL_MINUTES.get();
    }

    @Override
    public boolean operatorBypassLimits() {
        return EchoPinsServerConfig.OPERATOR_BYPASS_LIMITS.get();
    }

    @Override
    public int operatorPermissionLevel() {
        return EchoPinsServerConfig.OPERATOR_PERMISSION_LEVEL.get();
    }
}
