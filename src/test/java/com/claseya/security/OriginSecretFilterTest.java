package com.claseya.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** RT-08: direct hits to the backend (no Vercel secret) are rejected before rate limiting. */
class OriginSecretFilterTest {

    private static final String SECRET = "s3cr3t-value-for-tests";

    private MockHttpServletResponse run(String secretConfig, String path, String header) throws Exception {
        OriginSecretFilter filter = new OriginSecretFilter(secretConfig);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (header != null) {
            request.addHeader(OriginSecretFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        lastChain = mock(FilterChain.class);
        filter.doFilter(request, response, lastChain);
        return response;
    }

    private FilterChain lastChain;

    @Test
    void blankSecretDisablesTheCheck() throws Exception {
        MockHttpServletResponse response = run("", "/api/auth/login", null);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(lastChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingHeaderIsRejected() throws Exception {
        MockHttpServletResponse response = run(SECRET, "/api/auth/login", null);
        assertThat(response.getStatus()).isEqualTo(403);
        verify(lastChain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void wrongHeaderIsRejected() throws Exception {
        MockHttpServletResponse response = run(SECRET, "/api/auth/login", "other");
        assertThat(response.getStatus()).isEqualTo(403);
        verify(lastChain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void correctHeaderPasses() throws Exception {
        MockHttpServletResponse response = run(SECRET, "/api/auth/login", SECRET);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(lastChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void healthProbesAreExemptSoThePlatformCanCallThemDirectly() throws Exception {
        assertThat(run(SECRET, "/actuator/health", null).getStatus()).isEqualTo(200);
        assertThat(run(SECRET, "/actuator/health/liveness", null).getStatus()).isEqualTo(200);
        assertThat(run(SECRET, "/actuator/health/readiness", null).getStatus()).isEqualTo(200);
    }

    @Test
    void otherActuatorPathsAreNotExempt() throws Exception {
        assertThat(run(SECRET, "/actuator/metrics", null).getStatus()).isEqualTo(403);
        assertThat(run(SECRET, "/actuator/healthcheck", null).getStatus()).isEqualTo(403);
    }
}
