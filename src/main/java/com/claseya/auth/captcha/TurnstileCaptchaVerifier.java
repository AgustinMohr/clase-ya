package com.claseya.auth.captcha;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cloudflare Turnstile verification (SEC-04). The browser sends the widget token on registration;
 * the backend validates it server-side against Turnstile. Disabled by default for local/test; in
 * production set CAPTCHA_ENABLED=true and TURNSTILE_SECRET.
 *
 * <p>Fail-closed: if the provider cannot be reached, registration answers 503 instead of silently
 * skipping the challenge.
 */
@Component
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final String secret;

    public TurnstileCaptchaVerifier(RestClient.Builder builder,
                                    @Value("${app.auth.captcha.enabled:false}") boolean enabled,
                                    @Value("${app.auth.captcha.secret:}") String secret,
                                    @Value("${app.auth.captcha.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
                                    String verifyUrl) {
        if (enabled && (secret == null || secret.isBlank())) {
            throw new IllegalStateException("TURNSTILE_SECRET is required when CAPTCHA_ENABLED=true");
        }
        this.restClient = builder.baseUrl(verifyUrl).build();
        this.enabled = enabled;
        this.secret = secret;
    }

    @Override
    public void verify(String token) {
        if (!enabled) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Captcha verification is required");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);

        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(java.util.Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new BadRequestException("Captcha verification failed");
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Turnstile verification is unavailable", ex);
            throw new ServiceUnavailableException("Captcha verification is unavailable");
        }
    }
}

