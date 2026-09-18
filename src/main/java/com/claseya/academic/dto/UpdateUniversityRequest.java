package com.claseya.academic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUniversityRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Size(max = 100, message = "Short name must be at most 100 characters")
        String shortName,

        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Size(max = 100, message = "Province must be at most 100 characters")
        String province,

        @Size(max = 100, message = "Country must be at most 100 characters")
        String country
) {
}
