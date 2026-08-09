package dev.echopins.domain.limits;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    @DisplayName("A full bucket allows a burst up to its capacity, then refuses")
    void allowsBurstThenRefuses() {
        var limiter = new TokenBucketRateLimiter<UUID>(() -> 3, () -> 1.0D);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 0L));
    }

    @Test
    @DisplayName("Tokens come back as time passes")
    void refillsOverTime() {
        var limiter = new TokenBucketRateLimiter<UUID>(() -> 2, () -> 1.0D);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 500L));

        assertTrue(limiter.tryAcquire(PLAYER, 1_000L), "one token per second should be back");
        assertFalse(limiter.tryAcquire(PLAYER, 1_000L));
    }

    @Test
    @DisplayName("The bucket never accumulates more than its capacity while idle")
    void doesNotOverfill() {
        var limiter = new TokenBucketRateLimiter<UUID>(() -> 2, () -> 1.0D);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        // Idle for an hour: should still only be worth `capacity` actions.
        assertTrue(limiter.tryAcquire(PLAYER, 3_600_000L));
        assertTrue(limiter.tryAcquire(PLAYER, 3_600_000L));
        assertFalse(limiter.tryAcquire(PLAYER, 3_600_000L));
    }

    @Test
    @DisplayName("Limits are tracked per key")
    void keysAreIndependent() {
        var limiter = new TokenBucketRateLimiter<UUID>(() -> 1, () -> 1.0D);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 0L));
        assertTrue(limiter.tryAcquire(OTHER, 0L), "one player must not throttle another");
    }

    @Test
    @DisplayName("The wait hint reaches zero exactly when the next attempt succeeds")
    void reportsAccurateWait() {
        var limiter = new TokenBucketRateLimiter<UUID>(() -> 1, () -> 2.0D);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        long wait = limiter.millisUntilAvailable(PLAYER, 0L);
        assertTrue(wait > 0 && wait <= 500, "expected up to 500ms, got " + wait);

        assertFalse(limiter.tryAcquire(PLAYER, wait - 1));
        assertTrue(limiter.tryAcquire(PLAYER, wait));
        assertEquals(0L, limiter.millisUntilAvailable(OTHER, 0L), "an untracked key waits for nothing");
    }

    @Test
    @DisplayName("A clock that jumps backwards never grants free tokens")
    void toleratesBackwardsClock() {
        var limiter = new TokenBucketRateLimiter<UUID>(() -> 1, () -> 1.0D);

        assertTrue(limiter.tryAcquire(PLAYER, 10_000L));
        assertFalse(limiter.tryAcquire(PLAYER, 5_000L), "going back in time must not refill");
        assertFalse(limiter.tryAcquire(PLAYER, 5_500L));
    }

    @Test
    @DisplayName("Idle keys are pruned and forgetting a key resets it")
    void pruningAndForgetting() {
        var limiter = new TokenBucketRateLimiter<UUID>(() -> 1, () -> 0.001D);

        limiter.tryAcquire(PLAYER, 0L);
        limiter.tryAcquire(OTHER, 0L);
        assertEquals(2, limiter.trackedKeys());

        limiter.forget(PLAYER);
        assertEquals(1, limiter.trackedKeys());
        assertTrue(limiter.tryAcquire(PLAYER, 0L), "a forgotten key starts fresh");

        limiter.pruneIdle(60_000L, 30_000L);
        assertEquals(0, limiter.trackedKeys());
    }
}
