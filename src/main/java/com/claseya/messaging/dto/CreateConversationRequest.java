package com.claseya.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
        @NotNull(message = "teacherId is required")
        UUID teacherId,

        /** Optional first message of the contact request (CONTACT-001). Validated by the service. */
        String message
) {
}
