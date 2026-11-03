package com.claseya.review.service;

import com.claseya.common.dto.SearchResultPage;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.messaging.repository.ConversationRepository;
import com.claseya.messaging.repository.MessageRepository;
import com.claseya.model.Review;
import com.claseya.model.ReviewModerationEvent;
import com.claseya.model.StudentProfile;
import com.claseya.model.TeacherProfile;
import com.claseya.model.User;
import com.claseya.model.enums.ReviewModerationAction;
import com.claseya.model.enums.ReviewStatus;
import com.claseya.model.enums.UserRole;
import com.claseya.review.dto.AdminReviewResponse;
import com.claseya.review.dto.CreateReviewRequest;
import com.claseya.review.dto.HideReviewRequest;
import com.claseya.review.dto.MyReviewResponse;
import com.claseya.review.dto.RespondReviewRequest;
import com.claseya.review.dto.ReviewResponse;
import com.claseya.review.dto.UpdateReviewRequest;
import com.claseya.review.repository.ReviewModerationEventRepository;
import com.claseya.review.repository.ReviewRepository;
import com.claseya.student.repository.StudentProfileRepository;
import com.claseya.teacher.repository.TeacherProfileRepository;
import com.claseya.teacher.service.TeacherVisibilityService;
import com.claseya.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Reviews and rating (OPINION-001).
 *
 * <p>Eligibility is deliberately based on the existing contact flow, not on a booking: the teacher
 * must have replied at least once in the conversation with the student. The aggregate on
 * {@code teacher_profiles} is recalculated from PUBLISHED reviews inside the same transaction,
 * under a pessimistic lock on the teacher row so concurrent reviews cannot leave it stale.
 */
