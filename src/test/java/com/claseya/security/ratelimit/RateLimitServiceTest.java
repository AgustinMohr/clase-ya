package com.claseya.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    private final RateLimitService service;
    private final MutableTimeMeter clock = new MutableTimeMeter();

    RateLimitServiceTest() {
        RateLimitProperties properties = new RateLimitProperties();
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(properties, clock);
        this.service = new RateLimitService(store, new RateLimitKeyHasher(new byte[]{1, 2, 3}));
    }

    @Test
    void consume_allowsUpToTheLimitThenBlocks() {
        RateLimitPolicy policy = new RateLimitPolicy(2, Duration.ofMinutes(1));

        assertThat(service.consume(RateLimitScope.LOGIN_IP, "1.2.3.4", policy).allowed()).isTrue();
        assertThat(service.consume(RateLimitScope.LOGIN_IP, "1.2.3.4", policy).allowed()).isTrue();

        RateLimitDecision blocked = service.consume(RateLimitScope.LOGIN_IP, "1.2.3.4", policy);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void consume_allowsAgainAfterTheWindowRefills() {
        RateLimitPolicy policy = new RateLimitPolicy(2, Duration.ofMinutes(1));
        service.consume(RateLimitScope.LOGIN_IP, "1.2.3.4", policy);
        service.consume(RateLimitScope.LOGIN_IP, "1.2.3.4", policy);
        assertThat(service.consume(RateLimitScope.LOGIN_IP, "1.2.3.4", policy).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(service.consume(RateLimitScope.LOGIN_IP, "1.2.3.4", policy).allowed()).isTrue();
    }

    @Test
    void reset_clearsTheBucket() {
        RateLimitPolicy policy = new RateLimitPolicy(1, Duration.ofMinutes(1));
        service.consume(RateLimitScope.LOGIN_ACCOUNT, "ana@example.com", policy);
        assertThat(service.isBlocked(RateLimitScope.LOGIN_ACCOUNT, "ana@example.com", policy)).isTrue();

        service.reset(RateLimitScope.LOGIN_ACCOUNT, "ana@example.com");

        assertThat(service.isBlocked(RateLimitScope.LOGIN_ACCOUNT, "ana@example.com", policy)).isFalse();
    }

    @Test
    void fingerprint_isShortAndNeverTheRawIdentifier() {
        String fingerprint = service.fingerprint(RateLimitScope.LOGIN_ACCOUNT, "ana@example.com");

        assertThat(fingerprint).hasSize(12).doesNotContain("ana", "@", "example");
    }
}
