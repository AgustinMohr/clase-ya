package com.claseya.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurableContactNotificationSenderTest {

    @Test
    void composesTheContactEmailAndDelegatesToTheTransport() {
        CapturingEmailSender transport = new CapturingEmailSender(true);

        new ConfigurableContactNotificationSender(transport, false)
                .send("teacher@example.com", "Ada", "Grace", "https://app.test/mensajes/1?token=abc");

        assertThat(transport.to).isEqualTo("teacher@example.com");
        assertThat(transport.subject).isEqualTo("Tenés un nuevo contacto en ClaseYa");
        assertThat(transport.text).contains("Hola Ada");
        assertThat(transport.text).contains("Grace quiere contactarte");
        assertThat(transport.text).contains("https://app.test/mensajes/1?token=abc");
    }

    @Test
    void greetsWithoutAName() {
        CapturingEmailSender transport = new CapturingEmailSender(true);

        new ConfigurableContactNotificationSender(transport, false)
                .send("teacher@example.com", "  ", "Grace", "https://app.test/mensajes/1?token=abc");

        assertThat(transport.text).startsWith("Hola,");
    }

    @Test
    void failsPermanentlyWhenNothingCanDeliver() {
        ConfigurableContactNotificationSender sender =
                new ConfigurableContactNotificationSender(new CapturingEmailSender(false), false);

        assertThatThrownBy(() -> sender.send("teacher@example.com", "Ada", "Grace", "https://app.test/m"))
                .isInstanceOf(PermanentNotificationException.class);
    }
}
