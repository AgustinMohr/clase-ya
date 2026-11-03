package com.claseya.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/message-link} (REVIEW2-002). */
public record MessageLinkRequest(
        @NotBlank(message = "token is required")
        String token
) {
}
