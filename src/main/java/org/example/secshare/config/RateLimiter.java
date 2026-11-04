package org.example.secshare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-key token-bucket rate limiter. No external dependency and no shared store:
 * each application instance limits independently, which is sufficient for the single-node
 * deployment and degrades safely (a client is at worst allowed N×instances requests).
 *
 * <p>Each key (typically "{spec}|{clientIp}") gets a bucket that refills continuously at
 * {@code capacity / window} tokens per millisecond up to {@code capacity}. Idle buckets are
 * swept periodically so the map does not grow unbounded under IP churn.</p>
 */
@Component
public class RateLimiter {

    /** A spec is a bucket size plus the window over which it fully refills. */
    public record Spec(long capacity, long windowMs) {}

    /** Outcome of a limiter check: whether the request is allowed and, if not, when to retry. */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private final Map<String, Spec> specs;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(
            @Value("${app.ratelimit.auth.capacity:10}") long authCapacity,
            @Value("${app.ratelimit.auth.window-seconds:60}") long authWindowSeconds,
            @Value("${app.ratelimit.download.capacity:30}") long downloadCapacity,
            @Value("${app.ratelimit.download.window-seconds:60}") long downloadWindowSeconds
    ) {
        this.specs = Map.of(
                "auth", new Spec(authCapacity, authWindowSeconds * 1000L),
                "download", new Spec(downloadCapacity, downloadWindowSeconds * 1000L)
        );
    }

    /**
     * Attempts to consume one token for {@code clientKey} under the named {@code spec}.
     * An unknown spec always allows (fail-open) so a misrouted path is never silently blocked.
     */
    public Decision tryAcquire(String spec, String clientKey) {
        Spec s = specs.get(spec);
        if (s == null) {
            return new Decision(true, 0);
        }
        Bucket bucket = buckets.computeIfAbsent(spec + "|" + clientKey, k -> new Bucket(s));
        return bucket.tryConsume();
    }

    /** Evicts buckets that have sat full (idle) for a while, bounding memory under IP churn. */
    @Scheduled(fixedDelayString = "${app.ratelimit.sweep-ms:300000}",
            initialDelayString = "${app.ratelimit.sweep-ms:300000}")
    public void sweepIdleBuckets() {
        long now = System.currentTimeMillis();
        buckets.values().removeIf(b -> b.isIdleFull(now));
    }

    /** A single token bucket; all mutation is guarded by {@code synchronized} on the instance. */
    private static final class Bucket {
        private final long capacity;
        private final double refillPerMs;
        private final long windowMs;
        private double tokens;
        private long lastRefill;

        Bucket(Spec spec) {
            this.capacity = spec.capacity();
            this.windowMs = spec.windowMs();
            this.refillPerMs = (double) spec.capacity() / spec.windowMs();
            this.tokens = spec.capacity();
            this.lastRefill = System.currentTimeMillis();
        }

        synchronized Decision tryConsume() {
            refill(System.currentTimeMillis());
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new Decision(true, 0);
            }
            long waitMs = (long) Math.ceil((1.0 - tokens) / refillPerMs);
            return new Decision(false, Math.max(1, (waitMs + 999) / 1000));
        }

        synchronized boolean isIdleFull(long now) {
            refill(now);
            return tokens >= capacity && (now - lastRefill) >= windowMs;
        }

        private void refill(long now) {
            if (now > lastRefill) {
                tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerMs);
                lastRefill = now;
            }
        }
    }
}
