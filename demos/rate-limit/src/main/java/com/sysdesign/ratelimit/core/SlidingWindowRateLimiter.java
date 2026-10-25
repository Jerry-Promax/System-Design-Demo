package com.sysdesign.ratelimit.core;

import com.sysdesign.ratelimit.config.RateLimitProperties;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class SlidingWindowRateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentMap<String, ClientWindow> windows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(RateLimitProperties properties, Clock clock) {
        this.maxRequests = properties.maxRequests();
        this.windowMillis = properties.window().toMillis();
        this.clock = clock;
    }

    public Decision tryAcquire(String clientId) {
        Objects.requireNonNull(clientId, "clientId must not be null");

        long now = clock.millis();
        ClientWindow clientWindow = windows.computeIfAbsent(clientId, ignored -> new ClientWindow());

        synchronized (clientWindow) {
            evictExpired(clientWindow.timestamps, now);

            if (clientWindow.timestamps.size() >= maxRequests) {
                long oldestRequest = clientWindow.timestamps.element();
                long waitMillis = windowMillis - (now - oldestRequest);
                long retryAfterSeconds = Math.max(1, (long) Math.ceil(waitMillis / 1000.0));
                return new Decision(false, maxRequests, 0, retryAfterSeconds);
            }

            clientWindow.timestamps.addLast(now);
            int remaining = maxRequests - clientWindow.timestamps.size();
            return new Decision(true, maxRequests, remaining, 0);
        }
    }

    private void evictExpired(Deque<Long> timestamps, long now) {
        long cutoff = now - windowMillis;
        while (!timestamps.isEmpty() && timestamps.element() <= cutoff) {
            timestamps.removeFirst();
        }
    }

    private static final class ClientWindow {
        private final Deque<Long> timestamps = new ArrayDeque<>();
    }

    public record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
    }
}

