package com.claseya.verification.dto;

import com.claseya.model.enums.VerificationStatus;

import java.util.List;

/** The teacher's own verification view: aggregate profile status plus every credential. */
public record TeacherVerificationView(
        VerificationStatus profileStatus,
        List<CredentialVerificationView> credentials
) {
}
