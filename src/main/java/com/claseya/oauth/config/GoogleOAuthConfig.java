package com.claseya.oauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * OIDC JWT decoder for Google ID tokens. Uses {@code withIssuerLocation}, which
 * lazily resolves Google's JWKS and validates signature/issuer/expiry on use.
 * Tests provide a {@code @Primary} decoder backed by a local key.
 */
@Configuration
public class GoogleOAuthConfig {

    @Bean
    public JwtDecoder googleJwtDecoder(@Value("${oauth.google.issuer}") String issuer,
                                       @Value("${oauth.google.api-call-timeout:PT5S}") Duration timeout) {
        // Bounded timeouts: fetching Google's keys must not park the login request (B-06).
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = (int) Math.min(Integer.MAX_VALUE, timeout.toMillis());
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);
        return NimbusJwtDecoder.withIssuerLocation(issuer)
                .restOperations(new RestTemplate(factory))
                .build();
    }
}
