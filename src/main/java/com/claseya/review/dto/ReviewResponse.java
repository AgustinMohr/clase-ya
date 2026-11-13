package com.claseya.review.dto;

import com.claseya.model.Review;

import java.time.Instant;
import java.util.UUID;

/** Public review: no user ids, emails, moderation data or hidden state. */
public record ReviewResponse(
        UUID id,
        Integer rating,
        String comment,
        String authorDisplayName,
        Instant createdAt,
        String teacherResponse,
        Instant teacherRespondedAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                authorName(review),
                review.getCreatedAt(),
                review.getTeacherResponse(),
                review.getTeacherRespondedAt());
    }

    static String authorName(Review review) {
        String name = fullAuthorName(review);
        int firstSpace = name.indexOf(' ');
        return firstSpace > 0 ? name.substring(0, firstSpace) : name;
    }

    static String fullAuthorName(Review review) {
        String name = review.getStudent().getUser().getName();
        return name == null || name.isBlank() ? "Estudiante" : name.trim();
    }
}
