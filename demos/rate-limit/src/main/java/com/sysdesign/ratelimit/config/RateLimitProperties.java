package com.sysdesign.ratelimit.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(int maxRequests, Duration window) {

    public RateLimitProperties {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("maxRequests must be greater than zero");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be a positive duration");
        }
    }
}

