package com.claseya.student.dto;

import com.claseya.model.enums.StudentLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateStudentProfileRequest(
        @Size(max = 255, message = "Name is too long")
        String name,

        @NotNull(message = "level is required")
        StudentLevel level,

        @Size(max = 100, message = "City is too long")
        String city,

        /** Optional academic data; when present, universityId and careerId go together. */
        UUID universityId,

        UUID careerId,

        @Min(value = 1, message = "currentYear must be between 1 and 12")
        @Max(value = 12, message = "currentYear must be between 1 and 12")
        Integer currentYear,

        @Size(max = 1000, message = "Bio must be at most 1000 characters")
        String bio
) {
}
