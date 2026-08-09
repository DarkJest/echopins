package dev.echopins.infrastructure.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The mod's thread pools.
 *
 * <p>Two bounded pools, both created once for the lifetime of a server:
 *
 * <ul>
 *   <li><b>IO</b> - reading and writing audio containers. Disk work must never happen on the
 *       server thread, where a slow write would show up as a tick lag spike.</li>
 *   <li><b>Audio</b> - the 20 ms pacing of playback frames. Scheduled rather than thread-per-
 *       playback, so a hundred simultaneous playbacks still cost two threads.</li>
 * </ul>
 *
 * <p>Every thread is named and carries an uncaught-exception handler, so anything that escapes a
 * task lands in the log with context instead of vanishing.
 */
public final class EchoPinsExecutors {

    private static final Logger LOGGER = LoggerFactory.getLogger("EchoPins/Executors");

    /** Disk work is latency-tolerant; a small pool avoids thrashing a spinning disk. */
    private static final int IO_THREADS = 2;
    private static final int AUDIO_THREADS = 2;

    /**
     * Bounds the IO backlog. If this many audio writes are already queued the server is in
     * trouble, and failing the request tells the player something is wrong rather than queueing
     * work that will never drain.
     */
    private static final int IO_QUEUE_CAPACITY = 256;

    private static volatile EchoPinsExecutors instance;

    private final ThreadPoolExecutor io;
    private final ScheduledThreadPoolExecutor audio;

    private EchoPinsExecutors() {
        this.io = new ThreadPoolExecutor(
                IO_THREADS, IO_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(IO_QUEUE_CAPACITY),
                namedFactory("EchoPins-IO"));
        this.audio = new ScheduledThreadPoolExecutor(AUDIO_THREADS, namedFactory("EchoPins-Audio"));
        // Without this, a cancelled playback's task would sit in the queue until its next run.
        this.audio.setRemoveOnCancelPolicy(true);
    }

    public static synchronized EchoPinsExecutors start() {
        if (instance == null) {
            instance = new EchoPinsExecutors();
            LOGGER.debug("Started EchoPins executors ({} IO, {} audio)", IO_THREADS, AUDIO_THREADS);
        }
        return instance;
    }

    /** @return the running executors, or {@code null} if the server is not running */
    public static EchoPinsExecutors current() {
        return instance;
    }

    public ExecutorService io() {
        return io;
    }

    public ScheduledExecutorService audio() {
        return audio;
    }

    /**
     * Submits disk work, reporting back-pressure rather than throwing into a packet handler.
     *
     * @return {@code false} if the queue is full and the task was not accepted
     */
    public boolean submitIo(Runnable task) {
        try {
            io.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            LOGGER.warn("EchoPins IO queue is saturated; dropping a task");
            return false;
        }
    }

    /** Shuts both pools down, waiting briefly so in-flight audio writes can finish. */
    public static synchronized void stop() {
        EchoPinsExecutors executors = instance;
        if (executors == null) {
            return;
        }
        instance = null;
        shutdown(executors.audio, "audio");
        // IO last: a playback being torn down must not race an audio file still being written.
        shutdown(executors.io, "IO");
        LOGGER.debug("EchoPins executors stopped");
    }

    private static void shutdown(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("EchoPins {} pool did not stop in time; forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, error) ->
                    LOGGER.error("Uncaught exception on {}", t.getName(), error));
            return thread;
        };
    }
}
