package dev.echopins.infrastructure.audio;

import dev.echopins.domain.audio.AudioRef;
import dev.echopins.domain.audio.VoiceRecording;
import dev.echopins.infrastructure.audio.epv.EpvFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAudioStoreTest {

    private static VoiceRecording recording(int frames, byte fill) {
        VoiceRecording.Builder builder = VoiceRecording.builder(1000, 1_000_000);
        for (int i = 0; i < frames; i++) {
            byte[] frame = new byte[64];
            java.util.Arrays.fill(frame, fill);
            frame[0] = (byte) i;
            builder.addFrame(frame);
        }
        return builder.build();
    }

    @Test
    @DisplayName("A stored recording loads back identically")
    void storeAndLoad(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        VoiceRecording original = recording(25, (byte) 7);

        AudioRef ref = store.store(original);
        VoiceRecording loaded = store.load(ref.audioId()).orElseThrow();

        assertEquals(25, ref.frameCount());
        assertEquals(500, ref.durationMillis());
        assertEquals(original.frameCount(), loaded.frameCount());
        for (int i = 0; i < original.frameCount(); i++) {
            assertArrayEquals(original.frame(i), loaded.frame(i));
        }
    }

    @Test
    @DisplayName("Loading an unknown id returns empty rather than throwing")
    void missingAudioIsEmpty(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        assertTrue(store.load(UUID.randomUUID()).isEmpty());
        assertFalse(store.exists(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Deletion is idempotent and reclaims the accounted bytes")
    void deleteIsIdempotent(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        AudioRef ref = store.store(recording(10, (byte) 1));

        assertTrue(store.totalBytes() > 0);
        assertTrue(store.delete(ref.audioId()));
        assertFalse(store.delete(ref.audioId()), "deleting twice must not fail");
        assertEquals(0L, store.totalBytes());
        assertTrue(store.load(ref.audioId()).isEmpty());
    }

    @Test
    @DisplayName("Byte accounting survives a restart")
    void totalBytesRecomputedOnOpen(@TempDir Path dir) throws IOException {
        FileAudioStore first = new FileAudioStore(dir);
        AudioRef a = first.store(recording(10, (byte) 1));
        AudioRef b = first.store(recording(20, (byte) 2));
        long expected = first.totalBytes();

        FileAudioStore reopened = new FileAudioStore(dir);

        assertEquals(expected, reopened.totalBytes());
        assertEquals(Set.of(a.audioId(), b.audioId()), reopened.listAudioIds());
    }

    @Test
    @DisplayName("A damaged file is reported as damaged, not returned as audio")
    void damagedFileIsRejected(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        AudioRef ref = store.store(recording(12, (byte) 3));

        Path file = findStoredFile(dir);
        byte[] data = Files.readAllBytes(file);
        data[data.length / 2] ^= 0x55;
        Files.write(file, data);

        assertThrows(EpvFormatException.class, () -> store.load(ref.audioId()));
    }

    @Test
    @DisplayName("A file that is not an EPV container at all is rejected")
    void foreignFileIsRejected(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        AudioRef ref = store.store(recording(5, (byte) 4));

        Files.write(findStoredFile(dir), "this is definitely not audio".getBytes());

        assertThrows(EpvFormatException.class, () -> store.load(ref.audioId()));
    }

    @Test
    @DisplayName("Temporary files left by a crash are swept on open and never listed as audio")
    void sweepsStaleTempFiles(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        store.store(recording(5, (byte) 1));

        Path shard = findStoredFile(dir).getParent();
        Path stale = shard.resolve(UUID.randomUUID() + ".epv.tmp");
        Files.write(stale, new byte[]{1, 2, 3});

        FileAudioStore reopened = new FileAudioStore(dir);

        assertFalse(Files.exists(stale), "incomplete files must not survive a restart");
        assertEquals(1, reopened.listAudioIds().size());
    }

    @Test
    @DisplayName("Files that are not valid audio ids are ignored by the listing")
    void ignoresUnrelatedFiles(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        AudioRef ref = store.store(recording(5, (byte) 1));

        Path shard = findStoredFile(dir).getParent();
        Files.write(shard.resolve("notes.txt"), "hello".getBytes());
        Files.write(shard.resolve("not-a-uuid.epv"), "hello".getBytes());

        assertEquals(Set.of(ref.audioId()), store.listAudioIds());
    }

    @Test
    @DisplayName("Stored files stay inside the store root and are named only from the id")
    void filesStayInsideRoot(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        AudioRef ref = store.store(recording(3, (byte) 1));

        Path file = findStoredFile(dir);
        Path root = dir.toAbsolutePath().normalize();

        assertTrue(file.toAbsolutePath().normalize().startsWith(root));
        assertEquals(ref.audioId() + ".epv", file.getFileName().toString());
        assertEquals(2, file.getParent().getFileName().toString().length(), "two-character shard");
    }

    @Test
    @DisplayName("An empty recording still produces a readable file")
    void emptyRecordingIsStorable(@TempDir Path dir) throws IOException {
        FileAudioStore store = new FileAudioStore(dir);
        AudioRef ref = store.store(VoiceRecording.ofValidatedFrames(List.of()));

        assertEquals(0, ref.frameCount());
        assertTrue(store.load(ref.audioId()).orElseThrow().isEmpty());
    }

    private static Path findStoredFile(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".epv"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no stored audio file found"));
        }
    }
}
