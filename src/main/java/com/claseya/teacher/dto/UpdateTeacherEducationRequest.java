package com.claseya.teacher.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTeacherEducationRequest(
        @NotBlank(message = "Institution is required")
        @Size(max = 255, message = "Institution must be at most 255 characters")
        String institution,

        @NotBlank(message = "Degree is required")
        @Size(max = 255, message = "Degree must be at most 255 characters")
        String degree,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "startYear is required")
        @Min(value = 1900, message = "startYear must be 1900 or later")
        Integer startYear,

        @Min(value = 1900, message = "endYear must be 1900 or later")
        Integer endYear
) {
}
