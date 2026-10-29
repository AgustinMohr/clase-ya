package com.claseya.messaging.repository;

import com.claseya.model.ConversationPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationPairRepository
        extends JpaRepository<ConversationPair, ConversationPair.Key> {

    /**
     * Claims the pair for {@code conversationId}. Returns 1 for the winner and 0 when another
     * transaction already owns the pair (RACE-01). Postgres serializes the conflicting insert, so
     * the loser only continues after the winner commits.
     */
    @Modifying
    @Query(value = """
            insert into conversation_pairs (student_user_id, teacher_user_id, conversation_id)
            values (:studentUserId, :teacherUserId, :conversationId)
            on conflict (student_user_id, teacher_user_id) do nothing
            """, nativeQuery = true)
    int claim(@Param("studentUserId") UUID studentUserId,
              @Param("teacherUserId") UUID teacherUserId,
              @Param("conversationId") UUID conversationId);

    @Query("""
            select p.conversation.id from ConversationPair p
            where p.id.studentUserId = :studentUserId
              and p.id.teacherUserId = :teacherUserId
            """)
    Optional<UUID> findConversationId(@Param("studentUserId") UUID studentUserId,
                                      @Param("teacherUserId") UUID teacherUserId);
}
