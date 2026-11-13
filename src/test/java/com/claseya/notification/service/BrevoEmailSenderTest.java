package com.claseya.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BrevoEmailSenderTest {

    private static final String URL = "https://brevo.test/v3/smtp/email";
    private static final String FROM = "no-reply@claseya.app";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private BrevoEmailSender sender() {
        return new BrevoEmailSender(builder.build(), URL, "brevo-key", FROM);
    }

    @Test
    void failsFastWithoutAnApiKey() {
        assertThatThrownBy(() -> new BrevoEmailSender(builder.build(), URL, " ", FROM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_API_KEY");
    }

    @Test
    void isConfiguredWithAnApiKey() {
        assertThat(sender().isConfigured()).isTrue();
    }

    @Test
    void sendsTheProviderPayload() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "brevo-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "sender": {"email": "no-reply@claseya.app", "name": "ClaseYa"},
                          "to": [{"email": "student@example.com"}],
                          "subject": "Activá tu cuenta de ClaseYa",
                          "textContent": "cuerpo del email"
                        }
                        """))
                .andRespond(withStatus(HttpStatus.CREATED));

        sender().send("student@example.com", "Activá tu cuenta de ClaseYa", "cuerpo del email");

        server.verify();
    }

    @Test
    void sendsTheHtmlAlternativeWhenProvided() {
        server.expect(requestTo(URL))
                .andExpect(content().json("""
                        {
                          "sender": {"email": "no-reply@claseya.app", "name": "ClaseYa"},
                          "to": [{"email": "student@example.com"}],
                          "subject": "Activá tu cuenta de ClaseYa",
                          "textContent": "cuerpo del email",
                          "htmlContent": "<p>cuerpo del email</p>"
                        }
                        """))
                .andRespond(withStatus(HttpStatus.CREATED));

        sender().send("student@example.com",
                new EmailContent("Activá tu cuenta de ClaseYa", "cuerpo del email", "<p>cuerpo del email</p>"));

        server.verify();
    }

    @Test
    void treatsARejectedRequestAsPermanent() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> sender().send("student@example.com", "asunto", "cuerpo"))
                .isInstanceOf(PermanentNotificationException.class);
    }

    @Test
    void treatsAnInvalidSenderAsPermanent() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> sender().send("student@example.com", "asunto", "cuerpo"))
                .isInstanceOf(PermanentNotificationException.class);
    }

    @Test
    void retriesThrottling() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> sender().send("student@example.com", "asunto", "cuerpo"))
                .isInstanceOf(NotificationDeliveryException.class);
    }

    @Test
    void retriesProviderErrors() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> sender().send("student@example.com", "asunto", "cuerpo"))
                .isInstanceOf(NotificationDeliveryException.class);
    }

    @Test
    void retriesNetworkFailures() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> sender().send("student@example.com", "asunto", "cuerpo"))
                .isInstanceOf(NotificationDeliveryException.class);
    }
}
