package com.claseya.verification.dto;

import com.claseya.model.VerificationDecision;
import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationMethod;
import com.claseya.model.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-facing audit entry (§9): unlike {@link VerificationDecisionView} it also exposes the actor,
 * the document and the previous/new status. Never returned to a teacher.
 */
public record VerificationAuditEntry(
        UUID id,
        UUID educationId,
        UUID documentId,
        VerificationAction decision,
        VerificationStatus previousStatus,
        VerificationStatus newStatus,
        VerificationMethod method,
        String reason,
        String reviewerName,
        Instant decidedAt
) {

    public static VerificationAuditEntry from(VerificationDecision decision) {
        return new VerificationAuditEntry(
                decision.getId(),
                decision.getTeacherEducation() == null ? null : decision.getTeacherEducation().getId(),
                decision.getDocument() == null ? null : decision.getDocument().getId(),
                decision.getDecision(),
                decision.getPreviousStatus(),
                decision.getNewStatus(),
                decision.getMethod(),
                decision.getReason(),
                decision.getAdminUser().getName(),
                decision.getDecidedAt());
    }
}
