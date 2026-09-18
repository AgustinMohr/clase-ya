package com.claseya.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description
) {
}
