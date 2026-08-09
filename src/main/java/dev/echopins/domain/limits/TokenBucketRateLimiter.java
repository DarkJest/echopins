package dev.echopins.domain.limits;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * A token bucket that refills continuously.
 *
 * <p>Chosen over a fixed window because it tolerates a short natural burst - a player creating
 * two pins in quick succession while setting up a base - without allowing a sustained flood.
 *
 * <p>Thread-safe: network packets arrive on the netty thread and are validated before being
 * handed to the main thread, so limiters are touched from more than one thread.
 */
public final class TokenBucketRateLimiter<K> implements RateLimiter<K> {

    private static final class Bucket {
        double tokens;
        long lastRefillMillis;
        long lastTouchedMillis;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillMillis = now;
            this.lastTouchedMillis = now;
        }
    }

    private final Map<K, Bucket> buckets = new ConcurrentHashMap<>();
    private final IntSupplier capacity;
    private final DoubleSupplier refillPerSecond;

    /**
     * @param capacity        maximum burst size; re-read on each call so config reloads apply
     * @param refillPerSecond tokens added per second
     */
    public TokenBucketRateLimiter(IntSupplier capacity, DoubleSupplier refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    @Override
    public boolean tryAcquire(K key, long nowMillis) {
        int cap = Math.max(1, capacity.getAsInt());
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(cap, nowMillis));
        synchronized (bucket) {
            refill(bucket, cap, nowMillis);
            bucket.lastTouchedMillis = nowMillis;
            if (bucket.tokens >= 1.0D) {
                bucket.tokens -= 1.0D;
                return true;
            }
            return false;
        }
    }

    @Override
    public long millisUntilAvailable(K key, long nowMillis) {
        int cap = Math.max(1, capacity.getAsInt());
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return 0L;
        }
        synchronized (bucket) {
            refill(bucket, cap, nowMillis);
            if (bucket.tokens >= 1.0D) {
                return 0L;
            }
            double rate = refillPerSecond.getAsDouble();
            if (rate <= 0.0D) {
                return Long.MAX_VALUE;
            }
            double needed = 1.0D - bucket.tokens;
            return (long) Math.ceil(needed / rate * 1000.0D);
        }
    }

    private void refill(Bucket bucket, int cap, long nowMillis) {
        long elapsed = nowMillis - bucket.lastRefillMillis;
        if (elapsed <= 0) {
            // Clock went backwards (or two events share a timestamp); never award tokens for it.
            bucket.lastRefillMillis = nowMillis;
            return;
        }
        double rate = refillPerSecond.getAsDouble();
        bucket.tokens = Math.min(cap, bucket.tokens + elapsed / 1000.0D * rate);
        bucket.lastRefillMillis = nowMillis;
    }

    @Override
    public void forget(K key) {
        buckets.remove(key);
    }

    @Override
    public void pruneIdle(long nowMillis, long idleMillis) {
        buckets.entrySet().removeIf(e -> {
            Bucket b = e.getValue();
            synchronized (b) {
                return nowMillis - b.lastTouchedMillis > idleMillis;
            }
        });
    }

    /** Number of tracked keys. Exposed for the admin stats command. */
    public int trackedKeys() {
        return buckets.size();
    }
}
