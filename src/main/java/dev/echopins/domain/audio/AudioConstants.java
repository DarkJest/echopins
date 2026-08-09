package dev.echopins.domain.audio;

/**
 * Audio constants that follow from Simple Voice Chat's wire format.
 *
 * <p>Simple Voice Chat encodes 48 kHz mono audio in 20 ms Opus frames (960 samples per frame).
 * EchoPins stores those frames verbatim, so these values define both the on-disk container and
 * the pacing used during playback.
 */
public final class AudioConstants {

    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_DURATION_MILLIS = 20;
    public static final int SAMPLES_PER_FRAME = SAMPLE_RATE / 1000 * FRAME_DURATION_MILLIS;
    public static final int CHANNELS = 1;

    /**
     * Upper bound on a single Opus frame. Opus itself caps a packet at 1275 bytes for a single
     * frame; the extra headroom tolerates future multi-frame packets without allowing a
     * corrupted length field to trigger a huge allocation.
     */
    public static final int MAX_FRAME_BYTES = 4_096;

    private AudioConstants() {
    }

    public static int framesForMillis(int millis) {
        return millis / FRAME_DURATION_MILLIS;
    }

    public static int millisForFrames(int frames) {
        return frames * FRAME_DURATION_MILLIS;
    }
}
