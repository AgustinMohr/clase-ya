package com.claseya.auth.service;

import com.claseya.auth.dto.AdminUserSummaryResponse;
import com.claseya.auth.repository.AdminUserDirectoryRepository;
import com.claseya.auth.repository.AdminUserDirectoryRepository.AdminUserRow;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.common.dto.SearchResultPage;
import com.claseya.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Admin user directory and account-status transitions (ADMIN-002).
 *
 * <p>Suspending relies on {@code JwtAuthenticationFilter}, which reloads the account on every
 * request and only authenticates {@code ACTIVE} accounts: the change applies to the suspended
 * user's next request, without waiting for the token to expire.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    static final int MAX_SIZE = 50;
    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    private static final char LIKE_ESCAPE = '!';

    private final AdminUserDirectoryRepository directoryRepository;
    private final UserRepository userRepository;

    public AdminUserService(AdminUserDirectoryRepository directoryRepository,
                            UserRepository userRepository) {
        this.directoryRepository = directoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SearchResultPage<AdminUserSummaryResponse> list(UserRole role, UserStatus status,
                                                           String query, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        Page<AdminUserRow> result = directoryRepository.search(role, status, likePattern(query),
                PageRequest.of(page, size));
        List<AdminUserSummaryResponse> content = result.getContent().stream()
                .map(AdminUserService::toResponse)
                .toList();
        return SearchResultPage.of(content, page, size, result.getTotalElements());
    }

    @Transactional
    public void suspend(UUID actorId, UUID targetId) {
        if (actorId != null && actorId.equals(targetId)) {
            throw new ConflictException("You cannot suspend your own account");
        }
        transition(requireUser(targetId), UserStatus.ACTIVE, UserStatus.SUSPENDED,
                "USER_SUSPENDED", actorId);
    }

    @Transactional
    public void restore(UUID actorId, UUID targetId) {
        transition(requireUser(targetId), UserStatus.SUSPENDED, UserStatus.ACTIVE,
                "USER_RESTORED", actorId);
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    private void transition(User user, UserStatus expected, UserStatus next, String action,
                            UUID actorId) {
        UserStatus current = user.getStatus();
        if (current == next) {
            return; // Idempotent: repeating the action does not fail.
        }
        if (current != expected) {
            throw new ConflictException(
                    "Account status " + current + " cannot change to " + next);
        }
        user.setStatus(next);
        userRepository.save(user);
        log.info("adminAction={} actorId={} targetUserId={} from={} to={}",
                action, actorId, user.getId(), current, next);
    }

    private static AdminUserSummaryResponse toResponse(AdminUserRow row) {
        boolean teacher = row.getRole() == UserRole.TEACHER;
        boolean student = row.getRole() == UserRole.STUDENT;
        return new AdminUserSummaryResponse(
                row.getId(),
                row.getEmail(),
                row.getName(),
                row.getRole(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getLastLoginAt(),
                teacher ? row.getTeacherVerificationStatus() : null,
                student ? row.getStudentProfileId() != null : null);
    }

    /** Wraps the trimmed query in a LIKE pattern, escaping the wildcards and the escape char. */
    private static String likePattern(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            throw new BadRequestException(
                    "query must have at least " + MIN_QUERY_LENGTH + " characters");
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new BadRequestException(
                    "query must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
                .replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "" + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
