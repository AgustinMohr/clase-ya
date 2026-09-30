package com.claseya.verification.dto;

import com.claseya.model.TeacherEducation;
import com.claseya.model.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/** One row of the admin review queue: a credential waiting for (or already given) a decision. */
public record VerificationQueueItem(
        UUID educationId,
        UUID teacherId,
        String teacherName,
        String institution,
        String degree,
        VerificationStatus status,
        Instant submittedAt,
        long documentCount
) {

    public static VerificationQueueItem of(TeacherEducation education, String teacherName, long documentCount) {
        return new VerificationQueueItem(
                education.getId(),
                education.getTeacher().getId(),
                teacherName,
                education.getInstitution(),
                education.getDegree(),
                education.getVerificationStatus(),
                education.getSubmittedAt(),
                documentCount);
    }
}
