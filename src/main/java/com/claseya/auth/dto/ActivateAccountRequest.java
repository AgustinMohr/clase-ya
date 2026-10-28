package com.claseya.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateAccountRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 128, message = "Token is not valid")
        String token
) {
}
