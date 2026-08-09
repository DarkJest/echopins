package dev.echopins.domain.limits;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownRateLimiterTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    @DisplayName("A minimum gap is enforced between actions")
    void enforcesGap() {
        var limiter = new CooldownRateLimiter<UUID>(() -> 2_000);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 1_999L));
        assertTrue(limiter.tryAcquire(PLAYER, 2_000L));
        assertFalse(limiter.tryAcquire(PLAYER, 3_000L));
    }

    @Test
    @DisplayName("A cooldown of zero disables throttling entirely")
    void zeroCooldownIsUnlimited() {
        var limiter = new CooldownRateLimiter<UUID>(() -> 0);

        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire(PLAYER, 0L), "attempt " + i);
        }
        assertEquals(0L, limiter.millisUntilAvailable(PLAYER, 0L));
    }

    @Test
    @DisplayName("The reported wait counts down to the moment the next attempt succeeds")
    void reportsRemainingWait() {
        var limiter = new CooldownRateLimiter<UUID>(() -> 5_000);

        assertTrue(limiter.tryAcquire(PLAYER, 1_000L));
        assertEquals(5_000L, limiter.millisUntilAvailable(PLAYER, 1_000L));
        assertEquals(1L, limiter.millisUntilAvailable(PLAYER, 5_999L));
        assertEquals(0L, limiter.millisUntilAvailable(PLAYER, 6_000L));
    }

    @Test
    @DisplayName("A refused attempt does not extend the cooldown")
    void refusalDoesNotResetTheClock() {
        var limiter = new CooldownRateLimiter<UUID>(() -> 1_000);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 500L));
        assertFalse(limiter.tryAcquire(PLAYER, 900L));
        assertTrue(limiter.tryAcquire(PLAYER, 1_000L),
                "spamming while on cooldown must not push the next allowed time back");
    }

    @Test
    @DisplayName("Cooldowns are tracked per key")
    void keysAreIndependent() {
        var limiter = new CooldownRateLimiter<UUID>(() -> 1_000);

        assertTrue(limiter.tryAcquire(PLAYER, 0L));
        assertTrue(limiter.tryAcquire(OTHER, 0L));
        assertFalse(limiter.tryAcquire(PLAYER, 100L));
    }

    @Test
    @DisplayName("A clock that jumps backwards does not lock a player out")
    void toleratesBackwardsClock() {
        var limiter = new CooldownRateLimiter<UUID>(() -> 1_000);

        assertTrue(limiter.tryAcquire(PLAYER, 10_000L));
        assertTrue(limiter.tryAcquire(PLAYER, 5_000L), "a backwards jump releases rather than blocks");
    }

    @Test
    @DisplayName("Forgetting and pruning drop per-key state")
    void forgetAndPrune() {
        var limiter = new CooldownRateLimiter<UUID>(() -> 10_000);

        limiter.tryAcquire(PLAYER, 0L);
        limiter.tryAcquire(OTHER, 0L);
        assertEquals(2, limiter.trackedKeys());

        limiter.forget(PLAYER);
        assertEquals(1, limiter.trackedKeys());

        limiter.pruneIdle(100_000L, 30_000L);
        assertEquals(0, limiter.trackedKeys());
    }
}
