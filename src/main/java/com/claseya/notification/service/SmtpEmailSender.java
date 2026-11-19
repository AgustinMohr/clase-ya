package com.claseya.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP transport (NOTIF-001). This is the original behaviour extracted from the two senders: a
 * message is sent when {@code spring.mail.host} is configured and Spring Boot created a
 * {@link JavaMailSender}.
 *
 * <p>It stays the default so local development and tests keep working without any new variable,
 * and so a paid host that allows outbound port 587 can still use SMTP.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email.transport", havingValue = "smtp",
        matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final ObjectProvider<JavaMailSender> javaMailSender;
    private final String host;
    private final String from;

    public SmtpEmailSender(ObjectProvider<JavaMailSender> javaMailSender,
                           @Value("${spring.mail.host:}") String host,
                           @Value("${app.auth.activation.from:no-reply@claseya.app}") String from) {
        this.javaMailSender = javaMailSender;
        this.host = host;
        this.from = from;
    }

    @Override
    public boolean isConfigured() {
        if (host == null || host.isBlank()) {
            return false;
        }
        return javaMailSender.getIfAvailable() != null;
    }

    @Override
    public void send(String toEmail, String subject, String text) {
        JavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null) {
            throw new PermanentNotificationException("Email delivery is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException ex) {
            log.error("Email delivery failed ({})", ex.getClass().getSimpleName());
            log.debug("Email delivery failure detail", ex);
            throw new NotificationDeliveryException("Email delivery failed", ex);
        }
    }
}
