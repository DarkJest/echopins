package dev.echopins.domain.sync;

/**
 * Decides when a player's visible pin set is worth recalculating.
 *
 * <p>Lives in the domain layer, free of Minecraft types, specifically so it can be unit tested.
 * The rule is small but it gates every delta the server sends: when it was wrong, synchronisation
 * silently stopped working altogether and there was no way to see that from outside a running
 * server.
 */
public final class SyncThrottle {

    /**
     * Sentinel for "never recalculated".
     *
     * <p>Deliberately not {@link Long#MIN_VALUE}. The throttle compares {@code tick - lastSyncTick}
     * against an interval, and subtracting {@code Long.MIN_VALUE} overflows to a large negative
     * number, which makes the comparison conclude that no time has passed. Because the branch that
     * refreshes {@code lastSyncTick} then never runs, the subscription stays stuck on the sentinel
     * forever and no delta is ever sent. The sentinel is now tested explicitly rather than being
     * relied upon to behave arithmetically.
     */
    public static final long NEVER_SYNCED = -1L;

    private SyncThrottle() {
    }

    /**
     * @param lastSyncTick  {@link #NEVER_SYNCED}, or the tick of the last recalculation
     * @param tick          the current tick counter
     * @param intervalTicks minimum ticks between recalculations
     * @param movedChunk    whether the player crossed a chunk or dimension boundary
     * @return whether the visible set should be recomputed now
     */
    public static boolean shouldRecalculate(long lastSyncTick, long tick, int intervalTicks,
                                            boolean movedChunk) {
        if (lastSyncTick == NEVER_SYNCED) {
            // A new subscription, or one invalidated because a pin was created, updated or
            // removed. Must reach the player even if they are standing perfectly still.
            return true;
        }
        if (tick - lastSyncTick < intervalTicks) {
            return false;
        }
        return movedChunk;
    }
}
