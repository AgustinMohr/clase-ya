package com.claseya.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    private static final RateLimitPolicy POLICY = new RateLimitPolicy(1, Duration.ofMinutes(1));

    @Test
    void keepsTheConfiguredMaximumNumberOfEntries() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxEntries(2);
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(properties, new MutableTimeMeter());

        store.bucket("a", POLICY);
        store.bucket("b", POLICY);
        store.bucket("c", POLICY);

        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void cleanupExpired_dropsIdleEntries() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setIdleTtl(Duration.ofMinutes(30));
        MutableTimeMeter clock = new MutableTimeMeter();
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(properties, clock);

        store.bucket("a", POLICY);
        clock.advance(Duration.ofMinutes(31));
        store.cleanupExpired();

        assertThat(store.size()).isZero();
    }

    @Test
    void cleanupExpired_keepsRecentlyAccessedEntries() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setIdleTtl(Duration.ofMinutes(30));
        MutableTimeMeter clock = new MutableTimeMeter();
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(properties, clock);

        store.bucket("a", POLICY);
        clock.advance(Duration.ofMinutes(20));
        store.bucket("a", POLICY);
        clock.advance(Duration.ofMinutes(20));
        store.cleanupExpired();

        assertThat(store.size()).isEqualTo(1);
    }
}
