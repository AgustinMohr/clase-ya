package com.claseya.verification.dto;

import com.claseya.model.enums.VerificationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Revocation of the last verified credential; method and reason are both mandatory. */
public record VerificationRevokeRequest(
        @NotNull(message = "method is required")
        VerificationMethod method,

        @NotBlank(message = "reason is required")
        @Size(max = 2000, message = "reason must be at most 2000 characters")
        String reason
) {
}
