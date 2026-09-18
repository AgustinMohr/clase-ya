package com.claseya.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAcademicUnitRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 50, message = "Code must be at most 50 characters")
        String code
) {
}
