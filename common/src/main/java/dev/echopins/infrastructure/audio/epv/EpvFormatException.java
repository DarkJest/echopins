package dev.echopins.infrastructure.audio.epv;

import java.io.IOException;

/**
 * A container that is present but not usable: wrong magic, unknown version, out-of-range field,
 * truncated body, or failed checksum.
 *
 * <p>Distinct from a plain {@link IOException} so callers can tell "the disk is broken" apart
 * from "this file is damaged", and report the latter to the player as a damaged recording while
 * logging the technical detail.
 */
public class EpvFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    public EpvFormatException(String message) {
        super(message);
    }
}
