package com.claseya.notification.service;

import com.claseya.common.util.Hashes;
import com.claseya.model.Conversation;
import com.claseya.model.MessageLinkToken;
import com.claseya.model.User;
import com.claseya.notification.repository.MessageLinkTokenRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and consumes the single-use link embedded in the contact email (REVIEW2-002).
 * Only the SHA-256 hash is stored; the raw token exists in memory and in the email only.
 */
@Service
public class MessageLinkTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MessageLinkTokenRepository repository;
    private final EntityManager entityManager;
    private final Duration tokenTtl;

    public MessageLinkTokenService(MessageLinkTokenRepository repository,
                                   EntityManager entityManager,
                                   @Value("${app.notification.link-ttl:PT168H}") Duration tokenTtl) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.tokenTtl = tokenTtl;
    }

    /** Creates a token bound to the recipient and the conversation. Returns the raw value. */
    @Transactional
    public String issue(UUID userId, UUID conversationId) {
        String raw = newToken();
        MessageLinkToken token = new MessageLinkToken();
        // References avoid loading the entities: the worker only needs the ids.
        token.setUser(entityManager.getReference(User.class, userId));
        token.setConversation(entityManager.getReference(Conversation.class, conversationId));
        token.setTokenHash(Hashes.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(tokenTtl));
        repository.save(token);
        return raw;
    }

    /**
     * Marks the token used and returns its target. Empty for unknown, expired, already used, or a
     * concurrent exchange that lost the race.
     */
    @Transactional
    public Optional<MessageLinkTarget> consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        MessageLinkToken token = repository.findByTokenHash(Hashes.sha256Hex(rawToken.trim()))
                .orElse(null);
        if (token == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (repository.markUsed(token.getId(), now) == 0) {
            return Optional.empty();
        }
        return Optional.of(new MessageLinkTarget(token.getUser().getId(), token.getConversation().getId()));
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record MessageLinkTarget(UUID userId, UUID conversationId) {
    }
}
