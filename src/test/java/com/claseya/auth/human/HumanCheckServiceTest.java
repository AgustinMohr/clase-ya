package com.claseya.auth.human;

import com.claseya.auth.captcha.CaptchaVerifier;
import com.claseya.security.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HumanCheckServiceTest {

    private static final String SECRET =
            "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";

    private final JwtService jwtService = new JwtService(SECRET, 3600);

    @Test
    void skipsVerificationWhenCaptchaIsDisabled() {
        CaptchaVerifier captcha = mock(CaptchaVerifier.class);
        HumanCheckService service =
                new HumanCheckService(captcha, jwtService, false, Duration.ofHours(1));

        assertThat(service.isRequired()).isFalse();
        assertThat(service.isVerified(null)).isTrue();
    }

    @Test
    void requiredCheckRejectsMissingOrInvalidCookies() {
        HumanCheckService service = new HumanCheckService(
                mock(CaptchaVerifier.class), jwtService, true, Duration.ofHours(1));

        assertThat(service.isVerified(null)).isFalse();
        assertThat(service.isVerified("not-a-token")).isFalse();
    }

    @Test
    void verifyChecksTheCaptchaAndIssuesAValidCookieToken() {
        CaptchaVerifier captcha = mock(CaptchaVerifier.class);
        HumanCheckService service =
                new HumanCheckService(captcha, jwtService, true, Duration.ofHours(1));

        String token = service.verify("turnstile-token");

        verify(captcha).verify("turnstile-token");
        assertThat(service.isVerified(token)).isTrue();
    }
}
