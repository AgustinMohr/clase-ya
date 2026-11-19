package com.claseya.notification.repository;

import com.claseya.model.NotificationOutboxEntry;
import com.claseya.model.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntry, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    long countByStatus(OutboxStatus status);

    /**
     * Claims the next batch. {@code SKIP LOCKED} lets several instances work in parallel, and the
     * row lock is released when the claiming transaction commits (the send happens afterwards).
     */
    @Query(value = """
            select * from notification_outbox
            where status in ('PENDING', 'FAILED') and next_attempt_at <= now()
            order by next_attempt_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<NotificationOutboxEntry> lockDue(@Param("limit") int limit);

    /** A worker that died mid-send leaves the row SENDING; the expired lease makes it claimable. */
    @Modifying
    @Query(value = """
            update notification_outbox
            set status = 'PENDING', locked_until = null
            where status = 'SENDING' and locked_until < now()
            """, nativeQuery = true)
    int releaseExpiredLeases();

    @Modifying
    @Query(value = """
            update notification_outbox
            set status = 'SENDING', locked_until = now() + interval '2 minutes', attempts = attempts + 1
            where id = :id
            """, nativeQuery = true)
    int markSending(@Param("id") UUID id);

    /**
     * ADR-029: once the row is terminal the activation or password-reset token is dropped from the
     * payload ({@code payload - 'token'}). The credential is only needed until delivery; keeping it
     * at rest afterwards adds no value. The contact payload is left untouched.
     */
    @Modifying
    @Query(value = """
            update notification_outbox
            set status = 'SENT', sent_at = now(), locked_until = null, last_error = null,
                payload = case when event_type in ('ACTIVATION', 'PASSWORD_RESET')
                               then payload - 'token' else payload end
            where id = :id
            """, nativeQuery = true)
    int markSent(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            update notification_outbox
            set status = 'FAILED', locked_until = null, last_error = :error, next_attempt_at = :next
            where id = :id
            """, nativeQuery = true)
    int markFailed(@Param("id") UUID id, @Param("error") String error, @Param("next") Instant next);

    @Modifying
    @Query(value = """
            update notification_outbox
            set status = 'DEAD', locked_until = null, last_error = :error,
                payload = case when event_type in ('ACTIVATION', 'PASSWORD_RESET')
                               then payload - 'token' else payload end
            where id = :id
            """, nativeQuery = true)
    int markDead(@Param("id") UUID id, @Param("error") String error);
}
