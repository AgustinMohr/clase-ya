package com.claseya.auth.dto;

import com.claseya.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 2048, message = "Token is too long")
        String token,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String newPassword
) {
}
