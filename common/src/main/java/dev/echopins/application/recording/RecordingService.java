package dev.echopins.application.recording;

import dev.echopins.application.ServerLimits;
import dev.echopins.application.pin.AnchorResolver;
import dev.echopins.application.voice.VoiceBackend;
import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.audio.AudioRef;
import dev.echopins.domain.audio.AudioStore;
import dev.echopins.domain.audio.VoiceRecording;
import dev.echopins.domain.error.EchoPinError;
import dev.echopins.domain.error.EchoPinException;
import dev.echopins.infrastructure.concurrent.EchoPinsExecutors;
import dev.echopins.infrastructure.network.EchoPinsNetwork;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads;
import dev.echopins.infrastructure.network.payload.ClientboundPayloads.RecordingPhase;
import dev.echopins.infrastructure.network.payload.ServerboundPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the recording lifecycle.
 *
 * <p>The privacy rules are enforced here and are deliberately blunt:
 *
 * <ul>
 *   <li>A frame is stored only if the speaker has an open session of their own.</li>
 *   <li>A session opens only from an explicit {@code BeginRecording} request.</li>
 *   <li>Disconnecting, dying, changing dimension or going quiet for too long all end the
 *       session and throw away the audio.</li>
 * </ul>
 *
 * <p>After capture stops, audio is written to disk <em>before</em> the player is asked to
 * confirm. That ordering is what lets pin metadata be created only for audio that already exists;
 * if the player never confirms, the pending audio is deleted, and if the server dies in between,
 * the orphan sweep collects it.
 */
