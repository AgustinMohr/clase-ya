package com.claseya.verification.service;

import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.VerificationDecision;
import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.service.TeacherProfileService;
import com.claseya.verification.dto.CredentialVerificationView;
import com.claseya.verification.dto.TeacherVerificationView;
import com.claseya.verification.dto.VerificationDecisionView;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the aggregate profile status (RF-14/RF-15/D10): it is persisted but always recalculated from
 * the credential statuses inside the same transaction as every credential transition.
 *
 * <p>All credential transitions go through {@link #requireForUpdate(UUID)}, which takes a
 * pessimistic write lock on the profile row. That single lock serializes the decisions of a teacher
 * so that (a) concurrent decisions on the same credential cannot be last-write-wins (RF-25) and
 * (b) the derived profile status can never disagree with its credentials (I12).
 */
@Service
public class TeacherVerificationService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherEducationRepository teacherEducationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final VerificationDecisionRepository decisionRepository;
    private final VerificationAggregator aggregator;
    private final TeacherProfileService teacherProfileService;

    public TeacherVerificationService(TeacherProfileRepository teacherProfileRepository,
                                      TeacherEducationRepository teacherEducationRepository,
                                      VerificationDocumentRepository documentRepository,
                                      VerificationDecisionRepository decisionRepository,
                                      VerificationAggregator aggregator,
                                      TeacherProfileService teacherProfileService) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherEducationRepository = teacherEducationRepository;
        this.documentRepository = documentRepository;
        this.decisionRepository = decisionRepository;
        this.aggregator = aggregator;
        this.teacherProfileService = teacherProfileService;
    }

    /** Loads the teacher profile under a pessimistic write lock (see class javadoc). */
    @Transactional
    public TeacherProfile requireForUpdate(UUID teacherProfileId) {
        return teacherProfileRepository.findByIdForUpdate(teacherProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
    }

    @Transactional(readOnly = true)
    public List<TeacherEducation> credentials(UUID teacherProfileId) {
        return teacherEducationRepository.findByTeacher_IdOrderByStartYearDesc(teacherProfileId);
    }

    /** Recomputes the aggregate profile status from the current credential statuses and persists it. */
    @Transactional
    public VerificationStatus recalculateProfileStatus(TeacherProfile profile) {
        VerificationStatus aggregate = aggregator.aggregate(credentialStatuses(profile.getId()));
        profile.setVerificationStatus(aggregate);
        teacherProfileRepository.saveAndFlush(profile);
        return aggregate;
    }

    @Transactional(readOnly = true)
    public TeacherVerificationView viewForUser(UUID userId) {
        return buildView(teacherProfileService.requireByUser(userId));
    }

    @Transactional(readOnly = true)
    public TeacherVerificationView viewForProfile(UUID teacherProfileId) {
        TeacherProfile profile = teacherProfileRepository.findById(teacherProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
        return buildView(profile);
    }

    private TeacherVerificationView buildView(TeacherProfile profile) {
        UUID teacherId = profile.getId();
        Map<UUID, String> requirementByEducation = moreInfoRequirements(teacherId);

        List<CredentialVerificationView> credentials = new ArrayList<>();
        for (TeacherEducation education : credentials(teacherId)) {
            String requirement = education.getVerificationStatus() == VerificationStatus.MORE_INFO_REQUIRED
                    ? requirementByEducation.get(education.getId())
                    : null;
            credentials.add(CredentialVerificationView.of(education,
                    documentRepository.countByTeacherEducation_Id(education.getId()), requirement));
        }

        List<VerificationDecisionView> history = decisionRepository
                .findByTeacher_IdOrderByDecidedAtDesc(teacherId).stream()
                .map(VerificationDecisionView::from)
                .toList();

        return new TeacherVerificationView(profile.getVerificationStatus(), credentials, history);
    }

    /**
     * For each credential the reason of its latest "more info required" decision. The repository
     * returns them newest first, so {@code putIfAbsent} keeps the current requirement.
     */
    private Map<UUID, String> moreInfoRequirements(UUID teacherId) {
        Map<UUID, String> result = new HashMap<>();
        for (VerificationDecision decision : decisionRepository
                .findByTeacher_IdAndDecisionOrderByDecidedAtDesc(teacherId, VerificationAction.MORE_INFO_REQUIRED)) {
            if (decision.getTeacherEducation() == null) {
                continue;
            }
            result.putIfAbsent(decision.getTeacherEducation().getId(), decision.getReason());
        }
        return result;
    }

    private List<VerificationStatus> credentialStatuses(UUID teacherProfileId) {
        return teacherEducationRepository.findByTeacher_IdOrderByStartYearDesc(teacherProfileId).stream()
                .map(TeacherEducation::getVerificationStatus)
                .toList();
    }
}
