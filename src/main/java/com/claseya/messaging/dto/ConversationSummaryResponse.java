package com.claseya.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversation card for the "my conversations" list.
 */
public record ConversationSummaryResponse(
        UUID id,
        ConversationParticipantResponse otherParticipant,
        LastMessageResponse lastMessage,
        long unreadCount,
        Instant updatedAt
) {
}
