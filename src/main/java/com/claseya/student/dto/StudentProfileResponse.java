package com.claseya.student.dto;

import com.claseya.model.StudentProfile;

import java.time.Instant;
import java.util.UUID;

public record StudentProfileResponse(
        UUID id,
        UUID userId,
        String email,
        UUID universityId,
        String universityName,
        UUID careerId,
        String careerName,
        Integer currentYear,
        String bio,
        Instant createdAt,
        Instant updatedAt
) {

    public static StudentProfileResponse from(StudentProfile profile) {
        return new StudentProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getEmail(),
                profile.getUniversity().getId(),
                profile.getUniversity().getName(),
                profile.getCareer().getId(),
                profile.getCareer().getName(),
                profile.getCurrentYear(),
                profile.getBio(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
