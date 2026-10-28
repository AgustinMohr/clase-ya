package com.claseya.auth.dto;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;

import java.util.UUID;

/**
 * Authenticated user data safe to return to the owner's own SPA session. It intentionally has no
 * email: the token must not carry PII and the UI only needs an id, a display name and the role.
 */
public record SessionUserResponse(UUID id, String name, UserRole role) {

    public static SessionUserResponse from(User user) {
        return new SessionUserResponse(user.getId(), user.getName(), user.getRole());
    }
}
