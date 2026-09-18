package com.claseya.teacher.dto;

import com.claseya.model.TeacherEducation;

import java.time.Instant;
import java.util.UUID;

public record TeacherEducationResponse(
        UUID id,
        String institution,
        String degree,
        String description,
        Integer startYear,
        Integer endYear,
        boolean isVerified,
        Instant createdAt
) {

    public static TeacherEducationResponse from(TeacherEducation education) {
        return new TeacherEducationResponse(
                education.getId(),
                education.getInstitution(),
                education.getDegree(),
                education.getDescription(),
                education.getStartYear(),
                education.getEndYear(),
                Boolean.TRUE.equals(education.getIsVerified()),
                education.getCreatedAt());
    }
}
