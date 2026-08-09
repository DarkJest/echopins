package dev.echopins.infrastructure.audio.epv;

import dev.echopins.domain.audio.AudioConstants;
import dev.echopins.domain.audio.VoiceRecording;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;

/**
 * Serialises a {@link VoiceRecording} into the EPV container.
 *
 * <p>Writes to a byte array first and returns it, rather than streaming straight to a file. The
 * caller can then write the finished array in one shot and only publish a file that is complete
 * by construction, which is what makes the "metadata only exists if audio exists" rule
 * enforceable.
 */
public final class EpvWriter {

    private EpvWriter() {
    }

    /**
     * @throws EpvFormatException if the recording exceeds the format's absolute limits
     */
    public static byte[] toBytes(VoiceRecording recording) throws IOException {
        int frameCount = recording.frameCount();
        if (frameCount > EpvFormat.MAX_FRAME_COUNT) {
            throw new EpvFormatException("Too many frames: " + frameCount);
        }
        long size = EpvFormat.containerSize(frameCount, recording.totalPayloadBytes());
        if (size > EpvFormat.MAX_CONTAINER_BYTES) {
            throw new EpvFormatException("Container would exceed the maximum size: " + size);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) size);
        CRC32 crc = new CRC32();

        writeHeader(out, crc, frameCount);

        for (int i = 0; i < frameCount; i++) {
            byte[] frame = recording.frame(i);
            if (frame.length == 0 || frame.length > AudioConstants.MAX_FRAME_BYTES) {
                throw new EpvFormatException("Frame " + i + " has invalid length " + frame.length);
            }
            writeUnsignedShort(out, crc, frame.length);
            out.write(frame);
            crc.update(frame, 0, frame.length);
        }

        writeInt(out, null, (int) crc.getValue());
        return out.toByteArray();
    }

    private static void writeHeader(OutputStream out, CRC32 crc, int frameCount) throws IOException {
        out.write(EpvFormat.MAGIC);
        crc.update(EpvFormat.MAGIC, 0, EpvFormat.MAGIC.length);
        writeByte(out, crc, EpvFormat.CURRENT_FORMAT_VERSION);
        writeByte(out, crc, EpvFormat.CODEC_OPUS);
        writeInt(out, crc, AudioConstants.SAMPLE_RATE);
        writeByte(out, crc, AudioConstants.CHANNELS);
        writeByte(out, crc, AudioConstants.FRAME_DURATION_MILLIS);
        writeInt(out, crc, frameCount);
    }

    private static void writeByte(OutputStream out, CRC32 crc, int value) throws IOException {
        out.write(value & 0xFF);
        if (crc != null) {
            crc.update(value & 0xFF);
        }
    }

    private static void writeUnsignedShort(OutputStream out, CRC32 crc, int value) throws IOException {
        writeByte(out, crc, value >>> 8);
        writeByte(out, crc, value);
    }

    private static void writeInt(OutputStream out, CRC32 crc, int value) throws IOException {
        writeByte(out, crc, value >>> 24);
        writeByte(out, crc, value >>> 16);
        writeByte(out, crc, value >>> 8);
        writeByte(out, crc, value);
    }
}
