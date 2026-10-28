package com.claseya.security.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import org.springframework.stereotype.Service;

/**
 * Applies token buckets keyed by {@code scope + hash(identifier)}. No raw IP/email is stored.
 */
@Service
public class RateLimitService {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long MAX_RETRY_AFTER_SECONDS = 3600;

    private final InMemoryRateLimitStore store;
    private final RateLimitKeyHasher hasher;

    public RateLimitService(InMemoryRateLimitStore store, RateLimitKeyHasher hasher) {
        this.store = store;
        this.hasher = hasher;
    }

    public RateLimitDecision consume(RateLimitScope scope, String identifier, RateLimitPolicy policy) {
        ConsumptionProbe probe = store.bucket(key(scope, identifier), policy)
                .tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return RateLimitDecision.allow();
        }
        return RateLimitDecision.deny(retryAfterSeconds(probe.getNanosToWaitForRefill()));
    }

    public boolean isBlocked(RateLimitScope scope, String identifier, RateLimitPolicy policy) {
        return store.bucket(key(scope, identifier), policy).getAvailableTokens() <= 0;
    }

    public long retryAfterSeconds(RateLimitScope scope, String identifier, RateLimitPolicy policy) {
        EstimationProbe probe = store.bucket(key(scope, identifier), policy)
                .estimateAbilityToConsume(1);
        if (probe.canBeConsumed()) {
            return 1;
        }
        return retryAfterSeconds(probe.getNanosToWaitForRefill());
    }

    public void reset(RateLimitScope scope, String identifier) {
        store.reset(key(scope, identifier));
    }

    /** Non-reversible, truncated identifier for logs; never the raw IP/email. */
    public String fingerprint(RateLimitScope scope, String identifier) {
        return hasher.hash(identifier).substring(0, 12);
    }

    private String key(RateLimitScope scope, String identifier) {
        String value = identifier == null ? "" : identifier;
        return scope.name() + "|" + hasher.hash(value);
    }

    static long retryAfterSeconds(long nanos) {
        if (nanos <= 0) {
            return 1;
        }
        long seconds = (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
        return Math.max(1, Math.min(MAX_RETRY_AFTER_SECONDS, seconds));
    }
}

