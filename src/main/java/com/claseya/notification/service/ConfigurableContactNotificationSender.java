package com.claseya.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the contact notice and delegates to the configured {@link EmailSender} transport
 * (NOTIF-001). Unlike the activation sender it never silently succeeds: when nothing can deliver,
 * the outbox retries or marks the row DEAD.
 */
@Component
public class ConfigurableContactNotificationSender implements ContactNotificationSender {

    private static final Logger log =
            LoggerFactory.getLogger(ConfigurableContactNotificationSender.class);

    static final String SUBJECT = "Tenés un nuevo contacto en ClaseYa";

    static final String BODY = """
            %s,

            %s quiere contactarte para coordinar clases. Podés abrir la conversación con este
            enlace:

            %s

            El enlace vence en 7 días y se puede usar una sola vez.
            Para responder, entrá a ClaseYa. No compartas este correo.
            """;

    private final EmailSender emailSender;
    private final boolean logLinks;

    public ConfigurableContactNotificationSender(EmailSender emailSender,
                                                 @Value("${app.notification.log-links:false}")
                                                 boolean logLinks) {
        this.emailSender = emailSender;
        this.logLinks = logLinks;
    }

    @Override
    public void send(String toEmail, String teacherName, String studentName, String link) {
        if (emailSender.isConfigured()) {
            String greeting = teacherName == null || teacherName.isBlank()
                    ? "Hola"
                    : "Hola " + teacherName;
            emailSender.send(toEmail, SUBJECT, BODY.formatted(greeting, studentName, link));
            return;
        }
        if (logLinks) {
            // Development only: the link is a credential, never log it elsewhere.
            log.warn("Contact link (MESSAGE_LINK_LOG) for {}: {}", toEmail, link);
            return;
        }
        throw new PermanentNotificationException("Email delivery is not configured");
    }
}
