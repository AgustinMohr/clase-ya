package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.PayloadTooLargeException;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates an uploaded document by its <strong>real content</strong> (magic bytes), never by the
 * extension or the client-declared Content-Type (§6). Also enforces the size cap and derives the
 * sanitized filename and the SHA-256 of the bytes.
 */
@Component
public class DocumentContentValidator {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_FILENAME_LENGTH = 255;

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D};                      // %PDF-
    private static final byte[] JPG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};             // FF D8 FF
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}; // \x89PNG....

    public ValidatedDocument validate(byte[] content, String originalFilename) {
        if (content == null || content.length == 0) {
            throw new BadRequestException("Document is empty");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new PayloadTooLargeException("Document exceeds the 10 MB limit");
        }
        String contentType = detectContentType(content);
        if (contentType == null) {
            throw new BadRequestException("Unsupported document format: only PDF, JPG and PNG are accepted");
        }
        return new ValidatedDocument(contentType, sanitizeFilename(originalFilename), sha256Hex(content), content.length);
    }

    private String detectContentType(byte[] content) {
        if (startsWith(content, PDF_MAGIC)) {
            return "application/pdf";
        }
        if (startsWith(content, JPG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(content, PNG_MAGIC)) {
            return "image/png";
        }
        return null;
    }

    private boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Strips path components and control characters, bounds the length, and never returns blank. */
    private String sanitizeFilename(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            return "document";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
    }

    public record ValidatedDocument(String contentType, String filename, String sha256, long sizeBytes) {
    }
}
