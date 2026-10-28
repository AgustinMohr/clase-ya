package com.claseya.security.ratelimit;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.security.AppUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void login_overIpLimit_returns429WithRetryAfter() throws Exception {
        RateLimitFilter filter = filter(properties(true, 1, 10));

        MockHttpServletResponse first = new MockHttpServletResponse();
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"), first, firstChain);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(firstChain.getRequest()).isNotNull();

        MockHttpServletResponse second = new MockHttpServletResponse();
        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"), second, secondChain);

        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isNotBlank();
        assertThat(second.getContentAsString())
                .contains("\"status\":429")
                .contains("Too many requests. Try again later.");
        assertThat(secondChain.getRequest()).isNull();
    }

    @Test
    void differentIpIsNotAffectedByAnotherIpBucket() throws Exception {
        RateLimitFilter filter = filter(properties(true, 1, 10));

        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"),
                new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse other = new MockHttpServletResponse();
        MockFilterChain otherChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.8"), other, otherChain);

        assertThat(other.getStatus()).isEqualTo(200);
        assertThat(otherChain.getRequest()).isNotNull();
    }

    @Test
    void getRequestsAreNotLimited() throws Exception {
        RateLimitFilter filter = filter(properties(true, 1, 1));

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request("GET", "/api/auth/login", "203.0.113.7"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void disabledLimiterPassesEveryRequest() throws Exception {
        RateLimitFilter filter = filter(properties(false, 1, 1));

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void userScopedContact_isLimitedByAuthenticatedUser() throws Exception {
        RateLimitFilter filter = filter(properties(true, 10, 1));

        AppUserDetails ana = principal("ana@example.com");
        authenticate(ana);
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/conversations", "203.0.113.7"),
                new MockHttpServletResponse(), firstChain);
        assertThat(firstChain.getRequest()).isNotNull();

        MockHttpServletResponse second = new MockHttpServletResponse();
        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/conversations", "203.0.113.7"), second, secondChain);
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(secondChain.getRequest()).isNull();

        authenticate(principal("bea@example.com"));
        MockHttpServletResponse other = new MockHttpServletResponse();
        MockFilterChain otherChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/conversations", "203.0.113.7"), other, otherChain);
        assertThat(other.getStatus()).isEqualTo(200);
        assertThat(otherChain.getRequest()).isNotNull();
    }

    @Test
    void messagePath_isUserScopedAndLimited() throws Exception {
        RateLimitProperties properties = properties(true, 10, 10);
        properties.getMessageUser().setLimit(1);
        properties.getMessageUser().setWindow(Duration.ofMinutes(1));
        RateLimitFilter filter = filter(properties);
        authenticate(principal("ana@example.com"));

        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/conversations/abc/messages", "203.0.113.7"),
                new MockHttpServletResponse(), firstChain);
        assertThat(firstChain.getRequest()).isNotNull();

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/conversations/abc/messages", "203.0.113.7"),
                second, new MockFilterChain());
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void uploadPath_isUserScopedAndLimited() throws Exception {
        RateLimitProperties properties = properties(true, 10, 10);
        properties.getUploadUser().setLimit(1);
        properties.getUploadUser().setWindow(Duration.ofMinutes(1));
        RateLimitFilter filter = filter(properties);
        authenticate(principal("ana@example.com"));

        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/teachers/me/education/abc/documents", "203.0.113.7"),
                new MockHttpServletResponse(), firstChain);
        assertThat(firstChain.getRequest()).isNotNull();

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/teachers/me/education/abc/documents", "203.0.113.7"),
                second, new MockFilterChain());
        assertThat(second.getStatus()).isEqualTo(429);
    }

    @Test
    void evaluationFailure_failsOpen() throws Exception {
        RateLimitProperties properties = properties(true, 1, 1);
        InMemoryRateLimitStore failingStore =
                new InMemoryRateLimitStore(properties, new MutableTimeMeter()) {
                    @Override
                    public Bucket bucket(String key, RateLimitPolicy policy) {
                        throw new IllegalStateException("store unavailable");
                    }
                };
        RateLimitService failingService = new RateLimitService(
                failingStore, new RateLimitKeyHasher(new byte[]{1, 2, 3}));
        RateLimitFilter filter = new RateLimitFilter(properties, failingService,
                new ClientIpResolver(properties), objectMapper);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/auth/login", "203.0.113.7"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    private RateLimitFilter filter(RateLimitProperties properties) {
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(properties, new MutableTimeMeter());
        RateLimitService service = new RateLimitService(store, new RateLimitKeyHasher(new byte[]{1, 2, 3}));
        return new RateLimitFilter(properties, service, new ClientIpResolver(properties), objectMapper);
    }

    private RateLimitProperties properties(boolean enabled, int loginLimit, int contactLimit) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(enabled);
        properties.getLoginIp().setLimit(loginLimit);
        properties.getLoginIp().setWindow(Duration.ofMinutes(1));
        properties.getContactUser().setLimit(contactLimit);
        properties.getContactUser().setWindow(Duration.ofHours(1));
        return properties;
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private AppUserDetails principal(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        return new AppUserDetails(user);
    }

    private void authenticate(AppUserDetails principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
