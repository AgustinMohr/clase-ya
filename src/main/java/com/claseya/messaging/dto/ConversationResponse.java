package com.claseya.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Detail of one conversation as seen by a participant.
 */
public record ConversationResponse(
        UUID id,
        ConversationParticipantResponse otherParticipant,
        long unreadCount,
        Instant updatedAt
) {
}
