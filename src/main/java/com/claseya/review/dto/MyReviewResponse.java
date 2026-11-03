package com.claseya.review.dto;

import com.claseya.model.Review;
import com.claseya.model.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/** The author's own review; it includes the status even when the review is hidden. */
public record MyReviewResponse(
        UUID id,
        Integer rating,
        String comment,
        ReviewStatus status,
        Instant createdAt,
        Instant updatedAt,
        String teacherResponse,
        Instant teacherRespondedAt
) {

    public static MyReviewResponse from(Review review) {
        return new MyReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getTeacherResponse(),
                review.getTeacherRespondedAt());
    }
}
