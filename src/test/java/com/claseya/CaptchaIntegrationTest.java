package com.claseya;

import com.claseya.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEC-04: the registration forwards and enforces the CAPTCHA token. */
@Transactional
class CaptchaIntegrationTest extends AbstractWebIntegrationTest {

    private String body(String email, String captchaToken) {
        return """
                {"email":"%s","password":"%s","role":"STUDENT","captchaToken":"%s"}
                """.formatted(email, PASSWORD, captchaToken);
    }

    @Test
    void register_forwardsTheCaptchaToken() throws Exception {
        String email = "captcha-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(body(email, "turnstile-token")))
                .andExpect(status().isAccepted());

        verify(captchaVerifier).verify("turnstile-token");
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void register_rejectsAnInvalidCaptchaWithoutCreatingTheAccount() throws Exception {
        String email = "captcha-bad-" + UUID.randomUUID() + "@example.com";
        doThrow(new BadRequestException("Captcha verification failed"))
                .when(captchaVerifier).verify(anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(body(email, "invalid-token")))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }
}
