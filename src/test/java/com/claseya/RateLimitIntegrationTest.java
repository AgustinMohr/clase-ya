package com.claseya;

import com.claseya.auth.service.ActivationMailSender;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract for RATE-001 with low limits. Existing integration tests run with the limiter
 * disabled (see AbstractWebIntegrationTest); this class uses its own context with small windows.
 */
@SpringBootTest(properties = {
        "app.demo-seed.enabled=false",
        "security.rate-limit.enabled=true",
        "security.csrf.enabled=false",
        "security.rate-limit.login-ip.limit=2",
        "security.rate-limit.login-ip.window=PT1M",
        "security.rate-limit.login-account.limit=5",
        "security.rate-limit.login-account.window=PT15M",
        "security.rate-limit.register-ip.limit=2",
        "security.rate-limit.register-ip.window=PT1H"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTest extends AbstractPostgresTest {

    private static final String PASSWORD = "Password123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /** AUTH-001: registration sends an activation email; never contact SMTP in tests. */
    @MockitoBean private ActivationMailSender activationMailSender;

    @Test
    void login_fromTheSameIp_isLimitedToTheConfiguredRate() throws Exception {
        createActiveUser("rate-login-ip@example.com");
        String wrong = """
                {"email":"rate-login-ip@example.com","password":"wrong-password"}
                """;
        String correct = """
                {"email":"rate-login-ip@example.com","password":"%s"}
                """.formatted(PASSWORD);

        mockMvc.perform(json(post("/api/auth/login"), "10.10.0.1", wrong))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/auth/login"), "10.10.0.1", wrong))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/auth/login"), "10.10.0.1", wrong))
                .andExpect(status().is(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // A different IP is not affected, and a successful login resets the account counter.
        mockMvc.perform(json(post("/api/auth/login"), "10.10.0.2", correct))
                .andExpect(status().isOk());
    }

    @Test
    void accountLockout_appliesEvenFromAnotherIp() throws Exception {
        createActiveUser("rate-lockout@example.com");
        String wrong = """
                {"email":"rate-lockout@example.com","password":"wrong-password"}
                """;
        String correct = """
                {"email":"rate-lockout@example.com","password":"%s"}
                """.formatted(PASSWORD);

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(json(post("/api/auth/login"), "10.20.0." + i, wrong))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(json(post("/api/auth/login"), "10.20.0.99", correct))
                .andExpect(status().is(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void register_fromTheSameIp_isLimited() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String body = """
                    {"email":"rate-register-%d@example.com","password":"%s","role":"STUDENT"}
                    """.formatted(i, PASSWORD);
            mockMvc.perform(json(post("/api/auth/register"), "10.30.0.1", body))
                    .andExpect(status().isAccepted());
        }

        String third = """
                {"email":"rate-register-3@example.com","password":"%s","role":"STUDENT"}
                """.formatted(PASSWORD);
        mockMvc.perform(json(post("/api/auth/register"), "10.30.0.1", third))
                .andExpect(status().is(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void resendActivation_fromTheSameIp_isLimited() throws Exception {
        String body = """
                {"email":"rate-resend@example.com"}
                """;

        mockMvc.perform(json(post("/api/auth/resend-activation"), "10.50.0.1", body))
                .andExpect(status().isAccepted());
        mockMvc.perform(json(post("/api/auth/resend-activation"), "10.50.0.1", body))
                .andExpect(status().isAccepted());
        mockMvc.perform(json(post("/api/auth/resend-activation"), "10.50.0.1", body))
                .andExpect(status().is(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void publicReads_areNotLimited() throws Exception {

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/teachers").with(remoteAddr("10.40.0.1")))
                    .andExpect(status().isOk());
        }
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request,
                                               String ip,
                                               String body) {
        return request.with(remoteAddr(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private User createActiveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
