package dev.echopins.domain.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A finished recording: an ordered list of Opus frames exactly as Simple Voice Chat produced
 * them.
 *
 * <p>EchoPins never decodes to PCM in order to store audio. Simple Voice Chat hands over Opus
 * frames, the container writes those bytes verbatim, and playback hands them straight back. That
 * avoids a decode/re-encode round trip and the quality loss and CPU cost that come with it.
 *
 * <p>Immutable from the outside. Frame arrays are not copied on read for performance, so
 * consumers must treat them as read-only; {@link #frameCopy(int)} exists for the rare caller
 * that needs ownership.
 */
public final class VoiceRecording {

    private final List<byte[]> frames;
    private final int totalPayloadBytes;

    private VoiceRecording(List<byte[]> frames, int totalPayloadBytes) {
        this.frames = frames;
        this.totalPayloadBytes = totalPayloadBytes;
    }

    public static Builder builder(int maxFrames, int maxTotalPayloadBytes) {
        return new Builder(maxFrames, maxTotalPayloadBytes);
    }

    /** Builds from already-validated frames, used by the container reader. */
    public static VoiceRecording ofValidatedFrames(List<byte[]> frames) {
        Objects.requireNonNull(frames, "frames");
        int total = 0;
        for (byte[] frame : frames) {
            total += frame.length;
        }
        return new VoiceRecording(List.copyOf(frames), total);
    }

    public int frameCount() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public int totalPayloadBytes() {
        return totalPayloadBytes;
    }

    public int durationMillis() {
        return AudioConstants.millisForFrames(frames.size());
    }

    /** Read-only access; do not mutate the returned array. */
    public byte[] frame(int index) {
        return frames.get(index);
    }

    public byte[] frameCopy(int index) {
        return frames.get(index).clone();
    }

    public void forEachFrame(Consumer<byte[]> consumer) {
        for (byte[] frame : frames) {
            consumer.accept(frame);
        }
    }

    /**
     * Accumulates frames under hard limits.
     *
     * <p>{@link #addFrame} returning {@code false} is the signal that the recording has hit the
     * configured ceiling, which the recording session turns into a clean automatic stop rather
     * than an error - the player keeps what they already said.
     */
    public static final class Builder {

        private final List<byte[]> frames = new ArrayList<>();
        private final int maxFrames;
        private final int maxTotalPayloadBytes;
        private int totalPayloadBytes;

        private Builder(int maxFrames, int maxTotalPayloadBytes) {
            this.maxFrames = Math.max(0, maxFrames);
            this.maxTotalPayloadBytes = Math.max(0, maxTotalPayloadBytes);
        }

        /**
         * @return {@code false} if the frame was rejected because a limit was reached or the
         *         frame itself is malformed
         */
        public boolean addFrame(byte[] opusFrame) {
            if (opusFrame == null || opusFrame.length == 0
                    || opusFrame.length > AudioConstants.MAX_FRAME_BYTES) {
                return false;
            }
            if (frames.size() >= maxFrames) {
                return false;
            }
            if (totalPayloadBytes + opusFrame.length > maxTotalPayloadBytes) {
                return false;
            }
            // Copy: the caller's array comes from a network buffer that is reused.
            frames.add(opusFrame.clone());
            totalPayloadBytes += opusFrame.length;
            return true;
        }

        public boolean isFull() {
            return frames.size() >= maxFrames || totalPayloadBytes >= maxTotalPayloadBytes;
        }

        public int frameCount() {
            return frames.size();
        }

        public int durationMillis() {
            return AudioConstants.millisForFrames(frames.size());
        }

        public VoiceRecording build() {
            return new VoiceRecording(List.copyOf(frames), totalPayloadBytes);
        }
    }
}
