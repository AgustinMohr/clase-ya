package com.claseya.auth.human;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HumanVerifyRequest(
        @NotBlank(message = "Captcha token is required")
        @Size(max = 2048, message = "Captcha token is too long")
        String token
) {
}
