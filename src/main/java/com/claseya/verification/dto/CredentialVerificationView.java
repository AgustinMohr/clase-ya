package com.claseya.verification.dto;

import com.claseya.model.TeacherEducation;
import com.claseya.model.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/** The verification state of a single credential (teacher_education row) plus its document count. */
public record CredentialVerificationView(
        UUID educationId,
        String institution,
        String degree,
        VerificationStatus status,
        Instant submittedAt,
        long documentCount,
        String requirement
) {

    public static CredentialVerificationView of(TeacherEducation education, long documentCount) {
        return of(education, documentCount, null);
    }

    /**
     * {@code requirement} is the reason the admin asked for more information (RF-16); it is only
     * populated when the credential is {@link VerificationStatus#MORE_INFO_REQUIRED}.
     */
    public static CredentialVerificationView of(TeacherEducation education, long documentCount, String requirement) {
        return new CredentialVerificationView(
                education.getId(),
                education.getInstitution(),
                education.getDegree(),
                education.getVerificationStatus(),
                education.getSubmittedAt(),
                documentCount,
                requirement);
    }
}
