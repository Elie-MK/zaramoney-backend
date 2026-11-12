package com.zekodnix.zaramoney.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory fixed-window counter.
 * Good enough for a single instance; with several instances behind a load balancer, move it to Redis
 * (e.g. Bucket4j + Redis) so all instances share the counters.
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one hit for {@code key}.
     * @return {@link Duration#ZERO} if allowed, otherwise how long until the window resets.
     */
    public Duration tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        Window w = windows.compute(key, (k, current) ->
            current == null || current.resetAt <= now ? new Window(now + window.toMillis(), 1) : current.increment()
        );
        return w.count <= limit ? Duration.ZERO : Duration.ofMillis(Math.max(1, w.resetAt - now));
    }

    /** Current hit count, without counting a new one. */
    public int count(String key) {
        Window w = windows.get(key);
        return w == null || w.resetAt <= clock.millis() ? 0 : w.count;
    }

    public Duration retryAfter(String key) {
        Window w = windows.get(key);
        long now = clock.millis();
        return w == null || w.resetAt <= now ? Duration.ZERO : Duration.ofMillis(w.resetAt - now);
    }

    public void reset(String key) {
        windows.remove(key);
    }

    /** Drops expired windows so memory does not grow with every IP ever seen. */
    @Scheduled(fixedDelay = 60_000)
    public void evictExpired() {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> e.getValue().resetAt <= now);
    }

    private record Window(long resetAt, int count) {
        Window increment() {
            return new Window(resetAt, count + 1);
        }
    }
}
