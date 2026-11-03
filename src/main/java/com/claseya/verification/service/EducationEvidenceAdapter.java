package com.claseya.verification.service;

import com.claseya.teacher.port.EducationEvidencePort;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Adapter for the teacher-owned {@link EducationEvidencePort}. */
@Component
public class EducationEvidenceAdapter implements EducationEvidencePort {

    private final VerificationDocumentRepository documentRepository;
    private final VerificationDecisionRepository decisionRepository;

    public EducationEvidenceAdapter(VerificationDocumentRepository documentRepository,
                                    VerificationDecisionRepository decisionRepository) {
        this.documentRepository = documentRepository;
        this.decisionRepository = decisionRepository;
    }

    @Override
    public boolean hasEvidence(UUID educationId) {
        return documentRepository.countByTeacherEducation_Id(educationId) > 0;
    }

    @Override
    public boolean hasDecisions(UUID educationId) {
        return decisionRepository.existsByTeacherEducation_Id(educationId);
    }
}
