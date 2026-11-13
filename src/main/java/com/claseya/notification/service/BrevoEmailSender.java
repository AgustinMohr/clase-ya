package com.claseya.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTPS transport for providers that expose an email API (NOTIF-001). It exists because some
 * platforms block outbound SMTP: Render Free rejects ports 25, 465 and 587, so the SMTP transport
 * cannot reach any provider there.
 *
 * <p>Failures are classified so the outbox does not burn retries: a rejected request (bad API key
 * or unverified sender) is permanent, while throttling, provider errors and network failures are
 * retried with the existing backoff.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email.transport", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final String SENDER_NAME = "ClaseYa";

    private final RestClient restClient;
    private final String apiUrl;
    private final String apiKey;
    private final String from;

    public BrevoEmailSender(RestClient emailRestClient,
                            @Value("${app.notification.email.api-url:https://api.brevo.com/v3/smtp/email}")
                            String apiUrl,
                            @Value("${app.notification.email.api-key:}") String apiKey,
                            @Value("${app.auth.activation.from:no-reply@claseya.app}") String from) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("EMAIL_API_KEY is required when EMAIL_TRANSPORT=brevo");
        }
        this.restClient = emailRestClient;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public void send(String toEmail, String subject, String text) {
        send(toEmail, new EmailContent(subject, text, null));
    }

    @Override
    public void send(String toEmail, EmailContent content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("email", from, "name", SENDER_NAME));
        payload.put("to", List.of(Map.of("email", toEmail)));
        payload.put("subject", content.subject());
        payload.put("textContent", content.text());
        if (content.html() != null) {
            payload.put("htmlContent", content.html());
        }
        try {
            restClient.post()
                    .uri(apiUrl)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400 || status == 401 || status == 403) {
                throw new PermanentNotificationException(
                        "Email provider rejected the request (" + status + ")");
            }
            log.warn("Email provider returned {}", status);
            throw new NotificationDeliveryException("Email provider failed (" + status + ")", ex);
        } catch (RestClientException ex) {
            log.warn("Email provider is unavailable");
            throw new NotificationDeliveryException("Email provider is unavailable", ex);
        }
    }
}
