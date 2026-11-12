package com.claseya.teacher.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * PROFILE-004: exactly one target is required. {@code careerSubjectId} keeps the academic catalog
 * path; {@code subjectId} publishes a direct subject (school tutor, language, etc.).
 */
public record AddTeacherSubjectRequest(
        UUID careerSubjectId,

        UUID subjectId,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Min(value = 0, message = "yearsExperience must be 0 or greater")
        Integer yearsExperience
) {
}
