package com.claseya.security.ratelimit;

import io.github.bucket4j.Bandwidth;

import java.time.Duration;

/** A token-bucket limit: {@code limit} requests per {@code window}. */
public record RateLimitPolicy(int limit, Duration window) {

    public RateLimitPolicy {
        if (limit < 1) {
            throw new IllegalArgumentException("Rate limit must be at least 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit window must be greater than zero");
        }
    }

    public Bandwidth toBandwidth() {
        return Bandwidth.builder()
                .capacity(limit)
                .refillGreedy(limit, window)
                .build();
    }
}
