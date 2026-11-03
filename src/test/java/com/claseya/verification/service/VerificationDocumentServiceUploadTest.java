package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ServiceUnavailableException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.service.TeacherProfileService;
import com.claseya.user.UserRepository;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.claseya.model.enums.DocumentType;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REVIEW2-002 (RF-7 / AC-4): the scan and the object upload run outside the database transaction,
 * and the quota is re-checked inside the short persistence transaction. A failed store must leave
 * no row, and a quota filled between the two checks must not squeeze a sixth document in.
 */
@ExtendWith(MockitoExtension.class)
class VerificationDocumentServiceUploadTest {

    private static final byte[] PDF = "%PDF-1.4\nupload test\n".getBytes();

    @Mock private TeacherProfileService teacherProfileService;
    @Mock private TeacherEducationRepository teacherEducationRepository;
    @Mock private VerificationDocumentRepository documentRepository;
    @Mock private VerificationDecisionRepository decisionRepository;
    @Mock private DocumentContentValidator validator;
    @Mock private DocumentStorage storage;
    @Mock private DocumentScanner scanner;
    @Mock private UserRepository userRepository;

    private VerificationDocumentService service;
    private final UUID userId = UUID.randomUUID();
    private UUID credentialId;

    @BeforeEach
    void setUp() {
        service = new VerificationDocumentService(teacherProfileService, teacherEducationRepository,
                documentRepository, decisionRepository, validator, storage, scanner, userRepository,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));

        TeacherProfile profile = new TeacherProfile();
        profile.setId(UUID.randomUUID());

        TeacherEducation credential = new TeacherEducation();
        credential.setId(UUID.randomUUID());
        credential.setTeacher(profile);
        credentialId = credential.getId();

        when(teacherProfileService.requireByUser(userId)).thenReturn(profile);
        when(teacherEducationRepository.findByIdAndTeacher_Id(credentialId, profile.getId()))
                .thenReturn(Optional.of(credential));
        lenient().when(teacherEducationRepository.findOwnedForUpdate(credentialId, profile.getId()))
                .thenReturn(Optional.of(credential));
        when(validator.validate(any(), any())).thenReturn(
                new DocumentContentValidator.ValidatedDocument("application/pdf", "titulo.pdf", "abc", PDF.length));
    }

    @Test
    void storageFailure_answers503AndPersistsNothing() {
        when(storage.store(any(), any())).thenThrow(new ServiceUnavailableException("Storage is down"));

        assertThatThrownBy(() -> service.upload(userId, credentialId, DocumentType.DIPLOMA, PDF, "titulo.pdf"))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(documentRepository, never()).saveAndFlush(any());
        verify(storage, never()).delete(any());
    }

    @Test
    void quotaFilledBeforePersisting_rollsBackAndDropsTheOrphanObject() {
        when(documentRepository.countByTeacherEducation_Id(credentialId)).thenReturn(4L, 5L);
        when(documentRepository.sumSizeBytesByEducation(credentialId)).thenReturn(0L);
        when(storage.store(any(), any())).thenReturn("verification/orphan.pdf");

        assertThatThrownBy(() -> service.upload(userId, credentialId, DocumentType.DIPLOMA, PDF, "titulo.pdf"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("5 documents");

        verify(documentRepository, never()).saveAndFlush(any());
        verify(storage).delete(any());
    }

    @Test
    void totalSizeExceeded_answers400BeforeTouchingTheBucket() {
        when(documentRepository.countByTeacherEducation_Id(credentialId)).thenReturn(0L);
        when(documentRepository.sumSizeBytesByEducation(credentialId)).thenReturn(25L * 1024 * 1024);

        assertThatThrownBy(() -> service.upload(userId, credentialId, DocumentType.DIPLOMA, PDF, "titulo.pdf"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("25 MB");

        verify(storage, never()).store(any(), any());
        verify(documentRepository, never()).saveAndFlush(any());
    }
}
