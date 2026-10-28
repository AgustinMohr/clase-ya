package com.claseya.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configurable limits for RATE-001. Defaults are safe for production; env vars can override
 * every value (see application.yml). Invalid values fail fast at startup.
 */
@ConfigurationProperties(prefix = "security.rate-limit")
@Validated
@Getter
@Setter
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean trustForwardedHeaders = false;

    @Min(1)
    private int maxEntries = 50_000;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration idleTtl = Duration.ofMinutes(30);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration cleanupInterval = Duration.ofMinutes(5);

    @Valid
    @NotNull
    private Policy loginIp = new Policy(30, Duration.ofMinutes(1));

    @Valid
    @NotNull
    private Policy loginAccount = new Policy(5, Duration.ofMinutes(15));

    @Valid
    @NotNull
    private Policy registerIp = new Policy(20, Duration.ofHours(1));

    @Valid
    @NotNull
    private Policy googleIp = new Policy(30, Duration.ofMinutes(1));

    @Valid
    @NotNull
    private Policy contactUser = new Policy(10, Duration.ofHours(1));

    @Valid
    @NotNull
    private Policy messageUser = new Policy(60, Duration.ofMinutes(1));

    @Valid
    @NotNull
    private Policy uploadUser = new Policy(20, Duration.ofHours(1));

    public Policy policyFor(RateLimitScope scope) {
        return switch (scope) {
            case LOGIN_IP -> loginIp;
            case LOGIN_ACCOUNT -> loginAccount;
            case REGISTER_IP -> registerIp;
            case GOOGLE_IP -> googleIp;
            case CONTACT_USER -> contactUser;
            case MESSAGE_USER -> messageUser;
            case UPLOAD_USER -> uploadUser;
        };
    }

    @Getter
    @Setter
    public static class Policy {

        @Min(1)
        private int limit;

        @NotNull
        @DurationMin(seconds = 1)
        private Duration window;

        public Policy() {
        }

        public Policy(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }

        public RateLimitPolicy toRateLimitPolicy() {
            return new RateLimitPolicy(limit, window);
        }
    }
}
