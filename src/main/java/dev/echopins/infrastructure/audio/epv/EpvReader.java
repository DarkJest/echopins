package dev.echopins.infrastructure.audio.epv;

import dev.echopins.domain.audio.AudioConstants;
import dev.echopins.domain.audio.VoiceRecording;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Parses an EPV container.
 *
 * <p>Every read is bounds-checked against the actual buffer length before it happens, and no
 * declared length is used to allocate until it has been validated against both the format
 * ceilings and the remaining bytes. A hostile or corrupted file can therefore cause a rejected
 * parse, but not an oversized allocation, an infinite loop, or an out-of-bounds read.
 */
public final class EpvReader {

    private EpvReader() {
    }

    /** Header fields, exposed so callers can report details without decoding the whole body. */
    public record Header(int formatVersion, int codec, int sampleRate, int channels,
                         int frameDurationMillis, int frameCount) {
    }

    /**
     * Validates and decodes a container.
     *
     * @param data the complete file contents
     * @throws EpvFormatException if anything about the container is wrong
     */
    public static VoiceRecording read(byte[] data) throws EpvFormatException {
        Header header = readHeader(data);

        Cursor cursor = new Cursor(data, EpvFormat.HEADER_BYTES);
        List<byte[]> frames = new ArrayList<>(Math.min(header.frameCount(), 1024));
        long payloadBytes = 0;

        for (int i = 0; i < header.frameCount(); i++) {
            int length = cursor.readUnsignedShort("frame " + i + " length");
            if (length == 0 || length > AudioConstants.MAX_FRAME_BYTES) {
                throw new EpvFormatException("Frame " + i + " declares invalid length " + length);
            }
            byte[] frame = cursor.readBytes(length, "frame " + i);
            payloadBytes += length;
            if (payloadBytes > EpvFormat.MAX_CONTAINER_BYTES) {
                throw new EpvFormatException("Frame payload exceeds the maximum container size");
            }
            frames.add(frame);
        }

        int storedCrc = cursor.readInt("crc32");
        if (cursor.remaining() != 0) {
            throw new EpvFormatException("Trailing garbage after checksum: " + cursor.remaining() + " bytes");
        }

        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length - EpvFormat.CRC_BYTES);
        if ((int) crc.getValue() != storedCrc) {
            throw new EpvFormatException("Checksum mismatch - file is damaged");
        }

        return VoiceRecording.ofValidatedFrames(frames);
    }

    /**
     * Reads and validates only the fixed header. Cheap enough to run over many files during an
     * integrity sweep without decoding every frame.
     */
    public static Header readHeader(byte[] data) throws EpvFormatException {
        if (data == null) {
            throw new EpvFormatException("No data");
        }
        if (data.length > EpvFormat.MAX_CONTAINER_BYTES) {
            throw new EpvFormatException("Container is larger than the maximum: " + data.length);
        }
        if (data.length < EpvFormat.HEADER_BYTES + EpvFormat.CRC_BYTES) {
            throw new EpvFormatException("Container is too short: " + data.length + " bytes");
        }
        if (!Arrays.equals(data, 0, EpvFormat.MAGIC.length, EpvFormat.MAGIC, 0, EpvFormat.MAGIC.length)) {
            throw new EpvFormatException("Bad magic - not an EPV file");
        }

        Cursor cursor = new Cursor(data, EpvFormat.MAGIC.length);
        int formatVersion = cursor.readUnsignedByte("formatVersion");
        if (formatVersion != EpvFormat.FORMAT_VERSION_1) {
            throw new EpvFormatException("Unsupported EPV format version: " + formatVersion);
        }
        int codec = cursor.readUnsignedByte("codec");
        if (codec != EpvFormat.CODEC_OPUS) {
            throw new EpvFormatException("Unsupported codec id: " + codec);
        }
        int sampleRate = cursor.readInt("sampleRate");
        int channels = cursor.readUnsignedByte("channels");
        int frameDuration = cursor.readUnsignedByte("frameDurationMillis");
        if (!EpvFormat.isSupportedAudioShape(sampleRate, channels, frameDuration)) {
            throw new EpvFormatException("Unsupported audio shape: " + sampleRate + " Hz, "
                    + channels + " channel(s), " + frameDuration + " ms frames");
        }
        int frameCount = cursor.readInt("frameCount");
        if (frameCount < 0 || frameCount > EpvFormat.MAX_FRAME_COUNT) {
            throw new EpvFormatException("Frame count out of range: " + frameCount);
        }

        // Reject before allocating anything: the smallest possible body for this frame count
        // must still fit in what we actually have.
        long minimumSize = EpvFormat.containerSize(frameCount, frameCount);
        if (minimumSize > data.length) {
            throw new EpvFormatException("Container declares " + frameCount
                    + " frames but is only " + data.length + " bytes");
        }

        return new Header(formatVersion, codec, sampleRate, channels, frameDuration, frameCount);
    }

    /** A bounds-checked cursor over the buffer. */
    private static final class Cursor {
        private final byte[] data;
        private int offset;

        Cursor(byte[] data, int offset) {
            this.data = data;
            this.offset = offset;
        }

        int remaining() {
            return data.length - offset;
        }

        private void require(int count, String what) throws EpvFormatException {
            if (count < 0 || remaining() < count) {
                throw new EpvFormatException("Truncated while reading " + what
                        + ": needed " + count + ", have " + remaining());
            }
        }

        int readUnsignedByte(String what) throws EpvFormatException {
            require(1, what);
            return data[offset++] & 0xFF;
        }

        int readUnsignedShort(String what) throws EpvFormatException {
            require(2, what);
            int value = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            offset += 2;
            return value;
        }

        int readInt(String what) throws EpvFormatException {
            require(4, what);
            int value = ((data[offset] & 0xFF) << 24)
                    | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8)
                    | (data[offset + 3] & 0xFF);
            offset += 4;
            return value;
        }

        byte[] readBytes(int count, String what) throws EpvFormatException {
            // The CRC lives at the end, so the body must never consume it.
            if (count < 0 || remaining() - EpvFormat.CRC_BYTES < count) {
                throw new EpvFormatException("Truncated while reading " + what
                        + ": needed " + count + ", have " + Math.max(0, remaining() - EpvFormat.CRC_BYTES));
            }
            byte[] out = Arrays.copyOfRange(data, offset, offset + count);
            offset += count;
            return out;
        }
    }
}
