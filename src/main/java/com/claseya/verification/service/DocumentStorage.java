package com.claseya.verification.service;

import com.claseya.model.VerificationDocument;

/**
 * Storage abstraction for private verification evidence (decision D5). The MVP implementation keeps
 * the bytes in PostgreSQL ({@code bytea}); moving to private object storage later must not change
 * this contract.
 */
public interface DocumentStorage {

    VerificationDocument store(VerificationDocument document);
}
