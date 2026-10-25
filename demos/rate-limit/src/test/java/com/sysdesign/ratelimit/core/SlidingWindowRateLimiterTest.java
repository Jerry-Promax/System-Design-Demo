package com.sysdesign.ratelimit.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sysdesign.ratelimit.config.RateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void allowsRequestsUpToTheLimitAndRejectsTheNextOne() {
        SlidingWindowRateLimiter limiter = limiter(3, Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();

        SlidingWindowRateLimiter.Decision rejected = limiter.tryAcquire("client-a");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(10);
    }

    @Test
    void recoversWhenTheOldestRequestLeavesTheWindow() {
        SlidingWindowRateLimiter limiter = limiter(2, Duration.ofSeconds(10));

        limiter.tryAcquire("client-a");
        clock.advance(Duration.ofSeconds(4));
        limiter.tryAcquire("client-a");

        clock.advance(Duration.ofSeconds(5));
        SlidingWindowRateLimiter.Decision rejected = limiter.tryAcquire("client-a");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
    }

    @Test
    void isolatesDifferentClients() {
        SlidingWindowRateLimiter limiter = limiter(1, Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("client-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("client-a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("client-b").allowed()).isTrue();
    }

    @Test
    void concurrentRequestsCannotExceedTheLimit() throws Exception {
        SlidingWindowRateLimiter limiter = limiter(5, Duration.ofSeconds(10));

        List<Callable<Boolean>> requests = IntStream.range(0, 40)
                .mapToObj(ignored -> (Callable<Boolean>) () -> limiter.tryAcquire("shared-client").allowed())
                .toList();

        long allowed;
        try (var executor = Executors.newFixedThreadPool(8)) {
            allowed = executor.invokeAll(requests).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .count();
        }

        assertThat(allowed).isEqualTo(5);
    }

    private SlidingWindowRateLimiter limiter(int maxRequests, Duration window) {
        return new SlidingWindowRateLimiter(new RateLimitProperties(maxRequests, window), clock);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

