package com.claseya.review.dto;

import com.claseya.model.Review;
import com.claseya.model.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/** Admin review row: includes moderation data that is never exposed publicly. */
public record AdminReviewResponse(
        UUID id,
        UUID teacherId,
        String teacherName,
        String authorName,
        Integer rating,
        String comment,
        ReviewStatus status,
        String teacherResponse,
        Instant teacherRespondedAt,
        Instant createdAt,
        Instant updatedAt,
        String moderationReason,
        Instant moderatedAt,
        String moderatedByName
) {

    public static AdminReviewResponse from(Review review) {
        String teacherName = review.getTeacher().getUser().getName();
        String moderatedByName = review.getModeratedBy() == null
                ? null
                : review.getModeratedBy().getName();
        return new AdminReviewResponse(
                review.getId(),
                review.getTeacher().getId(),
                teacherName == null || teacherName.isBlank() ? "Profesor" : teacherName,
                ReviewResponse.fullAuthorName(review),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getTeacherResponse(),
                review.getTeacherRespondedAt(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getModerationReason(),
                review.getModeratedAt(),
                moderatedByName);
    }
}
