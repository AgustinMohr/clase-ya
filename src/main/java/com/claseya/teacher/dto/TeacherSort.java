package com.claseya.teacher.dto;

import org.springframework.data.domain.Sort;

/**
 * Allowed sort options for teacher search. Arbitrary column names are never
 * accepted from the client (see TeacherSearchService).
 */
public enum TeacherSort {

    RATING("rating"),
    RATING_DESC("ratingDesc"),
    RATING_ASC("ratingAsc"),
    NAME("name"),
    DISTANCE("distance");

    private final String code;

    TeacherSort(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * Stable order for the non-geographic sorts; id breaks ties so pagination is
     * deterministic. Distance ordering is handled inside the query.
     */
    public Sort toSort() {
        return switch (this) {
            case RATING, RATING_DESC -> Sort.by(
                    Sort.Order.desc("ratingAverage"),
                    Sort.Order.desc("ratingCount"),
                    Sort.Order.asc("id"));
            case RATING_ASC -> Sort.by(
                    Sort.Order.asc("ratingAverage"),
                    Sort.Order.asc("ratingCount"),
                    Sort.Order.asc("id"));
            case NAME -> Sort.by(Sort.Order.asc("user.name"), Sort.Order.asc("id"));
            case DISTANCE -> Sort.unsorted();
        };
    }

    public boolean isDistance() {
        return this == DISTANCE;
    }

    public static TeacherSort parse(String value) {
        if (value == null) {
            return RATING;
        }
        for (TeacherSort candidate : values()) {
            if (candidate.code.equalsIgnoreCase(value.trim())
                    || candidate.name().equalsIgnoreCase(value.trim())) {
                return candidate;
            }
        }
        throw new com.claseya.common.exception.BadRequestException(
                "Invalid sort '" + value + "'. Allowed: rating, ratingAsc, ratingDesc, name, distance");
    }
}
