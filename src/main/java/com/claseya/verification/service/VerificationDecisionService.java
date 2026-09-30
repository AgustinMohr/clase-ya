package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.VerificationDecision;
import com.claseya.model.VerificationDocument;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationMethod;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.user.UserRepository;
import com.claseya.verification.dto.VerificationDecisionView;
import com.claseya.verification.repository.VerificationDecisionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin decisions over a credential (RF-16/RF-17) and revocation (RF-25). Every transition is
 * applied over the state read under the profile write lock, so a decision that starts from a state
 * that is no longer current fails with 409 and never wins last-write. Each transition writes one
 * append-only audit row (RF-18/I4) and recalculates the aggregate profile status in the same
 * transaction (RF-15).
 */
@Service
public class VerificationDecisionService {

    private final TeacherEducationRepository teacherEducationRepository;
    private final VerificationDecisionRepository decisionRepository;
    private final TeacherVerificationService teacherVerificationService;
    private final UserRepository userRepository;

    public VerificationDecisionService(TeacherEducationRepository teacherEducationRepository,
                                       VerificationDecisionRepository decisionRepository,
                                       TeacherVerificationService teacherVerificationService,
                                       UserRepository userRepository) {
        this.teacherEducationRepository = teacherEducationRepository;
        this.decisionRepository = decisionRepository;
        this.teacherVerificationService = teacherVerificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public VerificationDecisionView decide(UUID adminUserId, UUID teacherProfileId, UUID educationId,
                                           VerificationAction action, VerificationMethod method, String reason) {
        User admin = requireAdmin(adminUserId);
        if (method == null) {
            throw new BadRequestException("A verification method is required");
        }
        if (action != VerificationAction.VERIFIED && action != VerificationAction.REJECTED
                && action != VerificationAction.MORE_INFO_REQUIRED) {
            throw new BadRequestException("Unsupported decision: " + action);
        }
        String trimmedReason = trimToNull(reason);
        if (action == VerificationAction.MORE_INFO_REQUIRED && trimmedReason == null) {
            throw new BadRequestException("A reason is required to request more information");
        }

        TeacherProfile profile = teacherVerificationService.requireForUpdate(teacherProfileId);
        requireNotOwnAccount(profile, adminUserId);

        TeacherEducation education = teacherEducationRepository.findByIdAndTeacher_Id(educationId, teacherProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher education not found"));

        // RF-25: the transition is applied over the read state; a stale state is a 409.
        if (education.getVerificationStatus() != VerificationStatus.UNDER_REVIEW) {
            throw new ConflictException("The credential is no longer under review");
        }
        VerificationStatus previous = education.getVerificationStatus();
        VerificationStatus next = statusFor(action);
        education.setVerificationStatus(next);
        teacherEducationRepository.saveAndFlush(education);
        teacherVerificationService.recalculateProfileStatus(profile);

        return VerificationDecisionView.from(record(profile, education, null, admin, previous, next, action, method, trimmedReason));
    }

    /** Revokes the last verified credential (VERIFIED → REJECTED); method and reason are mandatory. */
    @Transactional
    public VerificationDecisionView revoke(UUID adminUserId, UUID teacherProfileId, VerificationMethod method, String reason) {
        User admin = requireAdmin(adminUserId);
        if (method == null) {
            throw new BadRequestException("A verification method is required");
        }
        String trimmedReason = trimToNull(reason);
        if (trimmedReason == null) {
            throw new BadRequestException("A reason is required to revoke a verification");
        }

        TeacherProfile profile = teacherVerificationService.requireForUpdate(teacherProfileId);
        requireNotOwnAccount(profile, adminUserId);

        TeacherEducation target = findRevocationTarget(teacherProfileId);
        VerificationStatus previous = target.getVerificationStatus();
        target.setVerificationStatus(VerificationStatus.REJECTED);
        teacherEducationRepository.saveAndFlush(target);
        teacherVerificationService.recalculateProfileStatus(profile);

        return VerificationDecisionView.from(record(profile, target, null, admin, previous, VerificationStatus.REJECTED,
                VerificationAction.REVOKED, method, trimmedReason));
    }

    @Transactional(readOnly = true)
    public List<VerificationDecisionView> history(UUID teacherProfileId) {
        return decisionRepository.findByTeacher_IdOrderByDecidedAtDesc(teacherProfileId).stream()
                .map(VerificationDecisionView::from)
                .toList();
    }

    private TeacherEducation findRevocationTarget(UUID teacherProfileId) {
        return decisionRepository
                .findFirstByTeacher_IdAndDecisionOrderByDecidedAtDesc(teacherProfileId, VerificationAction.VERIFIED)
                .map(VerificationDecision::getTeacherEducation)
                .filter(education -> education != null
                        && education.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseGet(() -> teacherEducationRepository
                        .findByTeacher_IdAndVerificationStatus(teacherProfileId, VerificationStatus.VERIFIED).stream()
                        .findFirst()
                        .orElseThrow(() -> new ConflictException("There is no verified credential to revoke")));
    }

    private void requireNotOwnAccount(TeacherProfile profile, UUID adminUserId) {
        if (profile.getUser().getId().equals(adminUserId)) {
            throw new ConflictException("An admin cannot decide on their own account");
        }
    }

    private VerificationStatus statusFor(VerificationAction action) {
        return switch (action) {
            case VERIFIED -> VerificationStatus.VERIFIED;
            case REJECTED -> VerificationStatus.REJECTED;
            case MORE_INFO_REQUIRED -> VerificationStatus.MORE_INFO_REQUIRED;
            case REVOKED -> throw new BadRequestException("REVOKED is only produced by the revoke action");
        };
    }

    private User requireAdmin(UUID adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
        return admin;
    }

    private VerificationDecision record(TeacherProfile teacher, TeacherEducation education, VerificationDocument document,
                                        User admin, VerificationStatus previous, VerificationStatus next,
                                        VerificationAction action, VerificationMethod method, String reason) {
        VerificationDecision decision = new VerificationDecision();
        decision.setTeacher(teacher);
        decision.setTeacherEducation(education);
        decision.setDocument(document);
        decision.setAdminUser(admin);
        decision.setPreviousStatus(previous);
        decision.setNewStatus(next);
        decision.setDecision(action);
        decision.setMethod(method);
        decision.setReason(reason);
        return decisionRepository.saveAndFlush(decision);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
