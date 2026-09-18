package com.claseya.academic.dto;

import com.claseya.model.Subject;

import java.time.Instant;
import java.util.UUID;

public record SubjectResponse(
        UUID id,
        String name,
        String slug,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static SubjectResponse from(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getSlug(),
                subject.getDescription(),
                Boolean.TRUE.equals(subject.getActive()),
                subject.getCreatedAt(),
                subject.getUpdatedAt());
    }
}
