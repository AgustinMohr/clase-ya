package com.claseya.teacher.dto;

import com.claseya.model.CareerSubject;
import com.claseya.model.Subject;
import com.claseya.model.TeacherSubject;

import java.time.Instant;
import java.util.UUID;

public record TeacherSubjectResponse(
        UUID id,
        UUID careerSubjectId,
        UUID careerId,
        String careerName,
        UUID subjectId,
        String subjectName,
        String nameOverride,
        String code,
        Integer year,
        Integer semester,
        String description,
        Integer yearsExperience,
        Instant createdAt
) {

    public static TeacherSubjectResponse from(TeacherSubject teacherSubject) {
        CareerSubject careerSubject = teacherSubject.getCareerSubject();
        if (careerSubject == null) {
            Subject subject = teacherSubject.getSubject();
            return new TeacherSubjectResponse(
                    teacherSubject.getId(),
                    null,
                    null,
                    null,
                    subject.getId(),
                    subject.getName(),
                    null,
                    null,
                    null,
                    null,
                    teacherSubject.getDescription(),
                    teacherSubject.getYearsExperience(),
                    teacherSubject.getCreatedAt());
        }
        return new TeacherSubjectResponse(
                teacherSubject.getId(),
                careerSubject.getId(),
                careerSubject.getCareer().getId(),
                careerSubject.getCareer().getName(),
                careerSubject.getSubject().getId(),
                careerSubject.getSubject().getName(),
                careerSubject.getNameOverride(),
                careerSubject.getCode(),
                careerSubject.getYear(),
                careerSubject.getSemester(),
                teacherSubject.getDescription(),
                teacherSubject.getYearsExperience(),
                teacherSubject.getCreatedAt());
    }
}
