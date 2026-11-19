package com.claseya.auth.captcha;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client used by {@link TurnstileCaptchaVerifier}.
 *
 * <p>Bounded connect/read timeouts: the CAPTCHA check runs inside the registration request (and
 * its transaction), so a slow or unreachable provider must fail fast with a 503 instead of
 * parking a request thread and a pooled database connection (B-06). Keeping the timeout here
 * lets the verifier be unit-tested against {@code MockRestServiceServer}.
 */
@Configuration
public class TurnstileClientConfig {

    @Bean
    RestClient captchaRestClient(RestClient.Builder builder,
                                 @Value("${app.auth.captcha.api-call-timeout:PT5S}") Duration timeout,
                                 @Value("${app.auth.captcha.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
                                 String verifyUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = (int) Math.min(Integer.MAX_VALUE, timeout.toMillis());
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);
        return builder.requestFactory(factory).baseUrl(verifyUrl).build();
    }
}
