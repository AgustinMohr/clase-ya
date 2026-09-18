package com.claseya.teacher.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddTeacherSubjectRequest(
        @NotNull(message = "careerSubjectId is required")
        UUID careerSubjectId,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Min(value = 0, message = "yearsExperience must be 0 or greater")
        Integer yearsExperience
) {
}
