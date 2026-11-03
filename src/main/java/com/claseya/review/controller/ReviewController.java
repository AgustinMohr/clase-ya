package com.claseya.review.controller;

import com.claseya.common.dto.SearchResultPage;
import com.claseya.review.dto.CreateReviewRequest;
import com.claseya.review.dto.MyReviewResponse;
import com.claseya.review.dto.RespondReviewRequest;
import com.claseya.review.dto.ReviewResponse;
import com.claseya.review.dto.UpdateReviewRequest;
import com.claseya.review.service.ReviewService;
import com.claseya.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reviews of a teacher (OPINION-001). Identity always comes from the SecurityContext. */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @GetMapping("/teachers/{teacherId}/reviews")
    public SearchResultPage<ReviewResponse> list(
            @PathVariable UUID teacherId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reviewService.listPublic(teacherId, page, size);
    }

    @GetMapping("/teachers/{teacherId}/reviews/me")
    public MyReviewResponse mine(@PathVariable UUID teacherId) {
        return reviewService.getMine(currentUser.id(), teacherId);
    }

    @PostMapping("/teachers/{teacherId}/reviews")
    public ResponseEntity<ReviewResponse> create(@PathVariable UUID teacherId,
                                                 @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(currentUser.id(), teacherId, request));
    }

    @PutMapping("/reviews/{reviewId}")
    public ReviewResponse update(@PathVariable UUID reviewId,
                                 @Valid @RequestBody UpdateReviewRequest request) {
        return reviewService.update(currentUser.id(), reviewId, request);
    }

    @PostMapping("/reviews/{reviewId}/response")
    public ReviewResponse respond(@PathVariable UUID reviewId,
                                  @Valid @RequestBody RespondReviewRequest request) {
        return reviewService.respond(currentUser.id(), reviewId, request);
    }
}
