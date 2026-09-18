package com.claseya.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConversationRequest(
        @NotNull(message = "teacherId is required")
        UUID teacherId
) {
}
