package com.claseya.verification.service;

import com.claseya.model.VerificationDocument;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.springframework.stereotype.Component;

/** Stores evidence bytes in PostgreSQL via the {@code verification_documents.content bytea} column. */
@Component
public class PostgresDocumentStorage implements DocumentStorage {

    private final VerificationDocumentRepository repository;

    public PostgresDocumentStorage(VerificationDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public VerificationDocument store(VerificationDocument document) {
        return repository.saveAndFlush(document);
    }
}
