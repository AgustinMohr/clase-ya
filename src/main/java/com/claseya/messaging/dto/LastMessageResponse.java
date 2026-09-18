package com.claseya.messaging.dto;

import com.claseya.model.Message;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight last-message payload embedded in the conversation list.
 */
public record LastMessageResponse(
        UUID id,
        String content,
        Instant createdAt,
        UUID senderId
) {

    public static LastMessageResponse from(Message message) {
        return new LastMessageResponse(
                message.getId(),
                message.getContent(),
                message.getCreatedAt(),
                message.getSender().getId());
    }
}
