package com.claseya.auth.dto;

import com.claseya.auth.validation.ValidPassword;
import com.claseya.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email is too long")
        String email,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String password,

        @NotNull(message = "Role is required")
        UserRole role
) {
}
