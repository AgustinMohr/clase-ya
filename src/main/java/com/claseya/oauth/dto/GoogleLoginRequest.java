package com.claseya.oauth.dto;

import com.claseya.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;

/**
 * Google sign-in. The identity always comes from the validated Google ID token;
 * {@code role} is only used when the account does not exist yet (never ADMIN).
 */
public record GoogleLoginRequest(
        @NotBlank(message = "idToken is required")
        String idToken,

        UserRole role
) {
}
