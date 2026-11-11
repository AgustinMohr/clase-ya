package com.claseya.notification.service;

import com.claseya.model.NotificationOutboxEntry;
import com.claseya.model.enums.NotificationEventType;
import com.claseya.model.enums.OutboxStatus;
import com.claseya.notification.repository.NotificationOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enqueues side effects in the same transaction as the business action (REVIEW2-002).
 * Nothing is sent here: the worker delivers after commit.
 */
@Service
public class NotificationOutboxService {

    private final NotificationOutboxRepository repository;

    public NotificationOutboxService(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    /**
     * Queues the activation email (AUTH-001) inside the registration transaction. The dedupe key is
     * the token row, so every issued token produces exactly one email even under retries.
     *
     * <p>The raw token travels in the payload (it cannot be rebuilt from the stored hash) and the
     * worker clears the payload as soon as the row reaches SENT/DEAD (ADR-029).
     */
    @Transactional
    public void enqueueActivation(UUID recipientUserId, String email, UUID tokenId, String rawToken) {
        String dedupeKey = "activation:" + tokenId;
        if (repository.existsByDedupeKey(dedupeKey)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("token", rawToken);

        NotificationOutboxEntry entry = new NotificationOutboxEntry();
        entry.setEventType(NotificationEventType.ACTIVATION);
        entry.setRecipientUserId(recipientUserId);
        entry.setDedupeKey(dedupeKey);
        entry.setPayload(payload);
        entry.setStatus(OutboxStatus.PENDING);
        entry.setNextAttemptAt(Instant.now());
        repository.save(entry);
    }

    /** Queues a single-use password-reset link (AUTH-003). */
    @Transactional
    public void enqueuePasswordReset(UUID recipientUserId, String email, UUID tokenId, String rawToken) {
        String dedupeKey = "password-reset:" + tokenId;
        if (repository.existsByDedupeKey(dedupeKey)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("token", rawToken);

        NotificationOutboxEntry entry = new NotificationOutboxEntry();
        entry.setEventType(NotificationEventType.PASSWORD_RESET);
        entry.setRecipientUserId(recipientUserId);
        entry.setDedupeKey(dedupeKey);
        entry.setPayload(payload);
        entry.setStatus(OutboxStatus.PENDING);
        entry.setNextAttemptAt(Instant.now());
        repository.save(entry);
    }

    /**
     * Queues the teacher notification for a new contact. The dedupe key is the conversation, so a
     * retry (or a second call for the same thread) never sends two emails.
     */
    @Transactional
    public void enqueueContactRequest(UUID recipientUserId, UUID conversationId, String studentName) {
        String dedupeKey = "contact:" + conversationId;
        if (repository.existsByDedupeKey(dedupeKey)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId.toString());
        payload.put("studentName", studentName == null || studentName.isBlank()
                ? "Un estudiante"
                : studentName.trim());

        NotificationOutboxEntry entry = new NotificationOutboxEntry();
        entry.setEventType(NotificationEventType.CONTACT_REQUEST);
        entry.setRecipientUserId(recipientUserId);
        entry.setDedupeKey(dedupeKey);
        entry.setPayload(payload);
        entry.setStatus(OutboxStatus.PENDING);
        entry.setNextAttemptAt(Instant.now());
        repository.save(entry);
    }
}
