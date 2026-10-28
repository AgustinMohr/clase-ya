package com.claseya.security.ratelimit;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hashes rate-limit identifiers (IPs, emails) with a per-process random salt. The raw value is
 * never stored or logged; the salt is not persisted, so keys cannot be correlated across restarts.
 */
@Component
public class RateLimitKeyHasher {

    private final byte[] salt;

    public RateLimitKeyHasher() {
        this(randomSalt());
    }

    RateLimitKeyHasher(byte[] salt) {
        this.salt = salt.clone();
    }

    public String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }
}
