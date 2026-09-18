package com.claseya.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateStudentProfileRequest(
        @NotNull(message = "universityId is required")
        UUID universityId,

        @NotNull(message = "careerId is required")
        UUID careerId,

        @NotNull(message = "currentYear is required")
        @Min(value = 1, message = "currentYear must be between 1 and 12")
        @Max(value = 12, message = "currentYear must be between 1 and 12")
        Integer currentYear,

        @Size(max = 1000, message = "Bio must be at most 1000 characters")
        String bio
) {
}
