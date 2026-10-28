package com.claseya.teacher.service;

import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.TeacherProfile;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.specification.TeacherSpecifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single source of truth for public teacher visibility (D1, TEACHER-001): an ACTIVE teacher whose
 * announcement is PUBLISHED. Search, public detail, favorites and messaging all go through here so
 * the rule cannot drift between them. Reuses the same predicate as the search query.
 */
@Service
public class TeacherVisibilityService {

    private final TeacherProfileRepository teacherProfileRepository;

    public TeacherVisibilityService(TeacherProfileRepository teacherProfileRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
    }

    /**
     * Returns the profile or throws 404 — hidden profiles must never be revealed (privacy).
     * One query: the visibility predicate and the id are evaluated together (PERF-01).
     */
    @Transactional(readOnly = true)
    public TeacherProfile requireVisible(UUID teacherId) {
        return teacherProfileRepository
                .findOne(TeacherSpecifications.visible().and(TeacherSpecifications.hasId(teacherId)))
                .orElseThrow(ResourceNotFoundException::new);
    }
}
