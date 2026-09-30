package com.claseya.verification.repository;

import com.claseya.model.VerificationDecision;
import com.claseya.model.enums.VerificationAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationDecisionRepository extends JpaRepository<VerificationDecision, UUID> {

    /** Evidence immutability: a document referenced by any decision can never be deleted. */
    boolean existsByDocument_Id(UUID documentId);

    /** RF-21: a credential with any decision keeps its history and can never be deleted. */
    boolean existsByTeacherEducation_Id(UUID teacherEducationId);

    List<VerificationDecision> findByTeacher_IdOrderByDecidedAtDesc(UUID teacherId);

    Optional<VerificationDecision> findFirstByTeacher_IdAndDecisionOrderByDecidedAtDesc(
            UUID teacherId, VerificationAction decision);
}
