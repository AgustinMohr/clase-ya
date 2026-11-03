package com.claseya;

import com.claseya.auth.dto.IssuedSession;
import com.claseya.auth.service.MessageLinkAuthService;
import com.claseya.common.exception.BadRequestException;
import com.claseya.model.Conversation;
import com.claseya.model.MessageLinkToken;
import com.claseya.model.NotificationOutboxEntry;
import com.claseya.model.User;
import com.claseya.model.enums.NotificationEventType;
import com.claseya.model.enums.OutboxStatus;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.messaging.repository.ConversationRepository;
import com.claseya.notification.repository.MessageLinkTokenRepository;
import com.claseya.notification.repository.NotificationOutboxRepository;
import com.claseya.notification.service.ActivationLinkSender;
import com.claseya.notification.service.ContactNotificationSender;
import com.claseya.notification.service.MessageLinkTokenService;
import com.claseya.notification.service.NotificationOutboxService;
import com.claseya.notification.service.NotificationOutboxWorker;
import com.claseya.notification.service.PermanentNotificationException;
import com.claseya.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * REVIEW2-002: outbox delivery, retry/backoff, dedupe and the single-use message link.
 *
 * <p>Own container and no rollback transaction: the worker claims and marks in separate short
 * transactions, which is exactly the behaviour under test.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationOutboxIntegrationTest {

    private static final String TEST_SECRET =
            "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("claseya_outbox")
                .withUsername("claseya")
                .withPassword("claseya");
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("jwt.expiration", () -> "3600");
    }

    @MockitoBean
    private ContactNotificationSender sender;

    @MockitoBean
    private ActivationLinkSender activationLinkSender;

    @Autowired
    private NotificationOutboxService outboxService;
    @Autowired
    private NotificationOutboxWorker worker;
    @Autowired
    private NotificationOutboxRepository outboxRepository;
    @Autowired
    private MessageLinkTokenService linkTokens;
    @Autowired
    private MessageLinkTokenRepository linkTokenRepository;
    @Autowired
    private MessageLinkAuthService messageLinkAuthService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanOutbox() {
        // Tests share one container (the worker needs real commits); start each case empty.
        outboxRepository.deleteAll();
        linkTokenRepository.deleteAll();
    }

    @Test
    void contactRequestIsDeliveredOnceAndIssuesASingleUseLink() {
        User teacher = createTeacher();
        Conversation conversation = newConversation();
        outboxService.enqueueContactRequest(teacher.getId(), conversation.getId(), "Ana");

        int delivered = worker.processDue();

        assertThat(delivered).isEqualTo(1);
        verify(sender, times(1)).send(anyString(), anyString(), anyString(), anyString());
        assertThat(outboxRows(conversation.getId())).hasSize(1);
        assertThat(outboxRows(conversation.getId()).get(0).getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outboxRows(conversation.getId()).get(0).getSentAt()).isNotNull();
        assertThat(linkTokenRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void enqueueIsIdempotentPerConversation() {
        User teacher = createTeacher();
        Conversation conversation = newConversation();

        outboxService.enqueueContactRequest(teacher.getId(), conversation.getId(), "Ana");
        outboxService.enqueueContactRequest(teacher.getId(), conversation.getId(), "Ana");

        assertThat(outboxRows(conversation.getId())).hasSize(1);
    }

    @Test
    void aFailedSendIsRetriedWithBackoffAndBecomesDeadAfterTheLimit() {
        User teacher = createTeacher();
        Conversation conversation = newConversation();
        outboxService.enqueueContactRequest(teacher.getId(), conversation.getId(), "Ana");
        doThrow(new IllegalStateException("smtp down"))
                .when(sender).send(anyString(), anyString(), anyString(), anyString());

        assertThat(worker.processDue()).isZero();

        NotificationOutboxEntry afterFirstFailure = outboxRows(conversation.getId()).get(0);
        assertThat(afterFirstFailure.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(afterFirstFailure.getAttempts()).isEqualTo(1);
        assertThat(afterFirstFailure.getNextAttemptAt()).isAfter(Instant.now());

        // Force the remaining attempts to run now; the fifth failure is terminal.
        for (int attempt = 2; attempt <= 5; attempt++) {
            afterFirstFailure.setNextAttemptAt(Instant.now().minus(Duration.ofSeconds(1)));
            outboxRepository.saveAndFlush(afterFirstFailure);
            worker.processDue();
            afterFirstFailure = outboxRows(conversation.getId()).get(0);
        }

        assertThat(afterFirstFailure.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(afterFirstFailure.getAttempts()).isEqualTo(5);
    }

    @Test
    void activationEmailIsDeliveredAndTheRawTokenLeavesThePayload() {
        User teacher = createTeacher();
        UUID tokenId = UUID.randomUUID();
        outboxService.enqueueActivation(teacher.getId(), teacher.getEmail(), tokenId, "raw-token");

        assertThat(worker.processDue()).isEqualTo(1);

        verify(activationLinkSender, times(1))
                .send(eq(teacher.getEmail()), contains("token=raw-token"));
        NotificationOutboxEntry row = activationRows().get(0);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.SENT);
        // ADR-029: the credential is not kept at rest once the row is terminal.
        assertThat(row.getPayload()).doesNotContainKey("token");
        assertThat(row.getPayload()).containsEntry("email", teacher.getEmail());
    }

    @Test
    void activationEmailWithoutTransportBecomesDeadWithoutRetries() {
        User teacher = createTeacher();
        outboxService.enqueueActivation(teacher.getId(), teacher.getEmail(), UUID.randomUUID(), "raw");
        doThrow(new PermanentNotificationException("Email delivery is not configured"))
                .when(activationLinkSender).send(anyString(), anyString());

        assertThat(worker.processDue()).isZero();

        NotificationOutboxEntry row = activationRows().get(0);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getPayload()).doesNotContainKey("token");
    }

    @Test
    void messageLinkIsExchangedOnceAndReturnsASession() {
        User teacher = createTeacher();
        Conversation conversation = newConversation();
        String raw = linkTokens.issue(teacher.getId(), conversation.getId());

        IssuedSession session = messageLinkAuthService.exchange(raw);

        assertThat(session.token()).isNotBlank();
        assertThat(session.user().id()).isEqualTo(teacher.getId());
        assertThatThrownBy(() -> messageLinkAuthService.exchange(raw))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void expiredMessageLinkIsRejected() {
        User teacher = createTeacher();
        Conversation conversation = newConversation();
        MessageLinkToken token = new MessageLinkToken();
        token.setUser(teacher);
        token.setConversation(conversation);
        token.setTokenHash(com.claseya.common.util.Hashes.sha256Hex("expired-token"));
        token.setExpiresAt(Instant.now().minusSeconds(60));
        linkTokenRepository.saveAndFlush(token);

        assertThatThrownBy(() -> messageLinkAuthService.exchange("expired-token"))
                .isInstanceOf(BadRequestException.class);
    }

    private User createTeacher() {
        User user = new User();
        user.setEmail("outbox+" + UUID.randomUUID() + "@test.com");
        user.setName("Profe Outbox");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setRole(UserRole.TEACHER);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Conversation newConversation() {
        return conversationRepository.saveAndFlush(new Conversation());
    }

    private java.util.List<NotificationOutboxEntry> outboxRows(UUID conversationId) {
        return outboxRepository.findAll().stream()
                .filter(row -> row.getEventType() == NotificationEventType.CONTACT_REQUEST)
                .filter(row -> conversationId.toString().equals(row.getPayload().get("conversationId")))
                .toList();
    }

    private java.util.List<NotificationOutboxEntry> activationRows() {
        return outboxRepository.findAll().stream()
                .filter(row -> row.getEventType() == NotificationEventType.ACTIVATION)
                .toList();
    }
}
