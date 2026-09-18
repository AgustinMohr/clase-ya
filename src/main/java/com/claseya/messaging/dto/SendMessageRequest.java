package com.claseya.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must be at most 5000 characters")
        String content
) {
}
