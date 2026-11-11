package com.claseya.student.dto;

import com.claseya.model.Career;
import com.claseya.model.StudentProfile;
import com.claseya.model.University;
import com.claseya.model.enums.StudentLevel;

import java.time.Instant;
import java.util.UUID;

public record StudentProfileResponse(
        UUID id,
        UUID userId,
        String email,
        String name,
        StudentLevel level,
        String city,
        UUID universityId,
        String universityName,
        UUID academicUnitId,
        UUID careerId,
        String careerName,
        Integer currentYear,
        String bio,
        Instant createdAt,
        Instant updatedAt
) {

    public static StudentProfileResponse from(StudentProfile profile) {
        University university = profile.getUniversity();
        Career career = profile.getCareer();
        return new StudentProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getEmail(),
                profile.getUser().getName(),
                profile.getLevel(),
                profile.getCity(),
                university == null ? null : university.getId(),
                university == null ? null : university.getName(),
                career == null ? null : career.getAcademicUnit().getId(),
                career == null ? null : career.getId(),
                career == null ? null : career.getName(),
                profile.getCurrentYear(),
                profile.getBio(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
