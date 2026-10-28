package com.claseya.verification.service;

import com.claseya.model.VerificationDocument;

/**
 * Storage abstraction for private verification evidence. The PostgreSQL implementation keeps the
 * bytes in {@code verification_documents.content}; the S3 implementation uploads them to a private
 * bucket and stores the key in {@code storage_key} (STOR-01). The service owns the entity row.
 */
public interface DocumentStorage {

    /** Stores the bytes and returns the storage key (null when the content stays in the row). */
    String store(byte[] content, String contentType);

    /** Loads the evidence; legacy rows have no key and keep their bytes in {@code content}. */
    byte[] load(VerificationDocument document);

    /** Deletes the external object, if any. */
    void delete(VerificationDocument document);
}
