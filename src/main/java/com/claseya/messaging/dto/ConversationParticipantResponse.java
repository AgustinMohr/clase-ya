package com.claseya.messaging.dto;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;

import java.util.UUID;

/**
 * Minimal public identity of a participant. Never includes email.
 */
public record ConversationParticipantResponse(
        UUID id,
        String displayName,
        UserRole role
) {

    public static ConversationParticipantResponse from(User user) {
        return new ConversationParticipantResponse(
                user.getId(),
                user.getName() != null ? user.getName() : "",
                user.getRole());
    }
}
