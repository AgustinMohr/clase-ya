package com.claseya.teacher.dto;

/**
 * Informative announcement checklist for "Mi anuncio" (RF-2): overall completeness plus which
 * minimum pieces are already present, so the teacher knows what is missing.
 */
public record AnnouncementStatusResponse(
        AnnouncementCompleteness completeness,
        boolean hasName,
        boolean hasBio,
        boolean hasSubject,
        boolean hasModality,
        boolean hasAvailability
) {
}
