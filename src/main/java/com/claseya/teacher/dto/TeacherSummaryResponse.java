package com.claseya.teacher.dto;

import com.claseya.model.enums.VerificationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public teacher card for search results. Intentionally omits email, address and
 * exact coordinates. {@code distanceKm} is only set when the query is geographic.
 */
public record TeacherSummaryResponse(
        UUID id,
        String displayName,
        String bio,
        BigDecimal ratingAverage,
        Integer ratingCount,
        VerificationStatus verificationStatus,
        BigDecimal pricePerHour,
        String city,
        String photoUrl,
        List<String> modalities,
        Double distanceKm,
        List<TeacherSubjectView> subjects
) {
}
