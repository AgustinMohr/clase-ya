package com.claseya.verification.service;

import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherEducation;
import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.verification.dto.AdminVerificationDetail;
import com.claseya.verification.dto.CredentialVerificationView;
import com.claseya.verification.dto.VerificationAuditEntry;
import com.claseya.verification.dto.VerificationDocumentResponse;
import com.claseya.verification.dto.VerificationQueueItem;
import com.claseya.verification.repository.VerificationDecisionRepository;
import com.claseya.verification.repository.VerificationDocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read models for the review side: the paginated queue and the admin detail of one teacher. */
@Service
public class VerificationQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TeacherEducationRepository teacherEducationRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final VerificationDocumentRepository documentRepository;
    private final VerificationDecisionRepository decisionRepository;

    public VerificationQueryService(TeacherEducationRepository teacherEducationRepository,
                                    TeacherProfileRepository teacherProfileRepository,
                                    VerificationDocumentRepository documentRepository,
                                    VerificationDecisionRepository decisionRepository) {
        this.teacherEducationRepository = teacherEducationRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.documentRepository = documentRepository;
        this.decisionRepository = decisionRepository;
    }

    /** Queue ordered "oldest presentation first"; defaults to UNDER_REVIEW (the review backlog). */
    @Transactional(readOnly = true)
    public SearchResultPage<VerificationQueueItem> queue(VerificationStatus status, int page, int size) {
        VerificationStatus effectiveStatus = status == null ? VerificationStatus.UNDER_REVIEW : status;
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "submittedAt"));

        Page<TeacherEducation> result = teacherEducationRepository.pageByVerificationStatus(effectiveStatus, pageable);
        Map<UUID, Long> documentCounts = documentCounts(result.getContent());
        List<VerificationQueueItem> items = result.getContent().stream()
                .map(education -> VerificationQueueItem.of(education,
                        education.getTeacher().getUser().getName(),
                        documentCounts.getOrDefault(education.getId(), 0L)))
                .toList();
        return SearchResultPage.of(items, safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminVerificationDetail adminDetail(UUID teacherProfileId) {
        TeacherProfile profile = teacherProfileRepository.findById(teacherProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));
        List<TeacherEducation> credentials = teacherEducationRepository.findByTeacher_IdOrderByStartYearDesc(teacherProfileId);
        Map<UUID, Long> documentCounts = documentCounts(credentials);

        List<CredentialVerificationView> credentialViews = credentials.stream()
                .map(education -> CredentialVerificationView.of(education,
                        documentCounts.getOrDefault(education.getId(), 0L)))
                .toList();
        List<VerificationDocumentResponse> documents = documentRepository
                .findByTeacher_IdOrderByUploadedAtDesc(teacherProfileId).stream()
                .map(VerificationDocumentResponse::from)
                .toList();
        List<VerificationAuditEntry> history = decisionRepository
                .findByTeacher_IdOrderByDecidedAtDesc(teacherProfileId).stream()
                .map(VerificationAuditEntry::from)
                .toList();

        return new AdminVerificationDetail(profile.getId(), profile.getUser().getName(),
                profile.getVerificationStatus(), credentialViews, documents, history);
    }

    private Map<UUID, Long> documentCounts(List<TeacherEducation> credentials) {
        if (credentials.isEmpty()) {
            return Map.of();
        }
        List<UUID> educationIds = credentials.stream().map(TeacherEducation::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : documentRepository.countByEducationIds(educationIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
