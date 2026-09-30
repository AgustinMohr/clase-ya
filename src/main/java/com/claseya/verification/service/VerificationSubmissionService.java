package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.service.TeacherProfileService;
import com.claseya.verification.dto.CredentialVerificationView;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code submit} is the explicit action that puts a credential under review (RF-10, §8):
 *
 * <ul>
 *   <li>Eligible = at least one document and status ≠ VERIFIED.</li>
 *   <li>PENDING / MORE_INFO_REQUIRED / REJECTED → UNDER_REVIEW (updates {@code submittedAt}).</li>
 *   <li>UNDER_REVIEW → 200 idempotent, keeps its place in the queue (does not touch submittedAt).</li>
 *   <li>VERIFIED → 200 idempotent, never taken back to review.</li>
 *   <li>No documents → 400.</li>
 * </ul>
 */
@Service
public class VerificationSubmissionService {

    private final TeacherProfileService teacherProfileService;
    private final TeacherEducationRepository teacherEducationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final TeacherVerificationService teacherVerificationService;

    public VerificationSubmissionService(TeacherProfileService teacherProfileService,
                                         TeacherEducationRepository teacherEducationRepository,
                                         VerificationDocumentRepository documentRepository,
                                         TeacherVerificationService teacherVerificationService) {
        this.teacherProfileService = teacherProfileService;
        this.teacherEducationRepository = teacherEducationRepository;
        this.documentRepository = documentRepository;
        this.teacherVerificationService = teacherVerificationService;
    }

    @Transactional
    public CredentialVerificationView submit(UUID userId, UUID educationId) {
        TeacherProfile profile = lockOwnProfile(userId);
        TeacherEducation education = teacherEducationRepository.findByIdAndTeacher_Id(educationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Teacher education not found"));
        return applySubmit(profile, education);
    }

    /** Presents every eligible credential (at least one document, not VERIFIED) in one action. */
    @Transactional
    public List<CredentialVerificationView> submitAll(UUID userId) {
        TeacherProfile profile = lockOwnProfile(userId);
        List<TeacherEducation> credentials = teacherEducationRepository.findByTeacher_IdOrderByStartYearDesc(profile.getId());
        List<CredentialVerificationView> submitted = new ArrayList<>();
        for (TeacherEducation education : credentials) {
            long documents = documentRepository.countByTeacherEducation_Id(education.getId());
            if (education.getVerificationStatus() == VerificationStatus.VERIFIED || documents == 0) {
                continue; // not eligible
            }
            submitted.add(applySubmit(profile, education));
        }
        if (submitted.isEmpty()) {
            throw new BadRequestException("There is nothing to submit");
        }
        return submitted;
    }

    private CredentialVerificationView applySubmit(TeacherProfile profile, TeacherEducation education) {
        VerificationStatus current = education.getVerificationStatus();
        if (current == VerificationStatus.VERIFIED || current == VerificationStatus.UNDER_REVIEW) {
            // Idempotent: never re-queue a waiting credential, never take a verified one back.
            return viewOf(education);
        }
        if (documentRepository.countByTeacherEducation_Id(education.getId()) == 0) {
            throw new BadRequestException("Cannot submit a credential without documents");
        }
        education.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
        education.setSubmittedAt(Instant.now());
        teacherEducationRepository.saveAndFlush(education);
        teacherVerificationService.recalculateProfileStatus(profile);
        return viewOf(education);
    }

    private TeacherProfile lockOwnProfile(UUID userId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        return teacherVerificationService.requireForUpdate(profile.getId());
    }

    private CredentialVerificationView viewOf(TeacherEducation education) {
        return CredentialVerificationView.of(education,
                documentRepository.countByTeacherEducation_Id(education.getId()));
    }
}
