package com.claseya.verification.dto;

import com.claseya.model.VerificationDecision;
import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationMethod;
import com.claseya.model.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the append-only audit trail. Deliberately omits the reviewer's identity so the same
 * view can be shown to the teacher without exposing internal review data (§9).
 */
public record VerificationDecisionView(
        UUID id,
        UUID educationId,
        VerificationAction decision,
        VerificationStatus previousStatus,
        VerificationStatus newStatus,
        VerificationMethod method,
        String reason,
        Instant decidedAt
) {

    public static VerificationDecisionView from(VerificationDecision decision) {
        return new VerificationDecisionView(
                decision.getId(),
                decision.getTeacherEducation() == null ? null : decision.getTeacherEducation().getId(),
                decision.getDecision(),
                decision.getPreviousStatus(),
                decision.getNewStatus(),
                decision.getMethod(),
                decision.getReason(),
                decision.getDecidedAt());
    }
}
