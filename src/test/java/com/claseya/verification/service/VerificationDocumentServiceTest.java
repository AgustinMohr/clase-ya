package com.claseya.verification.service;

import com.claseya.common.exception.ConflictException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.VerificationDocument;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.service.TeacherProfileService;
import com.claseya.user.UserRepository;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Evidence immutability (RF-18/RF-21): a document that was ever presented for review, or that
 * belongs to a credential with an audit decision, can never be deleted. Only evidence of a
 * credential that was never submitted may be removed.
 */
@ExtendWith(MockitoExtension.class)
class VerificationDocumentServiceTest {

    @Mock private TeacherProfileService teacherProfileService;
    @Mock private TeacherEducationRepository teacherEducationRepository;
    @Mock private VerificationDocumentRepository documentRepository;
    @Mock private VerificationDecisionRepository decisionRepository;
    @Mock private DocumentContentValidator validator;
    @Mock private DocumentStorage storage;
    @Mock private UserRepository userRepository;
    @InjectMocks private VerificationDocumentService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private TeacherProfile profile;
    private TeacherEducation credential;
    private VerificationDocument document;

    @BeforeEach
    void setUp() {
        profile = new TeacherProfile();
        profile.setId(UUID.randomUUID());

        credential = new TeacherEducation();
        credential.setId(UUID.randomUUID());
        credential.setTeacher(profile);
        credential.setVerificationStatus(VerificationStatus.PENDING);

        document = new VerificationDocument();
        document.setId(documentId);
        document.setTeacher(profile);
        document.setTeacherEducation(credential);
    }

    @Test
    void delete_whenCredentialWasSubmitted_conflictsAndKeepsDocument() {
        givenOwnedDocument();
        credential.setSubmittedAt(Instant.now());
        credential.setVerificationStatus(VerificationStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> service.delete(userId, documentId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("evidence");

        verify(documentRepository, never()).delete(any());
    }

    @Test
    void delete_whenCredentialHasAnAuditDecision_conflictsAndKeepsDocument() {
        givenOwnedDocument();
        when(decisionRepository.existsByTeacherEducation_Id(credential.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(userId, documentId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("evidence");

        verify(documentRepository, never()).delete(any());
    }

    @Test
    void delete_whenCredentialIsVerified_conflictsEvenWithoutSubmissionTimestamp() {
        givenOwnedDocument();
        credential.setVerificationStatus(VerificationStatus.VERIFIED);

        assertThatThrownBy(() -> service.delete(userId, documentId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("evidence");

        verify(documentRepository, never()).delete(any());
    }

    @Test
    void delete_beforeSubmission_removesDocument() {
        givenOwnedDocument();

        service.delete(userId, documentId);

        verify(documentRepository).delete(document);
    }

    private void givenOwnedDocument() {
        when(teacherProfileService.requireByUser(userId)).thenReturn(profile);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    }
}
