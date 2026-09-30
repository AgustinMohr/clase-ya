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
        long documentCount
) {

    public static CredentialVerificationView of(TeacherEducation education, long documentCount) {
        return new CredentialVerificationView(
                education.getId(),
                education.getInstitution(),
                education.getDegree(),
                education.getVerificationStatus(),
                education.getSubmittedAt(),
                documentCount);
    }
}
