package com.claseya.academic.dto;

import com.claseya.model.CareerSubject;

import java.time.Instant;
import java.util.UUID;

public record CareerSubjectResponse(
        UUID id,
        UUID careerId,
        String careerName,
        UUID subjectId,
        String subjectName,
        String nameOverride,
        String code,
        Integer year,
        Integer semester,
        boolean mandatory,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static CareerSubjectResponse from(CareerSubject careerSubject) {
        return new CareerSubjectResponse(
                careerSubject.getId(),
                careerSubject.getCareer().getId(),
                careerSubject.getCareer().getName(),
                careerSubject.getSubject().getId(),
                careerSubject.getSubject().getName(),
                careerSubject.getNameOverride(),
                careerSubject.getCode(),
                careerSubject.getYear(),
                careerSubject.getSemester(),
                Boolean.TRUE.equals(careerSubject.getMandatory()),
                Boolean.TRUE.equals(careerSubject.getActive()),
                careerSubject.getCreatedAt(),
                careerSubject.getUpdatedAt());
    }
}
