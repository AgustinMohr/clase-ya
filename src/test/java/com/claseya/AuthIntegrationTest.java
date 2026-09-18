package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AuthIntegrationTest extends AbstractWebIntegrationTest {

    private String expiredToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String registerBody(String email, String password, String role) {
        return """
                {"email":"%s","password":"%s","role":"%s"}
                """.formatted(email, password, role);
    }

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    // --------------------------------------------------------------------- register

    @Test
    void register_valid_createsPendingUserWithHashedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody("student@example.com", PASSWORD, "STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.email").value("student@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        Optional<User> stored = userRepository.findByEmail("student@example.com");
        assertThat(stored).isPresent();
        assertThat(stored.get().getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.get().getPasswordHash())).isTrue();
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        createUser("dup@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody("dup@example.com", PASSWORD, "STUDENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody("not-an-email", PASSWORD, "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody("short@example.com", "short", "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidRole_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody("role@example.com", PASSWORD, "SUPERHERO")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_adminRole_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerBody("admin@example.com", PASSWORD, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------------- login

    @Test
    void login_valid_returnsTokenAndSetsLastLogin() throws Exception {
        User user = createUser("jane@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("jane@example.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        createUser("jane@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("jane@example.com", "WrongPassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_nonexistentUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("ghost@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_suspendedUser_returns401() throws Exception {
        createUser("s@example.com", UserRole.STUDENT, UserStatus.SUSPENDED);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("s@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_inactiveUser_returns401() throws Exception {
        createUser("i@example.com", UserRole.STUDENT, UserStatus.INACTIVE);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("i@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_pendingUser_returns401() throws Exception {
        createUser("p@example.com", UserRole.STUDENT, UserStatus.PENDING);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginBody("p@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------------- jwt access

    @Test
    void authenticated_withValidToken_returns200() throws Exception {
        User user = createUser("j@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/test/authenticated").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("j@example.com"));
    }

    @Test
    void authenticated_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/authenticated"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_withExpiredToken_returns401() throws Exception {
        User user = createUser("j@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/test/authenticated")
                        .header("Authorization", "Bearer " + expiredToken(user)))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------------- roles

    @Test
    void roleMatrix_student() throws Exception {
        User user = createUser("stu@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        String t = bearer(user);

        mockMvc.perform(get("/api/test/student").header("Authorization", t)).andExpect(status().isOk());
        mockMvc.perform(get("/api/test/teacher").header("Authorization", t)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/test/admin").header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void roleMatrix_teacher() throws Exception {
        User user = createUser("tea@example.com", UserRole.TEACHER, UserStatus.ACTIVE);
        String t = bearer(user);

        mockMvc.perform(get("/api/test/teacher").header("Authorization", t)).andExpect(status().isOk());
        mockMvc.perform(get("/api/test/admin").header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void roleMatrix_admin() throws Exception {
        User user = createUser("adm@example.com", UserRole.ADMIN, UserStatus.ACTIVE);
        String t = bearer(user);

        mockMvc.perform(get("/api/test/admin").header("Authorization", t)).andExpect(status().isOk());
    }

    @Test
    void publicEndpoint_isAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/test/public"))
                .andExpect(status().isOk());
    }

    @Test
    void suspendedUser_tokenIsRejectedEvenWhenSigned() throws Exception {
        User user = createUser("sus@example.com", UserRole.STUDENT, UserStatus.SUSPENDED);

        mockMvc.perform(get("/api/test/authenticated").header("Authorization", bearer(user)))
                .andExpect(status().isUnauthorized());
    }
}
