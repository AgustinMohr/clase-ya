package com.claseya.verification.repository;

import com.claseya.model.VerificationDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VerificationDecisionRepository extends JpaRepository<VerificationDecision, UUID> {
}
