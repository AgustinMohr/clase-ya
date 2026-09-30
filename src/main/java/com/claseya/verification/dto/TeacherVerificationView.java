package com.claseya.verification.dto;

import com.claseya.model.enums.VerificationStatus;

import java.util.List;

/**
 * The teacher's own verification view: aggregate profile status, every credential (with the current
 * requirement when more info is needed) and the decision history (newest first, reviewer hidden).
 */
public record TeacherVerificationView(
        VerificationStatus profileStatus,
        List<CredentialVerificationView> credentials,
        List<VerificationDecisionView> history
) {
}
