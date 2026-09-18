package com.claseya.oauth.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Validates a Google ID token and returns the claims ClaseYa trusts.
 *
 * <p>The injected {@link JwtDecoder} validates signature/expiry (and issuer when
 * configured via {@code withIssuerLocation}). This class additionally enforces
 * issuer, audience, expiration and {@code email_verified}.
 */
@Component
public class GoogleIdTokenVerifier {

    private final JwtDecoder jwtDecoder;
    private final String issuer;
    private final String clientId;

    public GoogleIdTokenVerifier(JwtDecoder jwtDecoder,
                                 @Value("${oauth.google.issuer}") String issuer,
                                 @Value("${oauth.google.client-id}") String clientId) {
        this.jwtDecoder = jwtDecoder;
        this.issuer = issuer;
        this.clientId = clientId;
    }

    public GoogleClaims verify(String idToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadRequestException("Invalid Google token");
        }

        if (issuer == null || !issuer.equals(jwt.getClaimAsString("iss"))) {
            throw new BadRequestException("Invalid Google token issuer");
        }
        Object audience = jwt.getClaim("aud");
        if (clientId == null || !(audience instanceof java.util.Collection<?> audList)
                || !audList.contains(clientId)) {
            throw new BadRequestException("Invalid Google token audience");
        }
        Instant expiresAt = epochSeconds(jwt.getClaim("exp"));
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new BadRequestException("Google token expired");
        }

        String sub = jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaimAsString("sub");
        String email = jwt.getClaimAsString("email");
        if (sub == null || email == null) {
            throw new BadRequestException("Google token missing subject or email");
        }
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new InvalidCredentialsException();
        }
        return new GoogleClaims(sub, email, jwt.getClaimAsString("name"));
    }

    private static Instant epochSeconds(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return null;
    }

    public record GoogleClaims(String sub, String email, String name) {
    }
}