@Service
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final ReviewModerationEventRepository moderationEventRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TeacherVisibilityService teacherVisibilityService;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewModerationEventRepository moderationEventRepository,
                         TeacherProfileRepository teacherProfileRepository,
                         StudentProfileRepository studentProfileRepository,
                         ConversationRepository conversationRepository,
                         MessageRepository messageRepository,
                         UserRepository userRepository,
                         TeacherVisibilityService teacherVisibilityService) {
        this.reviewRepository = reviewRepository;
        this.moderationEventRepository = moderationEventRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.teacherVisibilityService = teacherVisibilityService;
    }

    @Transactional
    public ReviewResponse create(UUID studentUserId, UUID teacherId, CreateReviewRequest request) {
        TeacherProfile visibleTeacher = teacherVisibilityService.requireVisible(teacherId);
        StudentProfile student = studentProfileRepository.findByUser_Id(studentUserId)
                .orElseThrow(() -> new AccessDeniedException("A student profile is required"));
        requireAcceptedContact(studentUserId, visibleTeacher.getUser().getId());

        if (reviewRepository.existsByStudent_IdAndTeacher_Id(student.getId(), teacherId)) {
            throw new ConflictException("You already reviewed this teacher");
        }

        TeacherProfile teacher = lockTeacher(teacherId);
        Review review = new Review();
        review.setStudent(student);
        review.setTeacher(teacher);
        review.setRating(request.rating());
        review.setComment(trimToNull(request.comment()));
        review.setStatus(ReviewStatus.PUBLISHED);
        Review saved = reviewRepository.saveAndFlush(review);
        recalculate(teacher);
        return ReviewResponse.from(saved);
    }

    @Transactional
    public ReviewResponse update(UUID studentUserId, UUID reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findByIdAndStudent_User_Id(reviewId, studentUserId)
                .orElseThrow(ResourceNotFoundException::new);
        if (review.getStatus() == ReviewStatus.HIDDEN) {
            throw new ConflictException("A hidden review cannot be edited");
        }
        TeacherProfile teacher = lockTeacher(review.getTeacher().getId());
        review.setRating(request.rating());
        review.setComment(trimToNull(request.comment()));
        Review saved = reviewRepository.saveAndFlush(review);
        recalculate(teacher);
        return ReviewResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MyReviewResponse getMine(UUID studentUserId, UUID teacherId) {
        teacherVisibilityService.requireVisible(teacherId);
        StudentProfile student = studentProfileRepository.findByUser_Id(studentUserId)
                .orElseThrow(ResourceNotFoundException::new);
        Review review = reviewRepository.findByStudent_IdAndTeacher_Id(student.getId(), teacherId)
                .orElseThrow(ResourceNotFoundException::new);
        return MyReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public SearchResultPage<ReviewResponse> listPublic(UUID teacherId, int page, int size) {
        TeacherProfile teacher = teacherVisibilityService.requireVisible(teacherId);
        Pageable pageable = pageRequest(page, size);
        Page<Review> reviews = reviewRepository.findByTeacherAndStatusWithAuthor(
                teacher.getId(), ReviewStatus.PUBLISHED, pageable);
        return SearchResultPage.of(
                reviews.getContent().stream().map(ReviewResponse::from).toList(),
                reviews.getNumber(), reviews.getSize(), reviews.getTotalElements());
    }

    @Transactional
    public ReviewResponse respond(UUID teacherUserId, UUID reviewId, RespondReviewRequest request) {
        Review review = reviewRepository.findByIdAndTeacher_User_Id(reviewId, teacherUserId)
                .orElseThrow(ResourceNotFoundException::new);
        if (review.getStatus() == ReviewStatus.HIDDEN) {
            throw new ConflictException("A hidden review cannot receive a response");
        }
        review.setTeacherResponse(trimToNull(request.response()));
        review.setTeacherRespondedAt(Instant.now());
        return ReviewResponse.from(reviewRepository.saveAndFlush(review));
    }

    @Transactional(readOnly = true)
    public SearchResultPage<AdminReviewResponse> adminList(ReviewStatus status, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        Page<Review> reviews = status == null
                ? reviewRepository.findAllForAdmin(pageable)
                : reviewRepository.findForAdminByStatus(status, pageable);
        return SearchResultPage.of(
                reviews.getContent().stream().map(AdminReviewResponse::from).toList(),
                reviews.getNumber(), reviews.getSize(), reviews.getTotalElements());
    }

    @Transactional
    public void hide(UUID adminUserId, UUID reviewId, HideReviewRequest request) {
        User admin = requireAdmin(adminUserId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(ResourceNotFoundException::new);
        if (review.getStatus() == ReviewStatus.HIDDEN) {
            return;
        }
        String reason = trimToNull(request.reason());
        if (reason == null) {
            throw new BadRequestException("A reason is required to hide a review");
        }
        TeacherProfile teacher = lockTeacher(review.getTeacher().getId());
        review.setStatus(ReviewStatus.HIDDEN);
        review.setModeratedBy(admin);
        review.setModeratedAt(Instant.now());
        review.setModerationReason(reason);
        reviewRepository.saveAndFlush(review);
        recordModeration(review, admin, ReviewModerationAction.HIDDEN, reason);
        recalculate(teacher);
    }

    @Transactional
    public void restore(UUID adminUserId, UUID reviewId) {
        User admin = requireAdmin(adminUserId);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(ResourceNotFoundException::new);
        if (review.getStatus() == ReviewStatus.PUBLISHED) {
            return;
        }
        TeacherProfile teacher = lockTeacher(review.getTeacher().getId());
        review.setStatus(ReviewStatus.PUBLISHED);
        review.setModeratedBy(null);
        review.setModeratedAt(null);
        review.setModerationReason(null);
        reviewRepository.saveAndFlush(review);
        recordModeration(review, admin, ReviewModerationAction.RESTORED, null);
        recalculate(teacher);
    }

    private void requireAcceptedContact(UUID studentUserId, UUID teacherUserId) {
        boolean accepted = conversationRepository.findBetween(studentUserId, teacherUserId).stream()
                .anyMatch(conversation -> messageRepository.existsByConversation_IdAndSender_Id(
                        conversation.getId(), teacherUserId));
        if (!accepted) {
            throw new AccessDeniedException("An accepted contact is required before reviewing");
        }
    }

    private TeacherProfile lockTeacher(UUID teacherId) {
        return teacherProfileRepository.findByIdForUpdate(teacherId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private User requireAdmin(UUID adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(ResourceNotFoundException::new);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
        return admin;
    }

    private void recalculate(TeacherProfile teacher) {
        ReviewRepository.RatingAggregate aggregate = reviewRepository
                .aggregateForTeacher(teacher.getId(), ReviewStatus.PUBLISHED);
        double average = aggregate == null || aggregate.getAverage() == null
                ? 0.0
                : aggregate.getAverage();
        long count = aggregate == null || aggregate.getTotal() == null
                ? 0L
                : aggregate.getTotal();
        teacher.setRatingAverage(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        teacher.setRatingCount(Math.toIntExact(count));
        teacherProfileRepository.saveAndFlush(teacher);
    }

    private void recordModeration(Review review, User admin,
                                  ReviewModerationAction action, String reason) {
        ReviewModerationEvent event = new ReviewModerationEvent();
        event.setReview(review);
        event.setAdminUser(admin);
        event.setAction(action);
        event.setReason(reason);
        moderationEventRepository.saveAndFlush(event);
    }

    private static Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
