package com.claseya.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 256, message = "Content must be at most 256 characters")
        String content
) {
}
