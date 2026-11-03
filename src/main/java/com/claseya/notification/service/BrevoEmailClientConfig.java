package com.claseya.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP client used by {@link BrevoEmailSender} (NOTIF-001).
 *
 * <p>The client is built once, with bounded connect/read timeouts: the send happens on the outbox
 * worker thread, so a slow provider must never park a delivery indefinitely. Keeping the timeout
 * here (instead of inside the sender) is what lets the sender be unit-tested against
 * {@code MockRestServiceServer}.
 */
@Configuration
@ConditionalOnProperty(name = "app.notification.email.transport", havingValue = "brevo")
public class BrevoEmailClientConfig {

    @Bean
    RestClient emailRestClient(RestClient.Builder builder,
                               @Value("${app.notification.email.api-call-timeout:PT10S}")
                               Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int millis = (int) Math.min(Integer.MAX_VALUE, timeout.toMillis());
        factory.setConnectTimeout(millis);
        factory.setReadTimeout(millis);
        return builder.requestFactory(factory).build();
    }
}
