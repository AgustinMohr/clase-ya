package com.claseya.academic.dto;

import com.claseya.model.AcademicUnit;

import java.time.Instant;
import java.util.UUID;

public record AcademicUnitResponse(
        UUID id,
        UUID universityId,
        String name,
        String code,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static AcademicUnitResponse from(AcademicUnit unit) {
        return new AcademicUnitResponse(
                unit.getId(),
                unit.getUniversity().getId(),
                unit.getName(),
                unit.getCode(),
                Boolean.TRUE.equals(unit.getActive()),
                unit.getCreatedAt(),
                unit.getUpdatedAt());
    }
}
