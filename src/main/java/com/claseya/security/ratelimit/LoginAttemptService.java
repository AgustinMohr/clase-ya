package com.claseya.security.ratelimit;

import com.claseya.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Per-account login throttling (RATE-001, RF-2). It counts failures for every normalized email,
 * existing or not, so a 429 never reveals whether an account exists. A successful login resets it.
 */
@Service
public class LoginAttemptService {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public LoginAttemptService(RateLimitService rateLimitService, RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    public boolean isBlocked(String email) {
        if (!properties.isEnabled()) {
            return false;
        }
        return rateLimitService.isBlocked(RateLimitScope.LOGIN_ACCOUNT, normalize(email), policy());
    }

    public void recordFailure(String email) {
        if (!properties.isEnabled()) {
            return;
        }
        rateLimitService.consume(RateLimitScope.LOGIN_ACCOUNT, normalize(email), policy());
    }

    public void reset(String email) {
        if (!properties.isEnabled()) {
            return;
        }
        rateLimitService.reset(RateLimitScope.LOGIN_ACCOUNT, normalize(email));
    }

    public RateLimitExceededException blockedException(String email) {
        long retryAfter = Math.max(1,
                rateLimitService.retryAfterSeconds(RateLimitScope.LOGIN_ACCOUNT, normalize(email), policy()));
        return new RateLimitExceededException(retryAfter);
    }

    private RateLimitPolicy policy() {
        return properties.policyFor(RateLimitScope.LOGIN_ACCOUNT).toRateLimitPolicy();
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
