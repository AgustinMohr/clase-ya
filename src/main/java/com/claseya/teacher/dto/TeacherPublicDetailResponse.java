package com.claseya.teacher.dto;

import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.VerificationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public teacher profile detail. Privacy-safe: no email, no address, no exact
 * coordinates. Education items are the public teacher-declared history.
 */
public record TeacherPublicDetailResponse(
        UUID id,
        String displayName,
        String bio,
        VerificationStatus verificationStatus,
        BigDecimal ratingAverage,
        Integer ratingCount,
        BigDecimal pricePerHour,
        String city,
        String photoUrl,
        String availabilityNote,
        List<String> modalities,
        List<TeacherSubjectView> subjects,
        List<TeacherEducationResponse> education
) {

    public static TeacherPublicDetailResponse of(TeacherProfile profile, String displayName,
                                                 List<String> modalities,
                                                 List<TeacherSubjectView> subjects,
                                                 List<TeacherEducationResponse> education) {
        return new TeacherPublicDetailResponse(
                profile.getId(),
                displayName,
                profile.getBio(),
                profile.getVerificationStatus(),
                profile.getRatingAverage(),
                profile.getRatingCount(),
                profile.getPricePerHour(),
                profile.getCity(),
                profile.getPhotoUrl(),
                profile.getAvailabilityNote(),
                modalities,
                subjects,
                education);
    }
}
