package com.claseya.oauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * OIDC JWT decoder for Google ID tokens. Uses {@code withIssuerLocation}, which
 * lazily resolves Google's JWKS and validates signature/issuer/expiry on use.
 * Tests provide a {@code @Primary} decoder backed by a local key.
 */
@Configuration
public class GoogleOAuthConfig {

    @Bean
    public JwtDecoder googleJwtDecoder(@Value("${oauth.google.issuer}") String issuer) {
        return NimbusJwtDecoder.withIssuerLocation(issuer).build();
    }
}
