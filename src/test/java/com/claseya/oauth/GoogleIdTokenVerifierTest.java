package com.claseya.oauth;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.InvalidCredentialsException;
import com.claseya.oauth.service.GoogleIdTokenVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTest {

    private static final String ISSUER = "https://accounts.google.com";
    private static final String AUDIENCE = "claseya-web";

    private final KeyPair keys = keyPair();
    private final GoogleIdTokenVerifier verifier =
            new GoogleIdTokenVerifier(decoder(keys), ISSUER, AUDIENCE);

    private String token(Instant issuedAt, Instant expiresAt, String subject, String email,
                         Boolean emailVerified, String issuer, String audience) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", "Jane Doe")
                .build();
        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource(keys));
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String validToken(String email, String subject) {
        return token(Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES),
                subject, email, true, ISSUER, AUDIENCE);
    }

    @Test
    void validToken_returnsClaims() {
        GoogleIdTokenVerifier.GoogleClaims claims = verifier.verify(validToken("jane@example.com", "sub-123"));
        assertThat(claims.sub()).isEqualTo("sub-123");
        assertThat(claims.email()).isEqualTo("jane@example.com");
        assertThat(claims.name()).isEqualTo("Jane Doe");
    }

    @Test
    void wrongIssuer_throwsBadRequest() {
        String bad = token(Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES),
                "s", "a@example.com", true, "https://evil.example.com", AUDIENCE);
        assertThatThrownBy(() -> verifier.verify(bad)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void wrongAudience_throwsBadRequest() {
        String bad = token(Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES),
                "s", "a@example.com", true, ISSUER, "other-client");
        assertThatThrownBy(() -> verifier.verify(bad)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void expiredToken_throwsBadRequest() {
        String expired = token(Instant.now().minus(10, ChronoUnit.MINUTES),
                Instant.now().minus(5, ChronoUnit.MINUTES), "s", "a@example.com", true, ISSUER, AUDIENCE);
        assertThatThrownBy(() -> verifier.verify(expired)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void unverifiedEmail_throwsInvalidCredentials() {
        String unverified = token(Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES),
                "s", "a@example.com", false, ISSUER, AUDIENCE);
        assertThatThrownBy(() -> verifier.verify(unverified)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void tamperedToken_throwsBadRequest() {
        String good = validToken("a@example.com", "s");
        String tampered = good.substring(0, good.length() - 4) + "AAAA";
        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(BadRequestException.class);
    }

    // ------------------------------------------------------------------ infra

    private static JwtDecoder decoder(KeyPair keys) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    }

    private static JWKSource<SecurityContext> jwkSource(KeyPair keys) {
        RSAKey rsa = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate()).keyID("test").build();
        return new ImmutableJWKSet<>(new JWKSet(rsa));
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
