package com.claseya.teacher.dto;

import java.util.List;

/**
 * Page-shaped search response matching the API contract:
 * content / page / size / totalElements / totalPages.
 */
public record SearchResultPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> SearchResultPage<T> of(List<T> content, int page, int size,
                                             long totalElements) {
        int totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new SearchResultPage<>(content, page, size, totalElements, totalPages);
    }
}
