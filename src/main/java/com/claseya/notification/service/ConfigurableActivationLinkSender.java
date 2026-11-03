package com.claseya.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Picks the activation delivery strategy at runtime (NOTIF-001 keeps the two fallbacks intact):
 *
 * <ul>
 *   <li>The configured {@link EmailSender} transport when it is available (SMTP or HTTPS API).</li>
 *   <li>A logged link when {@code app.auth.activation.log-links=true} (development opt-in, the
 *       same spirit as the demo seed).</li>
 *   <li>Otherwise {@link #isConfigured()} is false: registration answers 503 and rolls back, so we
 *       never leave an account that the owner cannot activate.</li>
 * </ul>
 */
@Component
public class ConfigurableActivationLinkSender implements ActivationLinkSender {

    private static final Logger log =
            LoggerFactory.getLogger(ConfigurableActivationLinkSender.class);

    static final String SUBJECT = "Activá tu cuenta de ClaseYa";

    static final String BODY = """
            Hola,

            Para activar tu cuenta de ClaseYa abrí este enlace:

            %s

            El enlace vence en 24 horas y se puede usar una sola vez.
            Si no creaste esta cuenta, podés ignorar este mensaje.
            """;

    private final EmailSender emailSender;
    private final boolean logLinks;

    public ConfigurableActivationLinkSender(EmailSender emailSender,
                                            @Value("${app.auth.activation.log-links:false}")
                                            boolean logLinks) {
        this.emailSender = emailSender;
        this.logLinks = logLinks;
    }

    @Override
    public boolean isConfigured() {
        return emailSender.isConfigured() || logLinks;
    }

    @Override
    public void send(String toEmail, String activationUrl) {
        if (emailSender.isConfigured()) {
            emailSender.send(toEmail, SUBJECT, BODY.formatted(activationUrl));
            return;
        }
        if (logLinks) {
            // Development only. Never enable this outside a local profile: the link is a credential.
            log.warn("Activation link (AUTH_LOG_ACTIVATION_LINKS) for {}: {}", toEmail, activationUrl);
            return;
        }
        throw new PermanentNotificationException("Email delivery is not configured");
    }
}
