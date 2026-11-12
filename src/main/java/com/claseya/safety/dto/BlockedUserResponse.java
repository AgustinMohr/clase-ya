package com.claseya.safety.dto;

import com.claseya.model.User;

import java.util.UUID;

public record BlockedUserResponse(UUID userId, String displayName) {

    public static BlockedUserResponse from(User user) {
        return new BlockedUserResponse(user.getId(), user.getName());
    }
}
