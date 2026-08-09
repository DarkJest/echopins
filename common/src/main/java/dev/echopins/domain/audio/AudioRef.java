package dev.echopins.domain.audio;

import java.util.Objects;
import java.util.UUID;

/**
 * A handle to a stored voice recording.
 *
 * <p>Deliberately holds no filesystem path. The audio store is the only component that turns an
 * {@code audioId} into a location on disk, so a value that arrives from the network can never
 * be used to address an arbitrary file.
 *
 * @param audioId    identifier of the stored blob
 * @param byteSize   size of the stored container in bytes, used for storage accounting
 * @param frameCount number of Opus frames, which determines playback duration
 */
public record AudioRef(UUID audioId, long byteSize, int frameCount) {

    public AudioRef {
        Objects.requireNonNull(audioId, "audioId");
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must not be negative");
        }
        if (frameCount < 0) {
            throw new IllegalArgumentException("frameCount must not be negative");
        }
    }

    /** Playback duration implied by the frame count. */
    public int durationMillis() {
        return frameCount * AudioConstants.FRAME_DURATION_MILLIS;
    }
}
