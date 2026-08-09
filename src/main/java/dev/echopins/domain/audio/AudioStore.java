package dev.echopins.domain.audio;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores and retrieves voice recordings.
 *
 * <p>Addressing is by opaque {@link UUID} only. The store is the sole component that knows where
 * bytes live, so no id arriving from the network can ever name a file - there is no API here
 * that accepts a path.
 */
public interface AudioStore {

    /**
     * Writes a recording durably and returns a handle to it.
     *
     * <p>Must be atomic from a reader's point of view: either the id resolves to a complete,
     * checksum-valid container or it does not resolve at all. Callers rely on this to guarantee
     * that pin metadata is only ever created for audio that is already safely on disk.
     *
     * @throws IOException if the recording could not be written
     */
    AudioRef store(VoiceRecording recording) throws IOException;

    /**
     * Loads a recording.
     *
     * @return empty if no audio with this id exists
     * @throws IOException if the file exists but is unreadable or damaged
     */
    Optional<VoiceRecording> load(UUID audioId) throws IOException;

    /** Whether audio with this id is present. */
    boolean exists(UUID audioId);

    /**
     * Deletes audio. Idempotent.
     *
     * @return {@code true} if a file was actually removed
     */
    boolean delete(UUID audioId);

    /** Total bytes currently occupied by stored audio. */
    long totalBytes();

    /** Every stored audio id. Used by the orphan collector. */
    Set<UUID> listAudioIds() throws IOException;
}
