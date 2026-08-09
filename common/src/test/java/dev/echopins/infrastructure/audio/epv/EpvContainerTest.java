package dev.echopins.infrastructure.audio.epv;

import dev.echopins.domain.audio.AudioConstants;
import dev.echopins.domain.audio.VoiceRecording;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpvContainerTest {

    private static VoiceRecording sampleRecording(int frames) {
        Random random = new Random(1234L);
        VoiceRecording.Builder builder = VoiceRecording.builder(10_000, 4_000_000);
        for (int i = 0; i < frames; i++) {
            byte[] frame = new byte[20 + random.nextInt(80)];
            random.nextBytes(frame);
            assertTrue(builder.addFrame(frame), "frame " + i + " should be accepted");
        }
        return builder.build();
    }

    @Test
    @DisplayName("A written container reads back with byte-identical frames")
    void roundTripPreservesFrames() throws IOException {
        VoiceRecording original = sampleRecording(120);
        byte[] encoded = EpvWriter.toBytes(original);

        VoiceRecording decoded = EpvReader.read(encoded);

        assertEquals(original.frameCount(), decoded.frameCount());
        assertEquals(original.totalPayloadBytes(), decoded.totalPayloadBytes());
        for (int i = 0; i < original.frameCount(); i++) {
            assertArrayEquals(original.frame(i), decoded.frame(i), "frame " + i);
        }
        assertEquals(120 * AudioConstants.FRAME_DURATION_MILLIS, decoded.durationMillis());
    }

    @Test
    @DisplayName("Encoded size matches the format's own size calculation")
    void encodedSizeMatchesPrediction() throws IOException {
        VoiceRecording recording = sampleRecording(37);
        byte[] encoded = EpvWriter.toBytes(recording);
        assertEquals(EpvFormat.containerSize(recording.frameCount(), recording.totalPayloadBytes()),
                encoded.length);
    }

    @Test
    @DisplayName("An empty recording is a valid container")
    void emptyRecordingRoundTrips() throws IOException {
        VoiceRecording empty = VoiceRecording.ofValidatedFrames(List.of());
        VoiceRecording decoded = EpvReader.read(EpvWriter.toBytes(empty));
        assertEquals(0, decoded.frameCount());
        assertTrue(decoded.isEmpty());
    }

    @Test
    @DisplayName("Wrong magic is rejected")
    void rejectsBadMagic() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(5));
        encoded[0] = 'X';
        EpvFormatException error = assertThrows(EpvFormatException.class, () -> EpvReader.read(encoded));
        assertTrue(error.getMessage().contains("magic"));
    }

    @Test
    @DisplayName("An unknown format version is rejected rather than guessed at")
    void rejectsUnknownVersion() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(5));
        encoded[4] = 99;
        assertThrows(EpvFormatException.class, () -> EpvReader.read(encoded));
    }

    @Test
    @DisplayName("A flipped payload byte is caught by the checksum")
    void detectsSilentCorruption() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(40));
        int middle = EpvFormat.HEADER_BYTES + 10;
        encoded[middle] ^= 0x40;

        EpvFormatException error = assertThrows(EpvFormatException.class, () -> EpvReader.read(encoded));
        assertTrue(error.getMessage().contains("Checksum"), error.getMessage());
    }

    @Test
    @DisplayName("Truncation at any point is rejected, never partially decoded")
    void rejectsTruncationAtEveryOffset() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(30));
        for (int length = 0; length < encoded.length; length++) {
            byte[] truncated = Arrays.copyOf(encoded, length);
            int offset = length;
            assertThrows(EpvFormatException.class, () -> EpvReader.read(truncated),
                    "truncation to " + offset + " bytes must be rejected");
        }
    }

    @Test
    @DisplayName("Extra bytes appended after the checksum are rejected")
    void rejectsTrailingGarbage() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(4));
        byte[] padded = Arrays.copyOf(encoded, encoded.length + 8);
        assertThrows(EpvFormatException.class, () -> EpvReader.read(padded));
    }

    @Test
    @DisplayName("A huge declared frame count cannot force a huge allocation")
    void rejectsImplausibleFrameCount() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(2));
        // Overwrite frameCount with Integer.MAX_VALUE.
        encoded[12] = 0x7F;
        encoded[13] = (byte) 0xFF;
        encoded[14] = (byte) 0xFF;
        encoded[15] = (byte) 0xFF;

        EpvFormatException error = assertThrows(EpvFormatException.class, () -> EpvReader.read(encoded));
        assertTrue(error.getMessage().contains("Frame count out of range"), error.getMessage());
    }

    @Test
    @DisplayName("A frame length pointing past the end of the file is rejected")
    void rejectsOverlongFrameLength() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(3));
        // First frame's 16-bit length prefix sits right after the header.
        encoded[EpvFormat.HEADER_BYTES] = 0x0F;
        encoded[EpvFormat.HEADER_BYTES + 1] = (byte) 0xFF;
        assertThrows(EpvFormatException.class, () -> EpvReader.read(encoded));
    }

    @Test
    @DisplayName("A zero-length frame is rejected")
    void rejectsZeroLengthFrame() throws IOException {
        byte[] encoded = EpvWriter.toBytes(sampleRecording(3));
        encoded[EpvFormat.HEADER_BYTES] = 0;
        encoded[EpvFormat.HEADER_BYTES + 1] = 0;
        assertThrows(EpvFormatException.class, () -> EpvReader.read(encoded));
    }

    @Test
    @DisplayName("Data too short to hold a header is rejected without indexing past the end")
    void rejectsShortBuffers() {
        for (int length = 0; length < EpvFormat.HEADER_BYTES + EpvFormat.CRC_BYTES; length++) {
            byte[] data = new byte[length];
            System.arraycopy(EpvFormat.MAGIC, 0, data, 0, Math.min(length, EpvFormat.MAGIC.length));
            assertThrows(EpvFormatException.class, () -> EpvReader.read(data));
        }
    }

    @Test
    @DisplayName("The builder enforces frame-count and total-byte ceilings")
    void builderEnforcesLimits() {
        VoiceRecording.Builder limited = VoiceRecording.builder(3, 1_000_000);
        byte[] frame = new byte[50];
        assertTrue(limited.addFrame(frame));
        assertTrue(limited.addFrame(frame));
        assertTrue(limited.addFrame(frame));
        assertTrue(limited.isFull());
        assertEquals(false, limited.addFrame(frame), "fourth frame must be refused");

        VoiceRecording.Builder byteLimited = VoiceRecording.builder(1000, 100);
        assertTrue(byteLimited.addFrame(new byte[60]));
        assertEquals(false, byteLimited.addFrame(new byte[60]), "would exceed the byte ceiling");
    }

    @Test
    @DisplayName("The builder refuses frames larger than a single Opus packet can be")
    void builderRefusesOversizedFrames() {
        VoiceRecording.Builder builder = VoiceRecording.builder(10, 1_000_000);
        assertEquals(false, builder.addFrame(new byte[AudioConstants.MAX_FRAME_BYTES + 1]));
        assertEquals(false, builder.addFrame(new byte[0]));
        assertEquals(false, builder.addFrame(null));
    }

    @Test
    @DisplayName("The builder copies frames so later reuse of the caller's buffer is safe")
    void builderCopiesFrames() {
        VoiceRecording.Builder builder = VoiceRecording.builder(10, 10_000);
        byte[] reused = {1, 2, 3, 4};
        builder.addFrame(reused);
        Arrays.fill(reused, (byte) 9);

        assertArrayEquals(new byte[]{1, 2, 3, 4}, builder.build().frame(0));
    }
}
