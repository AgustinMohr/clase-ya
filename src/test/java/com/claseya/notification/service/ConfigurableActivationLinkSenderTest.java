package com.claseya.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurableActivationLinkSenderTest {

    @Test
    void composesTheActivationEmailAndDelegatesToTheTransport() {
        CapturingEmailSender transport = new CapturingEmailSender(true);

        new ConfigurableActivationLinkSender(transport, false)
                .send("student@example.com", "https://app.test/activar?token=abc");

        assertThat(transport.to).isEqualTo("student@example.com");
        assertThat(transport.subject).isEqualTo("Activá tu cuenta de ClaseYa");
        assertThat(transport.text).contains("https://app.test/activar?token=abc");
        assertThat(transport.text).contains("El enlace vence en 24 horas");
    }

    @Test
    void isConfiguredWhenLinksAreLogged() {
        ConfigurableActivationLinkSender sender =
                new ConfigurableActivationLinkSender(new CapturingEmailSender(false), true);

        assertThat(sender.isConfigured()).isTrue();
    }

    @Test
    void isNotConfiguredWithoutTransportOrLogging() {
        ConfigurableActivationLinkSender sender =
                new ConfigurableActivationLinkSender(new CapturingEmailSender(false), false);

        assertThat(sender.isConfigured()).isFalse();
    }

    @Test
    void failsPermanentlyWhenNothingCanDeliver() {
        ConfigurableActivationLinkSender sender =
                new ConfigurableActivationLinkSender(new CapturingEmailSender(false), false);

        assertThatThrownBy(() -> sender.send("student@example.com", "https://app.test/activar?token=abc"))
                .isInstanceOf(PermanentNotificationException.class);
    }
}
