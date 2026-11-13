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
            emailSender.send(toEmail, EmailTemplates.contact(teacherName, studentName, link));
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
