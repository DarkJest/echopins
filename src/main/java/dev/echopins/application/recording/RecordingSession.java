package dev.echopins.application.recording;

import dev.echopins.domain.anchor.WorldAnchor;
import dev.echopins.domain.audio.VoiceRecording;

import java.util.UUID;

/**
 * One player's in-progress recording.
 *
 * <p>A session exists only between an explicit start and an explicit stop. Audio is accepted
 * <em>only</em> while a session is open and <em>only</em> from the player the session belongs to;
 * there is no code path that stores a frame from anyone else, and none that keeps a session alive
 * after the player disconnects.
 *
 * <p>Mutated from the voice system's thread (frames arriving) and from the server thread
 * (start/stop), so the frame buffer is guarded.
 */
public final class RecordingSession {

    private final UUID playerUuid;
    private final WorldAnchor anchor;
    private final long startedAtMillis;
    private final VoiceRecording.Builder frames;
    private final int maxDurationMillis;

    private final Object lock = new Object();
    private long lastFrameAtMillis;
    private boolean closed;
    private boolean receivedAnyAudio;

    public RecordingSession(UUID playerUuid, WorldAnchor anchor, long startedAtMillis,
                            int maxFrames, int maxTotalBytes, int maxDurationMillis) {
        this.playerUuid = playerUuid;
        this.anchor = anchor;
        this.startedAtMillis = startedAtMillis;
        this.lastFrameAtMillis = startedAtMillis;
        this.frames = VoiceRecording.builder(maxFrames, maxTotalBytes);
        this.maxDurationMillis = maxDurationMillis;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public WorldAnchor anchor() {
        return anchor;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    /**
     * Adds a frame.
     *
     * @return {@code false} if the session is closed or full, in which case the caller should
     *         stop the recording
     */
    public boolean addFrame(byte[] opusFrame, long nowMillis) {
        synchronized (lock) {
            if (closed) {
                return false;
            }
            if (!frames.addFrame(opusFrame)) {
                return false;
            }
            receivedAnyAudio = true;
            lastFrameAtMillis = nowMillis;
            return true;
        }
    }

    /**
     * Recorded length so far.
     *
     * <p>Derived from the frame count, not from wall-clock time. In push-to-talk a player may
     * hold the session open while saying nothing, and the honest length of the message is the
     * audio actually captured.
     */
    public int recordedMillis() {
        synchronized (lock) {
            return frames.durationMillis();
        }
    }

    public boolean hasAudio() {
        synchronized (lock) {
            return receivedAnyAudio;
        }
    }

    public boolean isFull() {
        synchronized (lock) {
            return frames.isFull() || frames.durationMillis() >= maxDurationMillis;
        }
    }

    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    /** Milliseconds since the last frame arrived, used to time out a stalled session. */
    public long millisSinceLastFrame(long nowMillis) {
        synchronized (lock) {
            return Math.max(0L, nowMillis - lastFrameAtMillis);
        }
    }

    /** Closes the session and returns what was captured. Safe to call more than once. */
    public VoiceRecording finish() {
        synchronized (lock) {
            closed = true;
            return frames.build();
        }
    }

    /** Closes the session and discards everything captured. */
    public void discard() {
        synchronized (lock) {
            closed = true;
        }
    }
}
