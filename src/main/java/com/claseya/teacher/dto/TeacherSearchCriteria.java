package com.claseya.teacher.dto;

import com.claseya.model.enums.TeachingModality;

import java.math.BigDecimal;
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
        BigDecimal minPrice,
        BigDecimal maxPrice,
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

    /** True when a price bound is present, which excludes teachers without a published price. */
    public boolean hasPriceFilter() {
        return minPrice != null || maxPrice != null;
    }
}
