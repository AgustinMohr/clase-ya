package com.claseya.messaging.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One page of a conversation thread: participant detail plus the requested messages in
 * chronological order (oldest first). {@code nextBefore} is the cursor for the next older page.
 */
public record ConversationThreadResponse(
        UUID id,
        ConversationParticipantResponse otherParticipant,
        long unreadCount,
        Instant updatedAt,
        List<MessageResponse> messages,
        boolean hasMore,
        UUID nextBefore
) {
}
