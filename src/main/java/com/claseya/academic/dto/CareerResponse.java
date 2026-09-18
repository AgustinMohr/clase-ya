package com.claseya.academic.dto;

import com.claseya.model.Career;

import java.time.Instant;
import java.util.UUID;

public record CareerResponse(
        UUID id,
        UUID academicUnitId,
        String name,
        String slug,
        String code,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static CareerResponse from(Career career) {
        return new CareerResponse(
                career.getId(),
                career.getAcademicUnit().getId(),
                career.getName(),
                career.getSlug(),
                career.getCode(),
                Boolean.TRUE.equals(career.getActive()),
                career.getCreatedAt(),
                career.getUpdatedAt());
    }
}
