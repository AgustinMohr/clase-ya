package com.claseya.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SmtpEmailSenderTest {

    private static final String FROM = "no-reply@claseya.app";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ObjectProvider<JavaMailSender> provider;

    @Test
    void isNotConfiguredWithoutAHost() {
        assertThat(new SmtpEmailSender(provider, "", FROM).isConfigured()).isFalse();
    }

    @Test
    void isNotConfiguredWhenSpringMailIsAbsent() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(new SmtpEmailSender(provider, "smtp.example.com", FROM).isConfigured()).isFalse();
    }

    @Test
    void isConfiguredWithAHostAndAMailSender() {
        when(provider.getIfAvailable()).thenReturn(mailSender);

        assertThat(new SmtpEmailSender(provider, "smtp.example.com", FROM).isConfigured()).isTrue();
    }

    @Test
    void sendsTheMessageWithTheConfiguredSender() {
        when(provider.getIfAvailable()).thenReturn(mailSender);

        new SmtpEmailSender(provider, "smtp.example.com", FROM)
                .send("student@example.com", "Activá tu cuenta de ClaseYa", "cuerpo");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo(FROM);
        assertThat(captor.getValue().getTo()).containsExactly("student@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Activá tu cuenta de ClaseYa");
        assertThat(captor.getValue().getText()).isEqualTo("cuerpo");
    }

    @Test
    void sendsAnHtmlAlternativeWhenProvided() throws Exception {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        new SmtpEmailSender(provider, "smtp.example.com", FROM)
                .send("student@example.com", new EmailContent("Asunto", "texto", "<p>html</p>"));

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Asunto");
        assertThat(message.getContentType()).contains("multipart/");
    }

    @Test
    void mapsMailFailuresToRetryableWithoutLoggingTheRecipient(CapturedOutput output) {
        when(provider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        SmtpEmailSender sender = new SmtpEmailSender(provider, "smtp.example.com", FROM);

        assertThatThrownBy(() -> sender.send("student@example.com", "asunto", "cuerpo"))
                .isInstanceOf(NotificationDeliveryException.class);
        assertThat(output.getOut()).doesNotContain("student@example.com");
        assertThat(output.getErr()).doesNotContain("student@example.com");
    }

    @Test
    void failsPermanentlyWithoutAMailSender() {
        when(provider.getIfAvailable()).thenReturn(null);

        SmtpEmailSender sender = new SmtpEmailSender(provider, "smtp.example.com", FROM);

        assertThatThrownBy(() -> sender.send("student@example.com", "asunto", "cuerpo"))
                .isInstanceOf(PermanentNotificationException.class);
    }
}
