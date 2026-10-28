package com.claseya.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPolicyTest {

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> new RateLimitPolicy(0, java.time.Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThatThrownBy(() -> new RateLimitPolicy(1, java.time.Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitPolicy(1, java.time.Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsBandwidthWithConfiguredCapacity() {
        RateLimitPolicy policy = new RateLimitPolicy(7, java.time.Duration.ofMinutes(1));

        assertThat(policy.toBandwidth().getCapacity()).isEqualTo(7);
    }
}
