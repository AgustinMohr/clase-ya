package com.claseya.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmailTransportSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void brevoIsSelectedByProperty() {
        runner.withUserConfiguration(BrevoEmailSender.class)
                .withBean(RestClient.class, () -> mock(RestClient.class))
                .withPropertyValues(
                        "app.notification.email.transport=brevo",
                        "app.notification.email.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context.getBean(EmailSender.class)).isInstanceOf(BrevoEmailSender.class);
                });
    }

    @Test
    void smtpIsTheDefault() {
        runner.withUserConfiguration(BrevoEmailSender.class, SmtpEmailSender.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class);
                });
    }

    @Test
    void smtpIsSelectedExplicitly() {
        runner.withUserConfiguration(BrevoEmailSender.class, SmtpEmailSender.class)
                .withPropertyValues("app.notification.email.transport=smtp")
                .run(context -> assertThat(context.getBean(EmailSender.class))
                        .isInstanceOf(SmtpEmailSender.class));
    }

    @Test
    void brevoWithoutAnApiKeyFailsFast() {
        runner.withUserConfiguration(BrevoEmailSender.class)
                .withBean(RestClient.class, () -> mock(RestClient.class))
                .withPropertyValues("app.notification.email.transport=brevo")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("EMAIL_API_KEY is required when EMAIL_TRANSPORT=brevo");
                });
    }
}
