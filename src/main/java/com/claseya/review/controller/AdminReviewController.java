package com.claseya.review.controller;

import com.claseya.common.dto.SearchResultPage;
import com.claseya.model.enums.ReviewStatus;
import com.claseya.review.dto.AdminReviewResponse;
import com.claseya.review.dto.HideReviewRequest;
import com.claseya.review.service.ReviewService;
import com.claseya.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin moderation of reviews (OPINION-001). The route is ADMIN-only in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public AdminReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public SearchResultPage<AdminReviewResponse> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewService.adminList(status, page, size);
    }

    @PostMapping("/{reviewId}/hide")
    public ResponseEntity<Void> hide(@PathVariable UUID reviewId,
                                     @Valid @RequestBody HideReviewRequest request) {
        reviewService.hide(currentUser.id(), reviewId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reviewId}/restore")
    public ResponseEntity<Void> restore(@PathVariable UUID reviewId) {
        reviewService.restore(currentUser.id(), reviewId);
        return ResponseEntity.noContent().build();
    }
}
