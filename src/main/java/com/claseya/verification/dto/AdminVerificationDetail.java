package com.claseya.verification.dto;

import com.claseya.model.enums.VerificationStatus;

import java.util.List;
import java.util.UUID;

/** Admin detail for one teacher: declared ad, credentials, documents and the full audit history. */
public record AdminVerificationDetail(
        UUID teacherId,
        String teacherName,
        VerificationStatus profileStatus,
        List<CredentialVerificationView> credentials,
        List<VerificationDocumentResponse> documents,
        List<VerificationAuditEntry> history
) {
}
