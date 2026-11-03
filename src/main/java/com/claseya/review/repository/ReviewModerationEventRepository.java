package com.claseya.review.repository;

import com.claseya.model.ReviewModerationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewModerationEventRepository extends JpaRepository<ReviewModerationEvent, UUID> {

    List<ReviewModerationEvent> findByReview_IdOrderByCreatedAtDesc(UUID reviewId);
}
