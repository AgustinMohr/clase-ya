package com.claseya.verification.repository;

import com.claseya.model.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {
}
