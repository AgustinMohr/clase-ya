package com.claseya.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

/**
 * Fails fast at startup when the JWT signing secret is unsafe outside local development.
 *
 * <p>{@code application.yml} ships a public development default so a fresh clone can run with the
 * {@code local} profile and no extra configuration. In any other profile that default — or any
 * secret shorter than the 256 bits HS256 requires — would let anyone forge tokens and impersonate
 * any account (including ADMIN). Rather than boot silently insecure, the context refuses to start.
 *
 * <p>It is a configuration validation, not a change to the JWT mechanism: with a valid secret the
 * behaviour is identical to before.
 */
@Component
public class JwtSecretValidator {

    /** Must match the {@code jwt.secret} default in {@code application.yml}. */
    static final String DEV_DEFAULT_SECRET =
            "dev-only-change-me-9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a";

    private static final String PROPERTY = "jwt.secret";
    private static final String DEV_PROFILE = "local";
    /** HS256 requires a key of at least 256 bits = 32 bytes. */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtSecretValidator(Environment environment) {
        validate(environment.getProperty(PROPERTY), Arrays.asList(environment.getActiveProfiles()));
    }

    /**
     * @throws IllegalStateException when the secret is blank, the development default, or shorter
     *                               than 32 bytes, and the {@code local} profile is not active.
     */
    static void validate(String secret, Collection<String> activeProfiles) {
        if (activeProfiles.contains(DEV_PROFILE)) {
            return; // Local development keeps the convenient default.
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret (JWT_SECRET) must be configured with a strong, unique value outside local development.");
        }
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "jwt.secret is still the public development default: set a strong, unique JWT_SECRET outside local development.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (HS256) outside local development.");
        }
    }
}
