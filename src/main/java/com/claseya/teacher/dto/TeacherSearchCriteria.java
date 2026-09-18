package com.claseya.teacher.dto;

import com.claseya.model.enums.TeachingModality;

import java.util.UUID;

/**
 * Immutable teacher-search filters built and validated by the controller/service.
 */
public record TeacherSearchCriteria(
        UUID subjectId,
        UUID careerId,
        UUID universityId,
        TeachingModality modality,
        Double minRating,
        Double latitude,
        Double longitude,
        Double radiusKm,
        int page,
        int size,
        String sort
) {

    public boolean isGeolocated() {
        return latitude != null && longitude != null;
    }
}
