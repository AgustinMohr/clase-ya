package com.claseya.teacher.dto;

import com.claseya.model.TeacherSubject;

import java.util.UUID;

/**
 * A subject taught by a teacher, resolved through CareerSubject into its
 * conceptual Subject, Career and University (privacy-safe; no exact location).
 */
public record TeacherSubjectView(
        UUID careerSubjectId,
        UUID subjectId,
        String subjectName,
        UUID careerId,
        String careerName,
        UUID universityId,
        String universityName
) {

    public static TeacherSubjectView from(TeacherSubject teacherSubject) {
        var careerSubject = teacherSubject.getCareerSubject();
        var subject = careerSubject.getSubject();
        var career = careerSubject.getCareer();
        var university = career.getAcademicUnit().getUniversity();
        String displayName = careerSubject.getNameOverride() != null
                ? careerSubject.getNameOverride()
                : subject.getName();
        return new TeacherSubjectView(
                careerSubject.getId(),
                subject.getId(),
                displayName,
                career.getId(),
                career.getName(),
                university.getId(),
                university.getName());
    }
}
