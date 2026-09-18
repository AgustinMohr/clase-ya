package com.claseya.messaging.dto;

import com.claseya.model.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderDisplayName,
        String content,
        Instant createdAt,
        Instant readAt
) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getContent(),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
