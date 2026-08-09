package dev.echopins.application.voice;

import dev.echopins.domain.anchor.WorldPos;
import dev.echopins.domain.audio.VoiceRecording;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The voice system EchoPins talks to, expressed without a single Simple Voice Chat type.
 *
 * <p>This is the seam that keeps the rest of the mod portable. Recording, playback and the pin
 * service depend only on this interface, so adding a second backend later - Plasmo Voice, say -
 * means writing one more adapter rather than editing the services.
 */
public interface VoiceBackend {

    /** Whether a voice chat server is running and the API is usable right now. */
    boolean isAvailable();

    /** Whether this player's voice chat client is connected and not disabled. */
    boolean isPlayerConnected(UUID playerUuid);

    /**
     * Installs the sink for captured microphone audio.
     *
     * <p>Called once at startup. The capture callback runs on the voice system's own thread.
     */
    void setMicrophoneCapture(MicrophoneCapture capture);

    /**
     * Installs a listener for a player's voice connection dropping.
     *
     * <p>Distinct from leaving the server: a player can lose voice chat while still playing. Any
     * recording they had open can no longer receive audio, so it should end at once rather than
     * sit until the silence timeout with the recording indicator still on screen.
     *
     * <p>Invoked on the voice system's thread.
     */
    void setVoiceDisconnectListener(java.util.function.Consumer<UUID> listener);

    /**
     * Starts locational playback of a stored recording.
     *
     * @param level      the level to play in
     * @param position   where the audio should appear to come from
     * @param distance   how far the audio carries, in blocks
     * @param channelId  unique id for this playback
     * @param audience   which players may hear it; re-evaluated by the voice system per listener
     * @param recording  the frames to play
     * @param onFinished run when playback ends, for any reason
     * @return a handle, or empty if playback could not be started
     */
    Optional<VoicePlayback> startLocationalPlayback(ServerLevel level,
                                                    WorldPos position,
                                                    float distance,
                                                    UUID channelId,
                                                    Predicate<UUID> audience,
                                                    VoiceRecording recording,
                                                    Runnable onFinished);

    /** Stops everything and releases voice-system resources. */
    void shutdown();

    /** A running playback. */
    interface VoicePlayback {
        UUID channelId();

        boolean isFinished();

        /** Stops early. Idempotent. */
        void stop();
    }

    /** Receives microphone frames from the voice system. */
    @FunctionalInterface
    interface MicrophoneCapture {
        /**
         * @param speaker    the player who spoke
         * @param opusFrame  one Opus frame; the array belongs to the caller and must be copied to
         *                   be retained
         * @return {@code true} to suppress the normal proximity broadcast of this frame, which is
         *         how a message being recorded is kept from also being heard by bystanders
         */
        boolean onMicrophoneFrame(UUID speaker, byte[] opusFrame);
    }
}
