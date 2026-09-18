package com.claseya.teacher.dto;

import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.VerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeacherProfileResponse(
        UUID id,
        UUID userId,
        String email,
        String bio,
        VerificationStatus verificationStatus,
        BigDecimal latitude,
        BigDecimal longitude,
        String address,
        BigDecimal ratingAverage,
        Integer ratingCount,
        List<String> modalities,
        Instant createdAt,
        Instant updatedAt
) {

    public static TeacherProfileResponse from(TeacherProfile profile, List<String> modalities) {
        return new TeacherProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getEmail(),
                profile.getBio(),
                profile.getVerificationStatus(),
                profile.getLatitude(),
                profile.getLongitude(),
                profile.getAddress(),
                profile.getRatingAverage(),
                profile.getRatingCount(),
                modalities,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
