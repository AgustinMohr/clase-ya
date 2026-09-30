package com.claseya.verification.dto;

import com.claseya.model.enums.VerificationAction;
import com.claseya.model.enums.VerificationMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin decision over a credential. Only VERIFIED / REJECTED / MORE_INFO_REQUIRED are accepted;
 * the service rejects anything else (400) and requires a reason for MORE_INFO_REQUIRED.
 */
public record VerificationDecisionRequest(
        @NotNull(message = "decision is required")
        VerificationAction decision,

        @NotNull(message = "method is required")
        VerificationMethod method,

        @Size(max = 2000, message = "reason must be at most 2000 characters")
        String reason
) {
}
