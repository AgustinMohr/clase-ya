package com.claseya;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.PayloadTooLargeException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.DocumentType;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationMethod;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.dto.UpdateTeacherEducationRequest;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.service.TeacherEducationService;
import com.claseya.verification.dto.CredentialVerificationView;
import com.claseya.verification.dto.TeacherVerificationView;
import com.claseya.verification.dto.VerificationDecisionView;
import com.claseya.verification.dto.VerificationDocumentResponse;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import com.claseya.verification.service.DocumentContentValidator;
import com.claseya.verification.service.TeacherVerificationService;
import com.claseya.verification.service.VerificationDecisionService;
import com.claseya.verification.service.VerificationDocumentService;
import com.claseya.verification.service.VerificationSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-layer coverage for TEACHER-001 slice B: upload vs submit, the credential state machine,
 * admin decisions, revocation, evidence immutability, the append-only audit and profile recalc.
 */
@Transactional
class VerificationWorkflowIntegrationTest extends AbstractWebIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\ncontenido de prueba\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private TeacherProfileRepository teacherProfileRepository;
    @Autowired private TeacherEducationRepository teacherEducationRepository;
    @Autowired private VerificationDocumentRepository documentRepository;
    @Autowired private VerificationDecisionRepository decisionRepository;
    @Autowired private VerificationDocumentService documentService;
    @Autowired private VerificationSubmissionService submissionService;
    @Autowired private VerificationDecisionService decisionService;
    @Autowired private TeacherVerificationService teacherVerificationService;
    @Autowired private TeacherEducationService teacherEducationService;

    private User teacher;
    private User admin;
    private TeacherProfile profile;
    private TeacherEducation education;

    @BeforeEach
    void setUp() {
        teacher = createUser("teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        admin = createUser("admin+" + UUID.randomUUID() + "@test.com", UserRole.ADMIN, UserStatus.ACTIVE);
        profile = newProfileFor(teacher);
        education = newCredential(profile, "UTN", "Ingeniero en Sistemas", 2005, 2010);
    }

    // ─── upload vs submit (RF-9, RF-10, I3) ──────────────────────────────────────

    @Test
    void upload_doesNotChangeVerificationState() {
        VerificationDocumentResponse doc = uploadPdf(teacher.getId(), education.getId());

        assertThat(doc.contentType()).isEqualTo("application/pdf");
        assertThat(doc.originalFilename()).isEqualTo("diploma.pdf"); // path stripped by sanitizer
        assertThat(doc.sha256()).hasSize(64);
        assertThat(credentialStatus(education.getId())).isEqualTo(VerificationStatus.PENDING);
        assertThat(profileStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void upload_rejectsNonDocumentByRealContent() {
        byte[] executable = "MZ\u0090\u0000 not a document".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> documentService.upload(teacher.getId(), education.getId(),
                DocumentType.DIPLOMA, executable, "renamed.pdf"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_rejectsOversizedFile() {
        byte[] oversized = new byte[(int) DocumentContentValidator.MAX_SIZE_BYTES + 1];
        oversized[0] = 0x25; oversized[1] = 0x50; oversized[2] = 0x44; oversized[3] = 0x46; oversized[4] = 0x2D;
        assertThatThrownBy(() -> documentService.upload(teacher.getId(), education.getId(),
                DocumentType.DIPLOMA, oversized, "big.pdf"))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void replacingEvidence_addsANewDocumentWithoutOverwriting() {
        VerificationDocumentResponse first = uploadPdf(teacher.getId(), education.getId());
        VerificationDocumentResponse second = uploadPdf(teacher.getId(), education.getId());

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(documentRepository.countByTeacherEducation_Id(education.getId())).isEqualTo(2);
    }

    @Test
    void submit_withDocuments_putsTheCredentialUnderReview() {
        uploadPdf(teacher.getId(), education.getId());

        CredentialVerificationView view = submissionService.submit(teacher.getId(), education.getId());

        assertThat(view.status()).isEqualTo(VerificationStatus.UNDER_REVIEW);
        assertThat(view.submittedAt()).isNotNull();
        assertThat(profileStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
    }

    @Test
    void submit_withoutDocuments_isRejected() {
        assertThatThrownBy(() -> submissionService.submit(teacher.getId(), education.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void submit_whileUnderReview_isIdempotentAndKeepsItsQueuePosition() {
        uploadPdfAndSubmit(education.getId());
        Instant firstSubmittedAt = submittedAt(education.getId());

        CredentialVerificationView again = submissionService.submit(teacher.getId(), education.getId());

        assertThat(again.status()).isEqualTo(VerificationStatus.UNDER_REVIEW);
        assertThat(submittedAt(education.getId())).isEqualTo(firstSubmittedAt);
    }

    @Test
    void submit_onVerifiedCredential_neverReopensReview() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null);

        CredentialVerificationView view = submissionService.submit(teacher.getId(), education.getId());

        assertThat(view.status()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(profileStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void resubmitRejectedCredential_returnsToUnderReview() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.REJECTED, VerificationMethod.DOCUMENT_ANALYSIS, "ilegible");
        assertThat(credentialStatus(education.getId())).isEqualTo(VerificationStatus.REJECTED);

        CredentialVerificationView view = submissionService.submit(teacher.getId(), education.getId());

        assertThat(view.status()).isEqualTo(VerificationStatus.UNDER_REVIEW);
    }

    @Test
    void submitAll_presentsEveryEligibleCredentialOnly() {
        TeacherEducation second = newCredential(profile, "UNR", "Licenciado en Física", 2010, 2015);
        TeacherEducation noDocuments = newCredential(profile, "UNL", "Profesorado", 2015, 2019);
        uploadPdf(teacher.getId(), education.getId());
        uploadPdf(teacher.getId(), second.getId());

        List<CredentialVerificationView> submitted = submissionService.submitAll(teacher.getId());

        assertThat(submitted).hasSize(2);
        assertThat(credentialStatus(education.getId())).isEqualTo(VerificationStatus.UNDER_REVIEW);
        assertThat(credentialStatus(second.getId())).isEqualTo(VerificationStatus.UNDER_REVIEW);
        assertThat(credentialStatus(noDocuments.getId())).isEqualTo(VerificationStatus.PENDING);
        assertThat(profileStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
    }

    @Test
    void submitAll_withoutEligibleCredentials_isRejected() {
        assertThatThrownBy(() -> submissionService.submitAll(teacher.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    // ─── decisions, audit and aggregate (RF-14, RF-16, RF-17, I4) ───────────────

    @Test
    void decideVerified_verifiesCredentialProfileAndWritesAudit() {
        uploadPdfAndSubmit(education.getId());

        VerificationDecisionView decision = decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, "constancia institucional");

        assertThat(decision.previousStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
        assertThat(decision.newStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(credentialStatus(education.getId())).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(profileStatus()).isEqualTo(VerificationStatus.VERIFIED);

        List<VerificationDecisionView> history = decisionService.history(profile.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).decision()).isEqualTo(VerificationAction.VERIFIED);
        assertThat(history.get(0).method()).isEqualTo(VerificationMethod.INSTITUTION_CHECK);
    }

    @Test
    void verifiedPlusRejected_keepsTheProfileVerified() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null);

        TeacherEducation second = newCredential(profile, "UNR", "Licenciado", 2010, 2015);
        uploadPdf(teacher.getId(), second.getId());
        submissionService.submit(teacher.getId(), second.getId());
        decisionService.decide(admin.getId(), profile.getId(), second.getId(),
                VerificationAction.REJECTED, VerificationMethod.DOCUMENT_ANALYSIS, "no coincide");

        assertThat(credentialStatus(second.getId())).isEqualTo(VerificationStatus.REJECTED);
        assertThat(profileStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void moreInfoRequired_requiresAReason() {
        uploadPdfAndSubmit(education.getId());

        assertThatThrownBy(() -> decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.MORE_INFO_REQUIRED, VerificationMethod.DOCUMENT_ANALYSIS, "   "))
                .isInstanceOf(BadRequestException.class);
        assertThat(credentialStatus(education.getId())).isEqualTo(VerificationStatus.UNDER_REVIEW);
    }

    @Test
    void teacherView_exposesTheMoreInfoRequirementAndHistory() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.MORE_INFO_REQUIRED, VerificationMethod.DOCUMENT_ANALYSIS,
                "subí el analítico completo");

        TeacherVerificationView view = teacherVerificationService.viewForUser(teacher.getId());

        assertThat(view.profileStatus()).isEqualTo(VerificationStatus.MORE_INFO_REQUIRED);
        assertThat(view.credentials()).hasSize(1);
        assertThat(view.credentials().get(0).requirement()).isEqualTo("subí el analítico completo");
        assertThat(view.history()).hasSize(1);
        assertThat(view.history().get(0).decision()).isEqualTo(VerificationAction.MORE_INFO_REQUIRED);
        assertThat(view.history().get(0).reason()).isEqualTo("subí el analítico completo");
    }

    @Test
    void decide_onCredentialNotUnderReview_conflicts() {
        // A PENDING credential has never been submitted; a decision over it is a 409.
        assertThatThrownBy(() -> decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null))
                .isInstanceOf(ConflictException.class);
        assertThat(credentialStatus(education.getId())).isEqualTo(VerificationStatus.PENDING);
        assertThat(decisionRepository.findByTeacher_IdOrderByDecidedAtDesc(profile.getId())).isEmpty();
    }

    @Test
    void admin_cannotDecideOnItsOwnAccount() {
        User selfAdmin = createUser("self+" + UUID.randomUUID() + "@test.com", UserRole.ADMIN, UserStatus.ACTIVE);
        TeacherProfile selfProfile = newProfileFor(selfAdmin);
        TeacherEducation selfCredential = newCredential(selfProfile, "UTN", "Ingeniero", 2005, 2010);
        documentService.upload(selfAdmin.getId(), selfCredential.getId(), DocumentType.DIPLOMA, PDF, "d.pdf");
        submissionService.submit(selfAdmin.getId(), selfCredential.getId());

        assertThatThrownBy(() -> decisionService.decide(selfAdmin.getId(), selfProfile.getId(), selfCredential.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null))
                .isInstanceOf(ConflictException.class);
    }

    // ─── revocation (RF-25, CA-8) ───────────────────────────────────────────────

    @Test
    void revoke_lastVerifiedCredential_recalculatesProfileAndAudits() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null);

        VerificationDecisionView revoke = decisionService.revoke(admin.getId(), profile.getId(),
                VerificationMethod.OFFICIAL_REGISTRY, "documento apócrifo");

        assertThat(revoke.decision()).isEqualTo(VerificationAction.REVOKED);
        assertThat(revoke.previousStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(revoke.newStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(credentialStatus(education.getId())).isEqualTo(VerificationStatus.REJECTED);
        assertThat(profileStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(decisionService.history(profile.getId())).hasSize(2);
    }

    @Test
    void revoke_withoutVerifiedCredential_conflicts() {
        assertThatThrownBy(() -> decisionService.revoke(admin.getId(), profile.getId(),
                VerificationMethod.OFFICIAL_REGISTRY, "motivo"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void revoke_requiresAReason() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null);

        assertThatThrownBy(() -> decisionService.revoke(admin.getId(), profile.getId(),
                VerificationMethod.OFFICIAL_REGISTRY, "  "))
                .isInstanceOf(BadRequestException.class);
        assertThat(profileStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    // ─── immutability of evidence (RF-19, RF-20, RF-21) ─────────────────────────

    @Test
    void verifiedCredential_materialFieldsAreFrozenButDescriptionIsEditable() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null);

        teacherEducationService.update(teacher.getId(), education.getId(),
                new UpdateTeacherEducationRequest("UTN", "Ingeniero en Sistemas", "nueva descripción", 2005, 2010));
        assertThat(teacherEducationRepository.findById(education.getId()).orElseThrow().getDescription())
                .isEqualTo("nueva descripción");

        assertThatThrownBy(() -> teacherEducationService.update(teacher.getId(), education.getId(),
                new UpdateTeacherEducationRequest("Otra Universidad", "Ingeniero en Sistemas", null, 2005, 2010)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void verifiedCredential_cannotBeDeleted() {
        uploadPdfAndSubmit(education.getId());
        decisionService.decide(admin.getId(), profile.getId(), education.getId(),
                VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, null);

        assertThatThrownBy(() -> teacherEducationService.delete(teacher.getId(), education.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void credentialWithDocumentsOrDecisions_cannotBeDeleted() {
        uploadPdf(teacher.getId(), education.getId());
        assertThatThrownBy(() -> teacherEducationService.delete(teacher.getId(), education.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void neverSubmittedCredentialWithoutEvidence_canBeDeleted() {
        TeacherEducation clean = newCredential(profile, "UNL", "Profesorado", 2015, 2019);

        teacherEducationService.delete(teacher.getId(), clean.getId());

        assertThat(teacherEducationRepository.findById(clean.getId())).isEmpty();
    }

    // ─── privacy and reads ──────────────────────────────────────────────────────

    @Test
    void anotherTeacher_cannotReadTheDocument() {
        VerificationDocumentResponse doc = uploadPdf(teacher.getId(), education.getId());
        User other = createUser("other+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        newProfileFor(other);

        assertThatThrownBy(() -> documentService.contentForOwner(other.getId(), doc.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void teacherView_exposesProfileStatusAndCredentialStates() {
        uploadPdfAndSubmit(education.getId());

        TeacherVerificationView view = teacherVerificationService.viewForUser(teacher.getId());

        assertThat(view.profileStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
        assertThat(view.credentials()).hasSize(1);
        assertThat(view.credentials().get(0).documentCount()).isEqualTo(1);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private TeacherProfile newProfileFor(User owner) {
        TeacherProfile created = new TeacherProfile();
        created.setUser(owner);
        created.setVerificationStatus(VerificationStatus.PENDING);
        return teacherProfileRepository.saveAndFlush(created);
    }

    private TeacherEducation newCredential(TeacherProfile owner, String institution, String degree, int start, int end) {
        TeacherEducation created = new TeacherEducation();
        created.setTeacher(owner);
        created.setInstitution(institution);
        created.setDegree(degree);
        created.setStartYear(start);
        created.setEndYear(end);
        return teacherEducationRepository.saveAndFlush(created);
    }

    private VerificationDocumentResponse uploadPdf(UUID userId, UUID educationId) {
        return documentService.upload(userId, educationId, DocumentType.DIPLOMA, PDF, "C:\\ruta\\diploma.pdf");
    }

    private void uploadPdfAndSubmit(UUID educationId) {
        uploadPdf(teacher.getId(), educationId);
        submissionService.submit(teacher.getId(), educationId);
    }

    private VerificationStatus profileStatus() {
        return teacherProfileRepository.findById(profile.getId()).orElseThrow().getVerificationStatus();
    }

    private VerificationStatus credentialStatus(UUID educationId) {
        return teacherEducationRepository.findById(educationId).orElseThrow().getVerificationStatus();
    }

    private Instant submittedAt(UUID educationId) {
        return teacherEducationRepository.findById(educationId).orElseThrow().getSubmittedAt();
    }
}
