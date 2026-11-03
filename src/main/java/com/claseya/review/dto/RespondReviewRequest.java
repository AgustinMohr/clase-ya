package com.claseya.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespondReviewRequest(
        @NotBlank(message = "response is required")
        @Size(max = 1000, message = "response must be at most 1000 characters")
        String response
) {
}
