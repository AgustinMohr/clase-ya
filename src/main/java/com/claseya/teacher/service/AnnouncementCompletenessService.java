package com.claseya.teacher.service;

import com.claseya.availability.repository.AvailabilityWindowRepository;
import com.claseya.model.TeacherProfile;
import com.claseya.teacher.dto.AnnouncementCompleteness;
import com.claseya.teacher.dto.AnnouncementStatusResponse;
import com.claseya.teacher.repository.TeacherModalityRepository;
import com.claseya.teacher.repository.TeacherSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Computes the announcement completeness (RF-2, D1): the minimum set a published announcement needs
 * is a name, a bio, at least one subject, at least one modality and at least one availability
 * window. Purely informational — it never blocks editing or verification, but it is exactly the
 * same rule the public visibility predicate enforces.
 */
@Service
public class AnnouncementCompletenessService {

    private final TeacherProfileService teacherProfileService;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final TeacherModalityRepository teacherModalityRepository;
    private final AvailabilityWindowRepository availabilityWindowRepository;

    public AnnouncementCompletenessService(TeacherProfileService teacherProfileService,
                                           TeacherSubjectRepository teacherSubjectRepository,
                                           TeacherModalityRepository teacherModalityRepository,
                                           AvailabilityWindowRepository availabilityWindowRepository) {
        this.teacherProfileService = teacherProfileService;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.teacherModalityRepository = teacherModalityRepository;
        this.availabilityWindowRepository = availabilityWindowRepository;
    }

    @Transactional(readOnly = true)
    public AnnouncementStatusResponse statusFor(UUID userId) {
        TeacherProfile profile = teacherProfileService.requireByUser(userId);
        boolean hasName = notBlank(profile.getUser().getName());
        boolean hasBio = notBlank(profile.getBio());
        boolean hasSubject = teacherSubjectRepository.existsByTeacher_IdAndActiveTrue(profile.getId());
        boolean hasModality = teacherModalityRepository.existsByTeacher_Id(profile.getId());
        boolean hasAvailability = availabilityWindowRepository.existsByTeacher_Id(profile.getId());

        boolean published = hasName && hasBio && hasSubject && hasModality && hasAvailability;
        return new AnnouncementStatusResponse(
                published ? AnnouncementCompleteness.PUBLISHED : AnnouncementCompleteness.INCOMPLETE,
                hasName, hasBio, hasSubject, hasModality, hasAvailability);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
