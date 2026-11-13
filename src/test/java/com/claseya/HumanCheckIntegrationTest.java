package com.claseya;

import com.claseya.auth.captcha.CaptchaVerifier;
import com.claseya.security.AuthCookieFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.auth.captcha.enabled=true")
class HumanCheckIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private CaptchaVerifier captchaVerifier;

    @Test
    void statusRequiresVerificationWhenCaptchaIsEnabled() throws Exception {
        mockMvc.perform(get("/api/human/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void verifySetsTheHumanCookieAndUnlocksTheStatus() throws Exception {
        Cookie human = mockMvc.perform(post("/api/human/verify")
                        .contentType("application/json;charset=UTF-8")
                        .content("{\"token\":\"turnstile-token\"}"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().httpOnly(AuthCookieFactory.HUMAN_COOKIE, true))
                .andReturn()
                .getResponse()
                .getCookie(AuthCookieFactory.HUMAN_COOKIE);

        verify(captchaVerifier).verify("turnstile-token");
        mockMvc.perform(get("/api/human/status").cookie(human))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/human/verify")
                        .contentType("application/json;charset=UTF-8")
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
