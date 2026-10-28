package com.claseya.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and parses HS256-signed JWTs.
 *
 * <p>The signing secret and token lifetime come from external configuration
 * ({@code jwt.secret} / {@code jwt.expiration}), never from code.
 *
 * <p>The token is intentionally free of personal data: only the user id ({@code sub}) and a
 * unique {@code jti} travel inside. Role and display data are resolved from the database on
 * every request ({@code /api/auth/me} for the UI). The token lives in an HttpOnly cookie and is
 * never exposed to JavaScript (SEC-002 / ADR-020).
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(AppUserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates the token (signature + expiration). Throws
     * {@link io.jsonwebtoken.JwtException} (or {@link IllegalArgumentException})
     * when the token is invalid or expired.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return parseToken(token).getSubject();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
