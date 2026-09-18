package com.claseya.messaging.repository;

import com.claseya.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query(value = """
            select m from Message m
            join fetch m.sender
            join fetch m.conversation
            where m.conversation.id = :conversationId
            """,
            countQuery = "select count(m) from Message m where m.conversation.id = :conversationId")
    Page<Message> findWithSenderByConversation(@Param("conversationId") UUID conversationId,
                                               Pageable pageable);

    interface UnreadByConversation {
        UUID getConversationId();

        long getUnread();
    }

    @Query("""
            select m.conversation.id as conversationId, count(m) as unread
            from Message m
            where m.conversation.id in :conversationIds
              and m.sender.id <> :me
              and m.readAt is null
            group by m.conversation.id
            """)
    List<UnreadByConversation> countUnreadGrouped(
            @Param("conversationIds") Collection<UUID> conversationIds,
            @Param("me") UUID me);

    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId
              and m.sender.id <> :me
              and m.readAt is null
            """)
    long countUnread(@Param("conversationId") UUID conversationId, @Param("me") UUID me);

    /**
     * The latest message of every conversation in the set, in a single query.
     * Uses a correlated max(createdAt, id) so at most one row per conversation.
     */
    @Query("""
            select m from Message m
            join fetch m.sender
            where m.conversation.id in :conversationIds
              and m.createdAt = (select max(m2.createdAt) from Message m2
                                 where m2.conversation.id = m.conversation.id)
              and m.id = (select max(m3.id) from Message m3
                          where m3.conversation.id = m.conversation.id
                            and m3.createdAt = m.createdAt)
            """)
    List<Message> findLastPerConversation(@Param("conversationIds") Collection<UUID> conversationIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Message m set m.readAt = :now
            where m.conversation.id = :conversationId
              and m.sender.id <> :me
              and m.readAt is null
            """)
    int markConversationRead(@Param("conversationId") UUID conversationId,
                             @Param("me") UUID me,
                             @Param("now") Instant now);
}
