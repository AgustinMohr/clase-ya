package com.claseya.academic.dto;

import com.claseya.model.University;

import java.time.Instant;
import java.util.UUID;

public record UniversityResponse(
        UUID id,
        String name,
        String shortName,
        String slug,
        String city,
        String province,
        String country,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static UniversityResponse from(University university) {
        return new UniversityResponse(
                university.getId(),
                university.getName(),
                university.getShortName(),
                university.getSlug(),
                university.getCity(),
                university.getProvince(),
                university.getCountry(),
                Boolean.TRUE.equals(university.getActive()),
                university.getCreatedAt(),
                university.getUpdatedAt());
    }
}
