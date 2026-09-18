package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GoogleAuthIntegrationTest extends AbstractWebIntegrationTest {

    private static final String ISSUER = "https://accounts.google.com";
    private static final String AUDIENCE = "test-aud";

    @DynamicPropertySource
    static void googleProperties(DynamicPropertyRegistry registry) {
        registry.add("oauth.google.issuer", () -> ISSUER);
        registry.add("oauth.google.client-id", () -> AUDIENCE);
    }

    @TestConfiguration
    static class GoogleTestConfig {
        @Bean
        @Primary
        JwtDecoder testGoogleJwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys().getPublic()).build();
        }
    }

    @Test
    void newUserViaGoogle_createsActiveAccountAndReturnsToken() throws Exception {
        JsonNode response = googlePost(body("jane@gmail.com", "sub-1", "STUDENT", null, null, true), 200);
        assertThat(response.get("accessToken").asText()).isNotBlank();
        assertThat(response.get("tokenType").asText()).isEqualTo("Bearer");

        User user = userRepository.findByEmail("jane@gmail.com").orElseThrow();
        assertThat(user.getGoogleSub()).isEqualTo("sub-1");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void sameGoogleAccount_logsInWithoutDuplicating() throws Exception {
        googlePost(body("jane@gmail.com", "sub-1", "STUDENT", null, null, true), 200);
        googlePost(body("jane@gmail.com", "sub-1", "TEACHER", null, null, true), 200);

        assertThat(userRepository.findAll().stream()
                .filter(u -> "sub-1".equals(u.getGoogleSub())).count()).isEqualTo(1);
        assertThat(userRepository.findByEmail("jane@gmail.com").orElseThrow().getRole())
                .isEqualTo(UserRole.STUDENT);
    }

    @Test
    void existingPasswordAccount_isLinkedByVerifiedEmail() throws Exception {
        User existing = createUser("jane@gmail.com", UserRole.STUDENT, UserStatus.ACTIVE);

        googlePost(body("jane@gmail.com", "sub-2", null, null, null, true), 200);

        assertThat(userRepository.findById(existing.getId()).orElseThrow().getGoogleSub()).isEqualTo("sub-2");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void disabledAccount_isNeverActivatedViaGoogle() throws Exception {
        User pending = createUser("p@gmail.com", UserRole.STUDENT, UserStatus.PENDING);
        User inactive = createUser("i@gmail.com", UserRole.STUDENT, UserStatus.INACTIVE);

        googlePost(body("p@gmail.com", "sub-p", null, null, null, true), 401);
        googlePost(body("i@gmail.com", "sub-i", null, null, null, true), 401);

        assertThat(userRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING);
        assertThat(userRepository.findById(inactive.getId()).orElseThrow().getGoogleSub()).isNull();
    }

    @Test
    void invalidTokens_areRejected() throws Exception {
        googlePost(raw(token("jane@gmail.com", "s", null, null, true, false, otherKeys())), 400); // bad signature
        googlePost(body("jane@gmail.com", "s", "STUDENT", "other-issuer", null, true), 400);
        googlePost(body("jane@gmail.com", "s", "STUDENT", null, "other-aud", true), 400);
        googlePost(body("jane@gmail.com", "s", "STUDENT", null, null, true, true), 400);       // expired
        googlePost(body("jane@gmail.com", "s", "STUDENT", null, null, false), 401);           // unverified email
    }

    @Test
    void adminRole_andMissingRole_areRejected() throws Exception {
        googlePost(body("admin@gmail.com", "sub-a", "ADMIN", null, null, true), 403);
        googlePost(raw(token("no-role@gmail.com", "sub-b", null, null, true, false, keys())), 400);
    }

    @Test
    void googleAccountLinkedToAnotherEmail_conflicts() throws Exception {
        googlePost(body("a@gmail.com", "sub-x", "STUDENT", null, null, true), 200);
        googlePost(body("b@gmail.com", "sub-x", "STUDENT", null, null, true), 409);
    }

    // ------------------------------------------------------------------ helpers

    private String body(String email, String sub, String role, String issuerOverride,
                        String audienceOverride, Boolean emailVerified) {
        return body(email, sub, role, issuerOverride, audienceOverride, emailVerified, false);
    }

    private String body(String email, String sub, String role, String issuerOverride,
                        String audienceOverride, Boolean emailVerified, boolean expired) {
        String roleJson = role == null ? "" : "\"role\":\"%s\",".formatted(role);
        return "{%s\"idToken\":\"%s\"}".formatted(roleJson,
                token(email, sub, issuerOverride, audienceOverride, emailVerified, expired, keys()));
    }

    private String raw(String idToken) {
        return "{\"idToken\":\"%s\"}".formatted(idToken);
    }

    private String token(String email, String sub, String issuerOverride, String audienceOverride,
                         Boolean emailVerified, boolean expired, KeyPair keyPair) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuerOverride != null ? issuerOverride : ISSUER)
                .subject(sub)
                .audience(List.of(audienceOverride != null ? audienceOverride : AUDIENCE))
                .issuedAt(expired ? now.minus(10, ChronoUnit.MINUTES) : now)
                .expiresAt(expired ? now.minus(5, ChronoUnit.MINUTES) : now.plus(5, ChronoUnit.MINUTES))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", "Jane Doe")
                .build();
        return new NimbusJwtEncoder(jwkSource(keyPair))
                .encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private JsonNode googlePost(String jsonBody, int expected) throws Exception {
        String content = mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json;charset=UTF-8")
                        .content(jsonBody.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return toJson(content);
    }

    private static volatile KeyPair sharedKeys;

    private static KeyPair keys() {
        if (sharedKeys == null) {
            synchronized (GoogleAuthIntegrationTest.class) {
                if (sharedKeys == null) {
                    sharedKeys = generate();
                }
            }
        }
        return sharedKeys;
    }

    private static KeyPair otherKeys() {
        return generate();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static JWKSource<SecurityContext> jwkSource(KeyPair keys) {
        RSAKey rsa = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate()).keyID("test").build();
        return new ImmutableJWKSet<>(new JWKSet(rsa));
    }
}
