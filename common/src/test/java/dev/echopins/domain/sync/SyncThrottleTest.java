package dev.echopins.domain.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the subscription throttle.
 *
 * <p>The original implementation used {@code Long.MIN_VALUE} as the "never synced" sentinel and
 * then computed {@code tick - lastSyncTick}. That subtraction overflows to a large negative
 * number, so the throttle concluded no time had passed and skipped the player - permanently,
 * because the branch that refreshes {@code lastSyncTick} was never reached. The visible effect was
 * that delta synchronisation never ran at all: a player only received the snapshot sent on join,
 * so a pin they had just created did not appear until they relogged or changed dimension.
 */
class SyncThrottleTest {

    private static final int INTERVAL = 20;
    private static final long NEVER = SyncThrottle.NEVER_SYNCED;

    @Test
    @DisplayName("A brand new subscription is recalculated immediately")
    void neverSyncedRecalculatesAtOnce() {
        assertTrue(SyncThrottle.shouldRecalculate(NEVER, 0L, INTERVAL, false));
        assertTrue(SyncThrottle.shouldRecalculate(NEVER, 1L, INTERVAL, false));
        assertTrue(SyncThrottle.shouldRecalculate(NEVER, 20_000L, INTERVAL, false));
    }

    @Test
    @DisplayName("The never-synced sentinel does not overflow the throttle arithmetic")
    void sentinelDoesNotOverflow() {
        // The exact shape of the original bug: subtracting Long.MIN_VALUE wraps to a huge
        // negative value, so the throttle would suppress the recalculation forever.
        long overflowing = 1L - Long.MIN_VALUE;
        assertTrue(overflowing < INTERVAL,
                "sanity check: subtracting Long.MIN_VALUE really does overflow");

        // The real sentinel must be immune to that at any tick value.
        assertTrue(SyncThrottle.shouldRecalculate(NEVER, 1L, INTERVAL, false));
        assertTrue(SyncThrottle.shouldRecalculate(NEVER, Long.MAX_VALUE / 2, INTERVAL, false));
    }

    @Test
    @DisplayName("Within the interval nothing is recalculated, even after moving")
    void throttleSuppressesFrequentRecalculation() {
        assertFalse(SyncThrottle.shouldRecalculate(100L, 105L, INTERVAL, true));
        assertFalse(SyncThrottle.shouldRecalculate(100L, 119L, INTERVAL, true));
    }

    @Test
    @DisplayName("Past the interval, a player who crossed a chunk boundary is recalculated")
    void recalculatesAfterMoving() {
        assertTrue(SyncThrottle.shouldRecalculate(100L, 120L, INTERVAL, true));
        assertTrue(SyncThrottle.shouldRecalculate(100L, 500L, INTERVAL, true));
    }

    @Test
    @DisplayName("A player standing still costs nothing, however long they stand there")
    void standingStillIsFree() {
        assertFalse(SyncThrottle.shouldRecalculate(100L, 120L, INTERVAL, false));
        assertFalse(SyncThrottle.shouldRecalculate(100L, 100_000L, INTERVAL, false));
    }

    @Test
    @DisplayName("Invalidating a subscription forces a recalculation even while standing still")
    void invalidationBeatsTheStandingStillShortcut() {
        // This is what creating or deleting a pin does: it resets the subscription to the
        // sentinel so the next tick pushes a delta, whether or not the player has moved. This is
        // the case that was broken, and it is why an author could not see their own new pin.
        assertTrue(SyncThrottle.shouldRecalculate(NEVER, 12_345L, INTERVAL, false),
                "a newly created pin must reach a stationary player");
    }

    @Test
    @DisplayName("An interval of zero still recalculates only when something changed")
    void zeroIntervalStillRequiresAChange() {
        assertTrue(SyncThrottle.shouldRecalculate(100L, 100L, 0, true));
        assertFalse(SyncThrottle.shouldRecalculate(100L, 100L, 0, false));
    }
}
