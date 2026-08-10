package dev.echopins.infrastructure.audio;

import dev.echopins.domain.audio.AudioRef;
import dev.echopins.domain.audio.AudioStorageFullException;
import dev.echopins.domain.audio.AudioStore;
import dev.echopins.domain.audio.VoiceRecording;
import dev.echopins.infrastructure.audio.epv.EpvFormat;
import dev.echopins.infrastructure.audio.epv.EpvReader;
import dev.echopins.infrastructure.audio.epv.EpvWriter;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filesystem-backed {@link AudioStore} rooted inside the world save.
 *
 * <p>Layout: {@code <root>/<first two hex chars of the id>/<id>.epv}. The two-character shard
 * keeps any one directory from filling with thousands of entries, which several filesystems and
 * most file managers handle poorly.
 *
 * <p>Path safety has two independent layers. Filenames are built only from
 * {@link UUID#toString()}, which cannot contain a separator or a dot segment; and the resulting
 * path is then normalised and re-checked against the root, so a future refactor that loosens the
 * first layer still cannot escape the directory.
 */
public final class FileAudioStore implements AudioStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/AudioStore");

    private static final String TEMP_SUFFIX = ".tmp";

    private final Path root;
    private final AtomicLong totalBytes = new AtomicLong();
    private final Object mutationLock = new Object();

    public FileAudioStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        cleanupStaleTempFiles();
        totalBytes.set(computeTotalBytes());
        LOGGER.info("Audio store ready at {} ({} bytes in use)", this.root, totalBytes.get());
    }

    @Override
    public AudioRef store(VoiceRecording recording, long maxTotalBytes) throws IOException {
        byte[] encoded = EpvWriter.toBytes(recording);
        synchronized (mutationLock) {
            long current = totalBytes.get();
            long limit = Math.max(0L, maxTotalBytes);
            if (encoded.length > limit || current > limit - encoded.length) {
                throw new AudioStorageFullException(current, encoded.length, limit);
            }

            UUID audioId = UUID.randomUUID();
            Path target = pathFor(audioId);
            Files.createDirectories(target.getParent());

            Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
            try {
                // Force to disk before publishing, so a crash between write and move can only leave
                // a stale temp file behind - never a half-written file under the real name.
                try (FileChannel channel = FileChannel.open(temp,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encoded);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                moveIntoPlace(temp, target);
            } catch (IOException e) {
                deleteQuietly(temp);
                throw e;
            }

            totalBytes.addAndGet(encoded.length);
            return new AudioRef(audioId, encoded.length, recording.frameCount());
        }
    }

    @Override
    public Optional<VoiceRecording> load(UUID audioId) throws IOException {
        Path path = pathFor(audioId);
        byte[] data;
        try {
            long size = Files.size(path);
            if (size > EpvFormat.MAX_CONTAINER_BYTES) {
                throw new IOException("Audio file " + audioId + " is implausibly large: " + size);
            }
            data = Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
        return Optional.of(EpvReader.read(data));
    }

    @Override
    public boolean exists(UUID audioId) {
        return Files.isRegularFile(pathFor(audioId));
    }

    @Override
    public boolean delete(UUID audioId) {
        Path path = pathFor(audioId);
        synchronized (mutationLock) {
            try {
                long size = Files.exists(path) ? Files.size(path) : 0L;
                boolean deleted = Files.deleteIfExists(path);
                if (deleted) {
                    totalBytes.addAndGet(-size);
                    pruneEmptyShard(path.getParent());
                }
                return deleted;
            } catch (IOException e) {
                LOGGER.warn("Could not delete audio {}", audioId, e);
                return false;
            }
        }
    }

    @Override
    public long totalBytes() {
        return Math.max(0L, totalBytes.get());
    }

    @Override
    public Set<UUID> listAudioIds() throws IOException {
        Set<UUID> ids = new LinkedHashSet<>();
        forEachAudioFile(path -> {
            parseAudioId(path.getFileName().toString()).ifPresent(ids::add);
            return true;
        });
        return ids;
    }

    /** Recomputes the byte total from disk. Used at startup and after an admin cleanup. */
    public long recomputeTotalBytes() throws IOException {
        synchronized (mutationLock) {
            long total = computeTotalBytes();
            totalBytes.set(total);
            return total;
        }
    }

    private long computeTotalBytes() throws IOException {
        long[] total = {0L};
        forEachAudioFile(path -> {
            try {
                total[0] += Files.size(path);
            } catch (IOException e) {
                LOGGER.warn("Could not stat audio file {}", path, e);
            }
            return true;
        });
        return total[0];
    }

    /**
     * Resolves the on-disk path for an id.
     *
     * <p>{@code UUID.toString()} is a fixed-shape string of hex digits and dashes, so the
     * filename cannot contain a separator, a dot segment, or a drive letter. The containment
     * assertion afterwards is defence in depth.
     */
    private Path pathFor(UUID audioId) {
        String name = audioId.toString().toLowerCase(Locale.ROOT);
        String shard = name.substring(0, 2);
        Path resolved = root.resolve(shard).resolve(name + EpvFormat.FILE_EXTENSION).normalize();
        if (!resolved.startsWith(root)) {
            // Unreachable given the above, but a hard failure is the right response if it ever
            // becomes reachable.
            throw new IllegalStateException("Refusing to address a path outside the audio store");
        }
        return resolved;
    }

    private static Optional<UUID> parseAudioId(String fileName) {
        if (!fileName.endsWith(EpvFormat.FILE_EXTENSION)) {
            return Optional.empty();
        }
        String base = fileName.substring(0, fileName.length() - EpvFormat.FILE_EXTENSION.length());
        try {
            return Optional.of(UUID.fromString(base));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Some network filesystems cannot do this. The fallback is still safe because the
            // source is already fully written and forced to disk.
            LOGGER.debug("Atomic move unavailable, falling back to replace for {}", target, e);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Removes {@code .tmp} files left by a crash. They are never referenced by metadata, so
     * dropping them can only reclaim space.
     */
    private void cleanupStaleTempFiles() {
        int[] removed = {0};
        try (DirectoryStream<Path> shards = Files.newDirectoryStream(root)) {
            for (Path shard : shards) {
                if (!Files.isDirectory(shard)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(shard, "*" + TEMP_SUFFIX)) {
                    for (Path file : files) {
                        if (Files.deleteIfExists(file)) {
                            removed[0]++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not sweep temporary audio files", e);
        }
        if (removed[0] > 0) {
            LOGGER.info("Removed {} incomplete audio file(s) left by a previous shutdown", removed[0]);
        }
    }

    private void pruneEmptyShard(Path shard) {
        if (shard == null || shard.equals(root)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(shard)) {
            if (!entries.iterator().hasNext()) {
                Files.deleteIfExists(shard);
            }
        } catch (IOException e) {
            LOGGER.debug("Could not prune shard directory {}", shard, e);
        }
    }

    @FunctionalInterface
    private interface FileVisitor {
        /** @return {@code false} to stop iterating */
        boolean visit(Path path) throws IOException;
    }

    private void forEachAudioFile(FileVisitor visitor) throws IOException {
        Set<Path> shards = new HashSet<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    shards.add(entry);
                }
            }
        }
        for (Path shard : shards) {
            try (DirectoryStream<Path> files =
                         Files.newDirectoryStream(shard, "*" + EpvFormat.FILE_EXTENSION)) {
                for (Path file : files) {
                    if (Files.isRegularFile(file) && !visitor.visit(file)) {
                        return;
                    }
                }
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            LOGGER.debug("Could not remove temporary file {}", path);
        }
    }
}
