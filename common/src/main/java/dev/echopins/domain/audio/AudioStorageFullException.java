package dev.echopins.domain.audio;

import java.io.IOException;

/** Raised when storing a recording would exceed the configured global audio limit. */
public final class AudioStorageFullException extends IOException {

    private static final long serialVersionUID = 1L;

    public AudioStorageFullException(long currentBytes, long incomingBytes, long limitBytes) {
        super("Audio storage limit exceeded: " + currentBytes + " + " + incomingBytes
                + " > " + limitBytes);
    }
}
