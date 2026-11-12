package com.claseya.teacher.dto;

import com.claseya.model.CareerSubject;
import com.claseya.model.Subject;
import com.claseya.model.TeacherSubject;

import java.util.UUID;

/**
 * A subject taught by a teacher. Legacy rows come from CareerSubject; direct rows point to
 * Subject only (PROFILE-004). Privacy-safe: no exact location.
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
        CareerSubject careerSubject = teacherSubject.getCareerSubject();
        if (careerSubject == null) {
            Subject subject = teacherSubject.getSubject();
            return new TeacherSubjectView(
                    null,
                    subject.getId(),
                    subject.getName(),
                    null,
                    null,
                    null,
                    null);
        }
        Subject subject = careerSubject.getSubject();
        String displayName = careerSubject.getNameOverride() != null
                ? careerSubject.getNameOverride()
                : subject.getName();
        return new TeacherSubjectView(
                careerSubject.getId(),
                subject.getId(),
                displayName,
                careerSubject.getCareer().getId(),
                careerSubject.getCareer().getName(),
                careerSubject.getCareer().getAcademicUnit().getUniversity().getId(),
                careerSubject.getCareer().getAcademicUnit().getUniversity().getName());
    }
}
