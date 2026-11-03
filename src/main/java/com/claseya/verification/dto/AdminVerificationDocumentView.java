package com.claseya.verification.dto;

import com.claseya.model.VerificationDocument;
import com.claseya.model.enums.DocumentScanStatus;
import com.claseya.model.enums.DocumentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-facing document metadata (VERIF-001). It adds the internal review data that must never
 * reach the teacher: the real scan status and who marked the document as reviewed.
 */
public record AdminVerificationDocumentView(
        UUID id,
        UUID educationId,
        DocumentType type,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant uploadedAt,
        DocumentScanStatus scanStatus,
        Instant reviewedAt,
        String reviewedByName
) {

    public static AdminVerificationDocumentView from(VerificationDocument document) {
        return new AdminVerificationDocumentView(
                document.getId(),
                document.getTeacherEducation().getId(),
                document.getType(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes() == null ? 0L : document.getSizeBytes(),
                document.getUploadedAt(),
                document.getScanStatus(),
                document.getReviewedAt(),
                document.getReviewedBy() == null ? null : document.getReviewedBy().getName());
    }
}