public final class RecordingService implements VoiceBackend.MicrophoneCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Recording");

    /** How long an unconfirmed recording is held before its audio is deleted. */
    private static final long PENDING_TIMEOUT_MILLIS = 120_000L;

    /** How often recording progress is pushed to the client. */
    private static final long STATE_PUSH_INTERVAL_MILLIS = 250L;

    /** A recording that has been captured and stored but not yet confirmed. */
    public record PendingRecording(WorldAnchor anchor, AudioRef audio, long storedAtMillis) {
    }

    private final ServerLimits limits;
    private final AudioStore audioStore;
    private final AnchorResolver anchorResolver;
    private final java.util.function.LongSupplier clock;

    private final Map<UUID, RecordingSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRecording> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStatePushMillis = new ConcurrentHashMap<>();

    public RecordingService(ServerLimits limits, AudioStore audioStore,
                            AnchorResolver anchorResolver, java.util.function.LongSupplier clock) {
        this.limits = limits;
        this.audioStore = audioStore;
        this.anchorResolver = anchorResolver;
        this.clock = clock;
    }

    public boolean isRecording(UUID player) {
        return sessions.containsKey(player);
    }

    public Optional<PendingRecording> pendingFor(UUID player) {
        return Optional.ofNullable(pending.get(player));
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    public int pendingCount() {
        return pending.size();
    }

    /**
     * Starts capture for a player.
     *
     * @throws EchoPinException if the anchor is invalid or a session already exists
     */
    public void begin(ServerPlayer player, Optional<ServerboundPayloads.BlockTarget> blockTarget,
                      VoiceBackend voice) {
        UUID uuid = player.getUUID();
        if (sessions.containsKey(uuid)) {
            throw new EchoPinException(EchoPinError.ALREADY_RECORDING,
                    "Player " + uuid + " already has an open recording session");
        }
        if (!voice.isAvailable() || !voice.isPlayerConnected(uuid)) {
            throw new EchoPinException(EchoPinError.VOICE_CHAT_NOT_CONNECTED,
                    "Voice chat is not available for " + uuid);
        }

        // Discard any earlier unconfirmed recording; a player starting a new one has clearly
        // abandoned the previous.
        discardPending(uuid);

        WorldAnchor anchor = anchorResolver.resolve(player, blockTarget);
        long now = clock.getAsLong();
        RecordingSession session = new RecordingSession(uuid, anchor, now,
                limits.maxRecordingFrames(),
                limits.maxAudioBytesPerPin(),
                limits.maxRecordingSeconds() * 1000);
        sessions.put(uuid, session);
        LOGGER.debug("Opened recording session for {}", uuid);
        pushState(player, session);
    }

    /**
     * Stops capture and stores the audio.
     *
     * <p>Validation happens before anything is written, so a too-short recording never reaches
     * the disk.
     */
    public void finish(ServerPlayer player) {
        UUID uuid = player.getUUID();
        RecordingSession session = sessions.remove(uuid);
        lastStatePushMillis.remove(uuid);
        if (session == null) {
            throw new EchoPinException(EchoPinError.NOT_RECORDING,
                    "No recording session for " + uuid);
        }

        VoiceRecording recording = session.finish();
        if (recording.isEmpty()) {
            sendIdle(player);
            throw new EchoPinException(EchoPinError.NOTHING_RECORDED,
                    "Session for " + uuid + " captured no audio");
        }
        if (recording.durationMillis() < limits.minRecordingMillis()) {
            sendIdle(player);
            throw new EchoPinException(EchoPinError.RECORDING_TOO_SHORT,
                    "Recording was " + recording.durationMillis() + "ms, minimum is "
                            + limits.minRecordingMillis() + "ms");
        }
        if (audioStore.totalBytes() >= limits.maxTotalAudioStorageBytes()) {
            sendIdle(player);
            throw new EchoPinException(EchoPinError.STORAGE_FULL,
                    "Audio storage is at capacity (" + audioStore.totalBytes() + " bytes)");
        }

        WorldAnchor anchor = session.anchor();
        EchoPinsExecutors executors = EchoPinsExecutors.current();
        MinecraftServer server = player.getServer();
        if (executors == null || server == null) {
            sendIdle(player);
            throw new EchoPinException(EchoPinError.INTERNAL_ERROR, "Server is shutting down");
        }

        boolean accepted = executors.submitIo(() -> {
            AudioRef ref;
            try {
                ref = audioStore.store(recording);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not store recording for {}", uuid, e);
                server.execute(() -> {
                    sendIdle(player);
                    sendError(player, EchoPinError.INTERNAL_ERROR);
                });
                return;
            }
            // Back to the server thread before touching any shared state or sending packets.
            server.execute(() -> {
                // The player may have left while the audio was being written. Storing a pending
                // recording for an absent player would leak the file until the timeout swept it,
                // and the packet below would go to a dead connection.
                if (server.getPlayerList().getPlayer(uuid) == null) {
                    LOGGER.debug("{} left before confirming; discarding the stored recording", uuid);
                    audioStore.delete(ref.audioId());
                    return;
                }
                pending.put(uuid, new PendingRecording(anchor, ref, clock.getAsLong()));
                EchoPinsNetwork.sendTo(player, new ClientboundPayloads.RecordingState(
                        RecordingPhase.AWAITING_CONFIRMATION,
                        recording.durationMillis(),
                        limits.maxRecordingSeconds() * 1000,
                        true));
            });
        });

        if (!accepted) {
            sendIdle(player);
            throw new EchoPinException(EchoPinError.INTERNAL_ERROR, "IO queue is saturated");
        }
    }

    /** Abandons an open session and any stored-but-unconfirmed audio. */
    public void cancel(ServerPlayer player) {
        UUID uuid = player.getUUID();
        RecordingSession session = sessions.remove(uuid);
        lastStatePushMillis.remove(uuid);
        if (session != null) {
            session.discard();
            LOGGER.debug("Cancelled recording session for {}", uuid);
        }
        discardPending(uuid);
        sendIdle(player);
    }

    /** Consumes a confirmed pending recording, handing ownership to the caller. */
    public Optional<PendingRecording> takePending(UUID player) {
        return Optional.ofNullable(pending.remove(player));
    }

    /**
     * Puts a taken recording back so a transient rejection does not destroy it.
     *
     * <p>{@link #takePending} hands ownership to the caller. If the caller then fails for a reason
     * the player can simply wait out - a cooldown - throwing the audio away would mean they lose
     * a message they already recorded.
     */
    public void restorePending(UUID player, PendingRecording taken) {
        pending.put(player, taken);
    }

    /**
     * Deletes a recording the caller already took ownership of.
     *
     * <p>Necessary because {@link #discardPending} works off the map, and after
     * {@link #takePending} there is nothing left in the map to find - calling it would silently do
     * nothing and leak the file until the orphan sweep noticed.
     */
    public void discardTaken(PendingRecording taken) {
        UUID audioId = taken.audio().audioId();
        EchoPinsExecutors executors = EchoPinsExecutors.current();
        if (executors == null || !executors.submitIo(() -> audioStore.delete(audioId))) {
            audioStore.delete(audioId);
        }
    }

    /** Deletes a pending recording's audio. Safe if there is none. */
    public void discardPending(UUID player) {
        PendingRecording abandoned = pending.remove(player);
        if (abandoned == null) {
            return;
        }
        EchoPinsExecutors executors = EchoPinsExecutors.current();
        UUID audioId = abandoned.audio().audioId();
        if (executors == null || !executors.submitIo(() -> audioStore.delete(audioId))) {
            // Falling back to a synchronous delete is acceptable here: it happens on cancel or
            // shutdown, and the orphan sweep would catch the file anyway.
            audioStore.delete(audioId);
        }
    }

    /** Ends everything for a player who has left, died, or changed dimension. */
    public void endFor(UUID player) {
        RecordingSession session = sessions.remove(player);
        lastStatePushMillis.remove(player);
        if (session != null) {
            session.discard();
            LOGGER.debug("Dropped recording session for {}", player);
        }
        discardPending(player);
    }

    @Override
    public boolean onMicrophoneFrame(UUID speaker, byte[] opusFrame) {
        RecordingSession session = sessions.get(speaker);
        if (session == null) {
            // Not recording: this is ordinary proximity chat and must pass through untouched.
            return false;
        }
        long now = clock.getAsLong();
        if (!session.addFrame(opusFrame, now)) {
            // Full, or already closed. The tick loop will finalise it; suppressing the broadcast
            // here keeps the tail of a maxed-out recording from leaking to bystanders.
            return limits.suppressProximityBroadcastWhileRecording();
        }
        return limits.suppressProximityBroadcastWhileRecording();
    }

    /**
     * Per-tick maintenance: pushes progress, auto-stops full recordings, and times out sessions
     * that have gone quiet or been abandoned.
     */
    public void tick(MinecraftServer server) {
        if (sessions.isEmpty() && pending.isEmpty()) {
            return;
        }
        long now = clock.getAsLong();
        long timeoutMillis = limits.recordingSessionTimeoutSeconds() * 1000L;

        for (Map.Entry<UUID, RecordingSession> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            RecordingSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                endFor(uuid);
                continue;
            }

            if (session.isFull()) {
                LOGGER.debug("Recording for {} reached the maximum length; stopping", uuid);
                try {
                    finish(player);
                } catch (EchoPinException e) {
                    sendError(player, e.error());
                }
                continue;
            }

            // Only time out on silence: a player holding push-to-talk and thinking still sends
            // frames, whereas a client that has stopped responding sends nothing at all.
            if (session.millisSinceLastFrame(now) > timeoutMillis) {
                LOGGER.debug("Recording session for {} timed out after {}ms of silence",
                        uuid, timeoutMillis);
                cancel(player);
                sendError(player, session.hasAudio()
                        ? EchoPinError.RECORDING_EXPIRED
                        : EchoPinError.VOICE_CHAT_NOT_CONNECTED);
                continue;
            }

            Long lastPush = lastStatePushMillis.get(uuid);
            if (lastPush == null || now - lastPush >= STATE_PUSH_INTERVAL_MILLIS) {
                pushState(player, session);
            }
        }

        pending.entrySet().removeIf(entry -> {
            if (now - entry.getValue().storedAtMillis() < PENDING_TIMEOUT_MILLIS) {
                return false;
            }
            LOGGER.debug("Discarding an unconfirmed recording from {}", entry.getKey());
            UUID audioId = entry.getValue().audio().audioId();
            EchoPinsExecutors executors = EchoPinsExecutors.current();
            if (executors == null || !executors.submitIo(() -> audioStore.delete(audioId))) {
                audioStore.delete(audioId);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendIdle(player);
                sendError(player, EchoPinError.RECORDING_EXPIRED);
            }
            return true;
        });
    }

    /** Drops all state, deleting unconfirmed audio. Called on server stop. */
    public void shutdown() {
        for (RecordingSession session : sessions.values()) {
            session.discard();
        }
        sessions.clear();
        lastStatePushMillis.clear();
        for (PendingRecording abandoned : pending.values()) {
            audioStore.delete(abandoned.audio().audioId());
        }
        pending.clear();
    }

    private void pushState(ServerPlayer player, RecordingSession session) {
        lastStatePushMillis.put(player.getUUID(), clock.getAsLong());
        EchoPinsNetwork.sendTo(player, new ClientboundPayloads.RecordingState(
                RecordingPhase.RECORDING,
                session.recordedMillis(),
                limits.maxRecordingSeconds() * 1000,
                session.hasAudio()));
    }

    private void sendIdle(ServerPlayer player) {
        EchoPinsNetwork.sendTo(player, new ClientboundPayloads.RecordingState(
                RecordingPhase.IDLE, 0, limits.maxRecordingSeconds() * 1000, false));
    }

    private void sendError(ServerPlayer player, EchoPinError error) {
        EchoPinsNetwork.sendTo(player, ClientboundPayloads.ErrorMessage.of(error));
    }
}
