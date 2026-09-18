package com.claseya.messaging.repository;

import com.claseya.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * The single conversation between two users (ordered by id so a duplicate,
     * if one ever slipped in, still resolves deterministically).
     */
    @Query("""
            select cp.conversation from ConversationParticipant cp
            where cp.user.id = :user1
              and exists (select 1 from ConversationParticipant p2
                          where p2.conversation = cp.conversation and p2.user.id = :user2)
            order by cp.conversation.id
            """)
    List<Conversation> findBetween(@Param("user1") UUID user1, @Param("user2") UUID user2);
}
