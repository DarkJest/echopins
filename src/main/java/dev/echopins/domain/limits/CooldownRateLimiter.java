package dev.echopins.domain.limits;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Enforces a minimum gap between two actions by the same key.
 *
 * <p>Used for {@code createCooldownSeconds} and {@code playbackCooldownMillis}, where the rule
 * really is "not again for N milliseconds" rather than "N per period". Modelling that as a token
 * bucket would need an infinite refill rate to express "no cooldown", which is exactly the kind
 * of special case that hides bugs.
 *
 * <p>A configured cooldown of zero or less disables throttling.
 */
public final class CooldownRateLimiter<K> implements RateLimiter<K> {

    private final Map<K, Long> lastAllowedMillis = new ConcurrentHashMap<>();
    private final IntSupplier cooldownMillis;

    public CooldownRateLimiter(IntSupplier cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    @Override
    public boolean tryAcquire(K key, long nowMillis) {
        int cooldown = cooldownMillis.getAsInt();
        if (cooldown <= 0) {
            return true;
        }
        // merge() makes the read-decide-write atomic, so two packets arriving together cannot
        // both observe an expired cooldown and both be allowed through.
        Long previous = lastAllowedMillis.get(key);
        if (previous != null && !hasElapsed(previous, nowMillis, cooldown)) {
            return false;
        }
        Long stored = lastAllowedMillis.merge(key, nowMillis,
                (old, candidate) -> hasElapsed(old, candidate, cooldown) ? candidate : old);
        return stored != null && stored == nowMillis;
    }

    @Override
    public long millisUntilAvailable(K key, long nowMillis) {
        int cooldown = cooldownMillis.getAsInt();
        if (cooldown <= 0) {
            return 0L;
        }
        Long previous = lastAllowedMillis.get(key);
        if (previous == null || hasElapsed(previous, nowMillis, cooldown)) {
            return 0L;
        }
        return Math.max(0L, previous + cooldown - nowMillis);
    }

    /** A clock that jumped backwards must not shorten the wait, so elapsed time is floored at 0. */
    private static boolean hasElapsed(long previous, long nowMillis, int cooldown) {
        long elapsed = nowMillis - previous;
        return elapsed < 0 || elapsed >= cooldown;
    }

    @Override
    public void forget(K key) {
        lastAllowedMillis.remove(key);
    }

    @Override
    public void pruneIdle(long nowMillis, long idleMillis) {
        lastAllowedMillis.entrySet().removeIf(e -> nowMillis - e.getValue() > idleMillis);
    }

    public int trackedKeys() {
        return lastAllowedMillis.size();
    }
}
