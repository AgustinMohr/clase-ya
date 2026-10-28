package com.claseya.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void resolve_usesRemoteAddressWhenForwardedHeadersAreNotTrusted() {
        RateLimitProperties properties = new RateLimitProperties();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertThat(new ClientIpResolver(properties).resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void resolve_usesFirstXForwardedForValueWhenTrusted() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustForwardedHeaders(true);
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

        assertThat(new ClientIpResolver(properties).resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void resolve_stripsPortFromForwardedIpv4() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustForwardedHeaders(true);
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7:5555");

        assertThat(new ClientIpResolver(properties).resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void resolve_parsesForwardedHeaderWithIpv6() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustForwardedHeaders(true);
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("Forwarded", "for=\"[2001:db8::1]:1234\";proto=https");

        assertThat(new ClientIpResolver(properties).resolve(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void resolve_fallsBackToRemoteAddressWhenTheHeaderIsInvalid() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setTrustForwardedHeaders(true);
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "not-an-ip");

        assertThat(new ClientIpResolver(properties).resolve(request)).isEqualTo("10.0.0.1");
    }
}
