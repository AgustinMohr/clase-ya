package com.claseya.auth.dto;

import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin user directory (ADMIN-002).
 *
 * <p>This is the only DTO that exposes the account email: the directory is ADMIN-only and the
 * email is the support identifier. Students carry no academic data on purpose (D2).
 * {@code passwordHash}, {@code googleSub} and {@code emailVerifiedAt} are never exposed.
 */
public record AdminUserSummaryResponse(
        UUID id,
        String email,
        String name,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt,
        VerificationStatus teacherVerificationStatus,
        Boolean studentProfileComplete) {
}
