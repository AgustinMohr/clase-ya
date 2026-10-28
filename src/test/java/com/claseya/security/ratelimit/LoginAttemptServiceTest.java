package com.claseya.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private final RateLimitService rateLimitService = new RateLimitService(
            new InMemoryRateLimitStore(new RateLimitProperties(), new MutableTimeMeter()),
            new RateLimitKeyHasher(new byte[]{1, 2, 3}));
    private final LoginAttemptService loginAttemptService =
            new LoginAttemptService(rateLimitService, new RateLimitProperties());

    @Test
    void fiveFailures_blockTheAccount() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("ana@example.com");
        }

        assertThat(loginAttemptService.isBlocked("ana@example.com")).isTrue();
        assertThat(loginAttemptService.blockedException("ana@example.com").getRetryAfterSeconds())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void reset_clearsTheAccountBlock() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("ana@example.com");
        }
        assertThat(loginAttemptService.isBlocked("ana@example.com")).isTrue();

        loginAttemptService.reset("ana@example.com");

        assertThat(loginAttemptService.isBlocked("ana@example.com")).isFalse();
    }

    @Test
    void failuresForOneEmailDoNotBlockAnother() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("ana@example.com");
        }

        assertThat(loginAttemptService.isBlocked("other@example.com")).isFalse();
    }

    @Test
    void unknownEmailIsThrottledTheSameWay() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("missing@example.com");
        }

        assertThat(loginAttemptService.isBlocked("missing@example.com")).isTrue();
    }

    @Test
    void disabledLimiter_neverBlocksOrCountsFailures() {
        RateLimitProperties disabled = new RateLimitProperties();
        disabled.setEnabled(false);
        RateLimitService service = new RateLimitService(
                new InMemoryRateLimitStore(disabled, new MutableTimeMeter()),
                new RateLimitKeyHasher(new byte[]{1, 2, 3}));
        LoginAttemptService attempts = new LoginAttemptService(service, disabled);

        for (int i = 0; i < 10; i++) {
            attempts.recordFailure("ana@example.com");
        }

        assertThat(attempts.isBlocked("ana@example.com")).isFalse();
    }
}
