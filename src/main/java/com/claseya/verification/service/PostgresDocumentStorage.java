package com.claseya.verification.service;

import com.claseya.model.VerificationDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default (local/test) storage: the bytes stay in the {@code verification_documents.content} column. */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "postgres", matchIfMissing = true)
public class PostgresDocumentStorage implements DocumentStorage {

    @Override
    public String store(byte[] content, String contentType) {
        return null;
    }

    @Override
    public byte[] load(VerificationDocument document) {
        return document.getContent();
    }

    @Override
    public void delete(VerificationDocument document) {
        // The row deletion owns the lifecycle when the bytes live in PostgreSQL.
    }
}
