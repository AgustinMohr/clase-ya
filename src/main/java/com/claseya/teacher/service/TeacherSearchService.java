package com.claseya.teacher.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherProfile;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import com.claseya.teacher.dto.SearchResultPage;
import com.claseya.teacher.dto.TeacherEducationResponse;
import com.claseya.teacher.dto.TeacherPublicDetailResponse;
import com.claseya.teacher.dto.TeacherSearchCriteria;
import com.claseya.teacher.dto.TeacherSort;
import com.claseya.teacher.dto.TeacherSubjectView;
import com.claseya.teacher.dto.TeacherSummaryResponse;
import com.claseya.teacher.repository.TeacherEducationRepository;
import com.claseya.teacher.repository.TeacherModalityRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.repository.TeacherSubjectRepository;
import com.claseya.teacher.specification.TeacherSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TeacherSearchService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final double MAX_RADIUS_KM = 100.0;

    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherEducationRepository teacherEducationRepository;
    private final TeacherModalityRepository teacherModalityRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final TeacherSummaryAssembler teacherSummaryAssembler;

    public TeacherSearchService(TeacherProfileRepository teacherProfileRepository,
                                TeacherEducationRepository teacherEducationRepository,
                                TeacherModalityRepository teacherModalityRepository,
                                TeacherSubjectRepository teacherSubjectRepository,
                                TeacherSummaryAssembler teacherSummaryAssembler) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.teacherEducationRepository = teacherEducationRepository;
        this.teacherModalityRepository = teacherModalityRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.teacherSummaryAssembler = teacherSummaryAssembler;
    }

    @Transactional(readOnly = true)
    public SearchResultPage<TeacherSummaryResponse> search(TeacherSearchCriteria criteria) {
        validateCriteria(criteria);

        TeacherSort sort = TeacherSort.parse(criteria.sort());
        Specification<TeacherProfile> spec = buildSpecification(criteria, sort);
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), sort.toSort());
        Page<TeacherProfile> page = teacherProfileRepository.findAll(spec, pageable);

        Double lat = criteria.isGeolocated() ? criteria.latitude() : null;
        Double lon = criteria.isGeolocated() ? criteria.longitude() : null;
        List<TeacherSummaryResponse> content =
                teacherSummaryAssembler.summarize(page.getContent(), lat, lon);

        return SearchResultPage.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TeacherPublicDetailResponse getPublic(UUID id) {
        TeacherProfile profile = teacherProfileRepository.findById(id)
                .orElseThrow(ResourceNotFoundException::new);
        // Public detail only exposes verified + active teachers (404 otherwise).
        if (profile.getVerificationStatus() != VerificationStatus.VERIFIED
                || profile.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ResourceNotFoundException();
        }

        String displayName = profile.getUser().getName();
        List<String> modalities = teacherModalityRepository.findByTeacher_Id(id).stream()
                .map(tm -> tm.getModality().name())
                .sorted()
                .toList();
        List<TeacherSubjectView> subjects = teacherSubjectRepository
                .findActiveWithDetailsByTeacherIds(List.of(id)).stream()
                .map(TeacherSubjectView::from)
                .toList();
        List<TeacherEducationResponse> education = teacherEducationRepository
                .findByTeacher_IdOrderByStartYearDesc(id).stream()
                .map(TeacherEducationResponse::from)
                .toList();
        return TeacherPublicDetailResponse.of(profile, displayName, modalities, subjects, education);
    }

    // ------------------------------------------------------------------ query build

    private Specification<TeacherProfile> buildSpecification(TeacherSearchCriteria criteria, TeacherSort sort) {
        List<Specification<TeacherProfile>> parts = new ArrayList<>();
        parts.add(TeacherSpecifications.visible());
        Specification<TeacherProfile> academic = TeacherSpecifications.teachesAcademicCombination(
                criteria.subjectId(), criteria.careerId(), criteria.universityId());
        if (academic != null) {
            parts.add(academic);
        }
        if (criteria.modality() != null) {
            parts.add(TeacherSpecifications.hasModality(criteria.modality()));
        }
        if (criteria.minRating() != null) {
            parts.add(TeacherSpecifications.minimumRating(BigDecimal.valueOf(criteria.minRating())));
        }
        if (criteria.isGeolocated()) {
            parts.add(TeacherSpecifications.withinRadius(
                    criteria.latitude(), criteria.longitude(), criteria.radiusKm()));
            if (sort.isDistance()) {
                parts.add(TeacherSpecifications.orderByDistance(
                        criteria.latitude(), criteria.longitude()));
            }
        }
        return parts.stream().reduce(Specification::and).orElse(null);
    }

    private void validateCriteria(TeacherSearchCriteria c) {
        if (c.page() < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (c.size() < 1 || c.size() > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        if (c.minRating() != null && (c.minRating() < 0 || c.minRating() > 5)) {
            throw new BadRequestException("minRating must be between 0 and 5");
        }
        boolean hasLat = c.latitude() != null;
        boolean hasLon = c.longitude() != null;
        if (hasLat != hasLon) {
            throw new BadRequestException("latitude and longitude must be provided together");
        }
        if (hasLat) {
            if (c.latitude() < -90 || c.latitude() > 90) {
                throw new BadRequestException("latitude must be between -90 and 90");
            }
            if (c.longitude() < -180 || c.longitude() > 180) {
                throw new BadRequestException("longitude must be between -180 and 180");
            }
            if (c.radiusKm() == null || c.radiusKm() <= 0) {
                throw new BadRequestException("radius must be greater than 0");
            }
            if (c.radiusKm() > MAX_RADIUS_KM) {
                throw new BadRequestException("radius must be at most " + MAX_RADIUS_KM + " km");
            }
        } else if (c.radiusKm() != null) {
            throw new BadRequestException("radius requires latitude and longitude");
        }
        TeacherSort sort = TeacherSort.parse(c.sort());
        if (sort.isDistance() && !hasLat) {
            throw new BadRequestException("sort=distance requires latitude and longitude");
        }
    }

    public static int defaultSize() {
        return DEFAULT_SIZE;
    }
}
