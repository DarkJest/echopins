package dev.echopins.domain.limits;

/**
 * Throttles an action per key (normally a player UUID).
 *
 * <p>Kept separate from the packet handlers on purpose: handlers decide <em>what</em> a request
 * means, limiters decide <em>how often</em> it is allowed. That split is what makes the limits
 * unit-testable without a server.
 *
 * @param <K> the key type
 */
public interface RateLimiter<K> {

    /**
     * Attempts to consume one unit of allowance.
     *
     * @param key key to throttle on
     * @param nowMillis current time; injected rather than read from the clock so tests are
     *                  deterministic and so all limiters in a request share one timestamp
     * @return {@code true} if the action may proceed
     */
    boolean tryAcquire(K key, long nowMillis);

    /**
     * Milliseconds until {@link #tryAcquire} would succeed, or 0 if it would succeed now.
     * Used to tell the player how long to wait instead of just refusing.
     */
    long millisUntilAvailable(K key, long nowMillis);

    /** Drops per-key state, for example when a player disconnects. */
    void forget(K key);

    /** Drops state for keys untouched for longer than {@code idleMillis}. */
    void pruneIdle(long nowMillis, long idleMillis);
}
