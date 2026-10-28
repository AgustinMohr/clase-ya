package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.security.AppUserDetails;
import com.claseya.security.JwtService;
import com.claseya.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-002: the browser session is a cookie, so unsafe requests require a CSRF token. This test
 * runs with CSRF enabled (the rest of the suite disables it) and exercises the complete flow.
 */
@SpringBootTest(properties = {
        "app.demo-seed.enabled=false",
        "security.rate-limit.enabled=false",
        "security.csrf.enabled=true",
        "auth.cookie.secure=false"
})
@AutoConfigureMockMvc
@Transactional
class CsrfIntegrationTest extends AbstractPostgresTest {

    private static final String EMAIL = "csrf@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);
    }

    @Test
    void loginCookieSessionRoundTripAndLogout() throws Exception {
        CsrfPair csrf = csrf();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("CLASEYA_SESSION=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andReturn();

        Cookie session = cookie(login, "CLASEYA_SESSION");

        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.email").doesNotExist());

        MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(session, csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(cookie(logout, "CLASEYA_SESSION").getMaxAge()).isZero();
    }

    @Test
    void unsafeRequestWithoutCsrfToken_isRejected() throws Exception {
        CsrfPair csrf = csrf();
        Cookie session = login(csrf);

        mockMvc.perform(post("/api/auth/logout").cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void bearerRequest_isExemptFromCsrf() throws Exception {
        String bearer = "Bearer " + jwtService.generateToken(new AppUserDetails(user));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    void csrfEndpointKeepsTheCookieHttpOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(cookie(result, "XSRF-TOKEN").isHttpOnly()).isTrue();
    }

    private CsrfPair csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CsrfPair(body.get("token").asText(), cookie(result, "XSRF-TOKEN"));
    }

    private Cookie login(CsrfPair csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "CLASEYA_SESSION");
    }

    private Cookie cookie(MvcResult result, String name) {
        Cookie[] cookies = result.getResponse().getCookies();
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing cookie " + name));
    }

    private String loginBody() {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD);
    }

    private record CsrfPair(String token, Cookie cookie) {
    }
}
