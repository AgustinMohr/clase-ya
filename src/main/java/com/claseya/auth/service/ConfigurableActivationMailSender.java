package com.claseya.auth.service;

import com.claseya.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Picks the delivery strategy at runtime:
 *
 * <ul>
 *   <li>SMTP when {@code spring.mail.host} is configured (any real environment).</li>
 *   <li>A logged link when {@code app.auth.activation.log-links=true} (development opt-in, the
 *       same spirit as the demo seed).</li>
 *   <li>Otherwise the endpoint answers 503 and the registration rolls back, so we never leave an
 *       account that the owner cannot activate.</li>
 * </ul>
 */
@Component
public class ConfigurableActivationMailSender implements ActivationMailSender {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableActivationMailSender.class);

    private final ObjectProvider<JavaMailSender> javaMailSender;
    private final String host;
    private final boolean logLinks;
    private final String from;

    public ConfigurableActivationMailSender(ObjectProvider<JavaMailSender> javaMailSender,
                                            @Value("${spring.mail.host:}") String host,
                                            @Value("${app.auth.activation.log-links:false}") boolean logLinks,
                                            @Value("${app.auth.activation.from:no-reply@claseya.app}") String from) {
        this.javaMailSender = javaMailSender;
        this.host = host;
        this.logLinks = logLinks;
        this.from = from;
    }

    @Override
    public void sendActivation(String toEmail, String activationUrl) {
        if (host != null && !host.isBlank()) {
            sendBySmtp(toEmail, activationUrl);
            return;
        }
        if (logLinks) {
            // Development only. Never enable this outside a local profile: the link is a credential.
            log.warn("Activation link (AUTH_LOG_ACTIVATION_LINKS) for {}: {}", toEmail, activationUrl);
            return;
        }
        throw new ServiceUnavailableException("Email delivery is not configured");
    }

    private void sendBySmtp(String toEmail, String activationUrl) {
        JavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null) {
            throw new ServiceUnavailableException("Email delivery is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Activá tu cuenta de ClaseYa");
        message.setText("""
                Hola,

                Para activar tu cuenta de ClaseYa abrí este enlace:

                %s

                El enlace vence en 24 horas y se puede usar una sola vez.
                Si no creaste esta cuenta, podés ignorar este mensaje.
                """.formatted(activationUrl));
        try {
            sender.send(message);
        } catch (MailException ex) {
            log.error("Activation email could not be sent to {}", toEmail, ex);
            throw new ServiceUnavailableException("Email delivery failed");
        }
    }
}
