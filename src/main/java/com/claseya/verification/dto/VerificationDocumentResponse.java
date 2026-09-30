package com.claseya.verification.dto;

import com.claseya.model.VerificationDocument;
import com.claseya.model.enums.DocumentType;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a stored document. Never carries the binary (I7). */
public record VerificationDocumentResponse(
        UUID id,
        UUID educationId,
        DocumentType type,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant uploadedAt
) {

    public static VerificationDocumentResponse from(VerificationDocument document) {
        return new VerificationDocumentResponse(
                document.getId(),
                document.getTeacherEducation().getId(),
                document.getType(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getSizeBytes() == null ? 0L : document.getSizeBytes(),
                document.getSha256(),
                document.getUploadedAt());
    }
}
