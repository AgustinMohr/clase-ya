package com.claseya.notification.service;

import com.claseya.model.NotificationOutboxEntry;
import com.claseya.model.User;
import com.claseya.model.enums.NotificationEventType;
import com.claseya.notification.repository.NotificationOutboxRepository;
import com.claseya.user.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers due outbox rows outside the business transaction (REVIEW2-002).
 *
 * <p>Claim and mark are short transactions; the network call happens between them, so a slow SMTP
 * server never holds a database connection. Rows are claimed with a lease, retried with backoff and
 * moved to DEAD after the configured attempts.
 */
@Service
public class NotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);
    private static final long[] BACKOFF_MINUTES = {1, 5, 15, 60, 240};
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String PASSWORD_RESET_SUBJECT = "Restablecé tu contraseña de ClaseYa";
    private static final String PASSWORD_RESET_BODY = """
            Hola,

            Para elegir una nueva contraseña de ClaseYa abrí este enlace:

            %s

            El enlace vence en 30 minutos y se puede usar una sola vez.
            Si no lo pediste, podés ignorar este mensaje.
            """;

    private final NotificationOutboxRepository repository;
    private final MessageLinkTokenService linkTokens;
    private final ContactNotificationSender contactSender;
    private final ActivationLinkSender activationSender;
    private final EmailSender emailSender;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry metrics;
    private final String linkBaseUrl;
    private final String activationBaseUrl;
    private final int batchSize;
    private final int maxAttempts;

    public NotificationOutboxWorker(NotificationOutboxRepository repository,
                                    MessageLinkTokenService linkTokens,
                                    ContactNotificationSender contactSender,
                                    ActivationLinkSender activationSender,
                                    EmailSender emailSender,
                                    UserRepository userRepository,
                                    TransactionTemplate transactionTemplate,
                                    MeterRegistry metrics,
                                    @Value("${app.notification.link-base-url:http://localhost:5173}")
                                    String linkBaseUrl,
                                    @Value("${app.auth.activation.base-url:http://localhost:5173}")
                                    String activationBaseUrl,
                                    @Value("${app.notification.worker.batch-size:10}") int batchSize,
                                    @Value("${app.notification.worker.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.linkTokens = linkTokens;
        this.contactSender = contactSender;
        this.activationSender = activationSender;
        this.emailSender = emailSender;
        this.userRepository = userRepository;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
        this.linkBaseUrl = linkBaseUrl;
        this.activationBaseUrl = activationBaseUrl;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** @return how many rows were delivered in this pass. */
    public int processDue() {
        List<Claimed> claimed = transactionTemplate.execute(status -> claim());
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (Claimed item : claimed) {
            if (deliver(item)) {
                delivered++;
            }
        }
        return delivered;
    }

    private List<Claimed> claim() {
        repository.releaseExpiredLeases();
        List<NotificationOutboxEntry> due = repository.lockDue(batchSize);
        List<Claimed> claimed = new ArrayList<>(due.size());
        for (NotificationOutboxEntry entry : due) {
            if (repository.markSending(entry.getId()) == 1) {
                claimed.add(new Claimed(entry.getId(), entry.getEventType(),
                        entry.getRecipientUserId(), entry.getPayload(), entry.getAttempts() + 1));
            }
        }
        return claimed;
    }

    private boolean deliver(Claimed item) {
        try {
            switch (item.eventType()) {
                case CONTACT_REQUEST -> deliverContact(item);
                case ACTIVATION -> deliverActivation(item);
                case PASSWORD_RESET -> deliverPasswordReset(item);
            }
            transactionTemplate.executeWithoutResult(status -> repository.markSent(item.id()));
            metrics.counter("claseya.notification.outbox.delivered",
                    "event_type", item.eventType().name()).increment();
            log.info("Outbox {} ({}) delivered on attempt {}", item.id(), item.eventType(),
                    item.attempts());
            return true;
        } catch (PermanentNotificationException ex) {
            transactionTemplate.executeWithoutResult(
                    status -> repository.markDead(item.id(), abbreviate(ex)));
            counter("dead", item).increment();
            log.error("Outbox {} ({}) is DEAD: {}", item.id(), item.eventType(), abbreviate(ex));
            return false;
        } catch (RuntimeException ex) {
            boolean dead = item.attempts() >= maxAttempts;
            transactionTemplate.executeWithoutResult(status -> markFailure(item, ex));
            counter(dead ? "dead" : "failed", item).increment();
            if (dead) {
                log.error("Outbox {} ({}) exhausted {} attempts: {}", item.id(), item.eventType(),
                        maxAttempts, abbreviate(ex));
            } else {
                log.warn("Outbox {} ({}) failed on attempt {}: {}", item.id(), item.eventType(),
                        item.attempts(), abbreviate(ex));
            }
            return false;
        }
    }

    private Counter counter(String outcome, Claimed item) {
        return metrics.counter("claseya.notification.outbox." + outcome,
                "event_type", item.eventType().name());
    }

    private void deliverContact(Claimed item) {
        User teacher = userRepository.findById(item.recipientUserId())
                .orElseThrow(() -> new PermanentNotificationException("Recipient no longer exists"));
        UUID conversationId = UUID.fromString(String.valueOf(item.payload().get("conversationId")));
        String studentName = String.valueOf(item.payload().getOrDefault("studentName", "Un estudiante"));
        String rawToken = linkTokens.issue(teacher.getId(), conversationId);
        String link = trimTrailingSlash(linkBaseUrl) + "/mensajes/" + conversationId
                + "?token=" + rawToken;
        contactSender.send(teacher.getEmail(), teacher.getName(), studentName, link);
    }

    /** RF-1/RF-6: the token was created inside the registration transaction; only delivery is here. */
    private void deliverActivation(Claimed item) {
        String email = stringPayload(item, "email");
        String rawToken = stringPayload(item, "token");
        activationSender.send(email, trimTrailingSlash(activationBaseUrl) + "/activar?token=" + rawToken);
    }

    private void deliverPasswordReset(Claimed item) {
        String email = stringPayload(item, "email");
        String rawToken = stringPayload(item, "token");
        String link = trimTrailingSlash(activationBaseUrl) + "/restablecer?token=" + rawToken;
        emailSender.send(email, PASSWORD_RESET_SUBJECT, PASSWORD_RESET_BODY.formatted(link));
    }

    private static String stringPayload(Claimed item, String key) {
        Object value = item.payload().get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new PermanentNotificationException("Outbox payload is missing " + key);
        }
        return String.valueOf(value);
    }

    private void markFailure(Claimed item, RuntimeException ex) {
        String error = abbreviate(ex);
        if (item.attempts() >= maxAttempts) {
            repository.markDead(item.id(), error);
            return;
        }
        repository.markFailed(item.id(), error, nextAttemptAt(item.attempts()));
    }

    private static Instant nextAttemptAt(int attempts) {
        int index = Math.min(Math.max(attempts, 1), BACKOFF_MINUTES.length) - 1;
        return Instant.now().plus(Duration.ofMinutes(BACKOFF_MINUTES[index]));
    }

    private static String abbreviate(RuntimeException ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private record Claimed(UUID id,
                           NotificationEventType eventType,
                           UUID recipientUserId,
                           Map<String, Object> payload,
                           int attempts) {
    }
}
