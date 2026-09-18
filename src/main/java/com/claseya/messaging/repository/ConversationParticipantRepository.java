package com.claseya.messaging.repository;

import com.claseya.model.ConversationParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, UUID> {

    @Query(value = """
            select cp from ConversationParticipant cp
            join fetch cp.conversation
            where cp.user.id = :userId
            """,
            countQuery = """
                    select count(cp) from ConversationParticipant cp
                    where cp.user.id = :userId
                    """)
    Page<ConversationParticipant> findParticipations(@Param("userId") UUID userId, Pageable pageable);

    /** The other participants (users already joined) for a set of conversations. */
    @Query("""
            select cp from ConversationParticipant cp
            join fetch cp.user
            where cp.conversation.id in :conversationIds
              and cp.user.id <> :me
            """)
    List<ConversationParticipant> findOthers(@Param("conversationIds") Collection<UUID> conversationIds,
                                             @Param("me") UUID me);

    boolean existsByConversation_IdAndUser_Id(UUID conversationId, UUID userId);

    Optional<ConversationParticipant> findByConversation_IdAndUser_Id(UUID conversationId, UUID userId);
}
