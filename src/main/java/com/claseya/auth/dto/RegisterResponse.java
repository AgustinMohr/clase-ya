package com.claseya.auth.dto;

import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        UserRole role,
        UserStatus status
) {
}
