package com.claseya;

import com.claseya.common.exception.ConflictException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.DocumentType;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationMethod;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import com.claseya.verification.service.VerificationDecisionService;
import com.claseya.verification.service.VerificationDocumentService;
import com.claseya.verification.service.VerificationSubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-25: two admins deciding at the same time over the same credential must yield exactly one
 * applied decision and one 409 — never "last write wins". Deliberately non-transactional so each
 * worker thread gets its own real transaction and the profile write lock actually contends.
 */
class VerificationConcurrencyIntegrationTest extends AbstractWebIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\nconcurrency\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private TeacherProfileRepository teacherProfileRepository;
    @Autowired private TeacherEducationRepository teacherEducationRepository;
    @Autowired private VerificationDocumentRepository documentRepository;
    @Autowired private VerificationDecisionRepository decisionRepository;
    @Autowired private VerificationDocumentService documentService;
    @Autowired private VerificationSubmissionService submissionService;
    @Autowired private VerificationDecisionService decisionService;

    private User teacher;
    private User adminA;
    private User adminB;
    private TeacherProfile profile;
    private TeacherEducation education;

    @Test
    void twoConcurrentDecisions_oneWinsAndTheOtherConflicts() throws Exception {
        seedUnderReviewCredential();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(decide(start, adminA.getId(),
                    VerificationAction.VERIFIED, VerificationMethod.INSTITUTION_CHECK, "ok"));
            Future<String> second = pool.submit(decide(start, adminB.getId(),
                    VerificationAction.REJECTED, VerificationMethod.DOCUMENT_ANALYSIS, "no coincide"));
            start.countDown();

            List<String> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "CONFLICT");
        } finally {
            pool.shutdownNow();
        }

        // Exactly one decision was applied and audited; the credential matches the winner.
        assertThat(decisionRepository.findByTeacher_IdOrderByDecidedAtDesc(profile.getId())).hasSize(1);
        VerificationStatus finalStatus = teacherEducationRepository.findById(education.getId())
                .orElseThrow().getVerificationStatus();
        assertThat(finalStatus).isIn(VerificationStatus.VERIFIED, VerificationStatus.REJECTED);
        assertThat(teacherProfileRepository.findById(profile.getId()).orElseThrow().getVerificationStatus())
                .isEqualTo(finalStatus);
    }

    private Callable<String> decide(CountDownLatch start, UUID adminId,
                                    VerificationAction action, VerificationMethod method, String reason) {
        return () -> {
            start.await();
            try {
                decisionService.decide(adminId, profile.getId(), education.getId(), action, method, reason);
                return "OK";
            } catch (ConflictException e) {
                return "CONFLICT";
            }
        };
    }

    private void seedUnderReviewCredential() {
        teacher = createUser("conc-teacher+" + UUID.randomUUID() + "@test.com", UserRole.TEACHER, UserStatus.ACTIVE);
        adminA = createUser("conc-admin-a+" + UUID.randomUUID() + "@test.com", UserRole.ADMIN, UserStatus.ACTIVE);
        adminB = createUser("conc-admin-b+" + UUID.randomUUID() + "@test.com", UserRole.ADMIN, UserStatus.ACTIVE);

        profile = new TeacherProfile();
        profile.setUser(teacher);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        teacherProfileRepository.saveAndFlush(profile);

        education = new TeacherEducation();
        education.setTeacher(profile);
        education.setInstitution("UTN");
        education.setDegree("Ingeniero en Sistemas");
        education.setStartYear(2005);
        education.setEndYear(2010);
        teacherEducationRepository.saveAndFlush(education);

        documentService.upload(teacher.getId(), education.getId(), DocumentType.DIPLOMA, PDF, "diploma.pdf");
        submissionService.submit(teacher.getId(), education.getId());
    }

    @AfterEach
    void cleanup() {
        if (profile != null) {
            decisionRepository.deleteAll(decisionRepository.findByTeacher_IdOrderByDecidedAtDesc(profile.getId()));
            for (TeacherEducation credential : teacherEducationRepository.findByTeacher_IdOrderByStartYearDesc(profile.getId())) {
                documentRepository.deleteAll(
                        documentRepository.findByTeacherEducation_IdOrderByUploadedAtDesc(credential.getId()));
            }
            teacherEducationRepository.deleteAll(
                    teacherEducationRepository.findByTeacher_IdOrderByStartYearDesc(profile.getId()));
            teacherProfileRepository.deleteById(profile.getId());
        }
        if (teacher != null) userRepository.deleteById(teacher.getId());
        if (adminA != null) userRepository.deleteById(adminA.getId());
        if (adminB != null) userRepository.deleteById(adminB.getId());
    }
}
