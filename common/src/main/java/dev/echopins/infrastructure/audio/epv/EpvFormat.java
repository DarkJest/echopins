package dev.echopins.infrastructure.audio.epv;

import dev.echopins.domain.audio.AudioConstants;

/**
 * Constants describing the EchoPin Voice (EPV) container.
 *
 * <p>Layout, all integers big-endian:
 *
 * <pre>
 * offset size field
 * 0      4    magic          'E' 'P' 'V' 0x1A
 * 4      1    formatVersion  currently 1
 * 5      1    codec          1 = Opus
 * 6      4    sampleRate     48000
 * 10     1    channels       1
 * 11     1    frameDuration  milliseconds per frame, 20
 * 12     4    frameCount     number of frames that follow
 * 16     -    frames         frameCount repetitions of: uint16 length, then `length` bytes
 * -      4    crc32          CRC-32 of every preceding byte in the file
 * </pre>
 *
 * <p>The trailing CRC is what makes a truncated or partially written file detectable rather than
 * silently playable as noise. The 0x1A byte in the magic is the classic "stop displaying this
 * file" marker, which keeps a stray {@code type}/{@code cat} of the file from spewing terminal
 * control bytes.
 *
 * <p>No field is ever used to size an allocation before it has been range-checked; see
 * {@link EpvReader}.
 */
public final class EpvFormat {

    public static final byte[] MAGIC = {'E', 'P', 'V', 0x1A};

    public static final int FORMAT_VERSION_1 = 1;
    public static final int CURRENT_FORMAT_VERSION = FORMAT_VERSION_1;

    public static final int CODEC_OPUS = 1;

    public static final int HEADER_BYTES = 16;
    public static final int FRAME_LENGTH_PREFIX_BYTES = 2;
    public static final int CRC_BYTES = 4;

    /** File extension. Lowercase, fixed; never derived from user input. */
    public static final String FILE_EXTENSION = ".epv";

    /**
     * Absolute ceiling on frame count, independent of config. 30,000 frames is ten minutes of
     * audio - far above any sane {@code maxRecordingSeconds} - and bounds the frame list
     * allocation a corrupted header could request.
     */
    public static final int MAX_FRAME_COUNT = 30_000;

    /**
     * Absolute ceiling on container size, independent of config. Ten minutes of Opus at a
     * generous bitrate stays well under this.
     */
    public static final int MAX_CONTAINER_BYTES = 8 * 1024 * 1024;

    private EpvFormat() {
    }

    /** Exact byte size a container with the given frames will occupy. */
    public static long containerSize(int frameCount, long totalPayloadBytes) {
        return (long) HEADER_BYTES
                + (long) frameCount * FRAME_LENGTH_PREFIX_BYTES
                + totalPayloadBytes
                + CRC_BYTES;
    }

    /** Whether a header's audio parameters are ones this build can play back. */
    public static boolean isSupportedAudioShape(int sampleRate, int channels, int frameDurationMillis) {
        return sampleRate == AudioConstants.SAMPLE_RATE
                && channels == AudioConstants.CHANNELS
                && frameDurationMillis == AudioConstants.FRAME_DURATION_MILLIS;
    }
}
