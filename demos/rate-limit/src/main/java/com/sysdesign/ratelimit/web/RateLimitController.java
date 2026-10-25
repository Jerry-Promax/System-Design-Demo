package com.sysdesign.ratelimit.web;

import com.sysdesign.ratelimit.core.SlidingWindowRateLimiter;
import com.sysdesign.ratelimit.core.SlidingWindowRateLimiter.Decision;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RateLimitController {

    private final SlidingWindowRateLimiter rateLimiter;

    public RateLimitController(SlidingWindowRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/reports")
    public ResponseEntity<ReportsResponse> reports(
            @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId) {
        Decision decision = rateLimiter.tryAcquire(clientId);
        HttpHeaders headers = rateLimitHeaders(decision);

        if (!decision.allowed()) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(new ReportsResponse(
                            false,
                            clientId,
                            List.of(),
                            "Rate limit exceeded. Retry after " + decision.retryAfterSeconds() + " seconds.",
                            Instant.now()));
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(new ReportsResponse(
                        true,
                        clientId,
                        List.of("daily-active-users", "conversion-rate"),
                        "Request accepted",
                        Instant.now()));
    }

    private HttpHeaders rateLimitHeaders(Decision decision) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", Integer.toString(decision.limit()));
        headers.set("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        return headers;
    }

    public record ReportsResponse(
            boolean allowed,
            String clientId,
            List<String> reports,
            String message,
            Instant servedAt) {
    }
}

