package com.claseya.safety.dto;

import com.claseya.model.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReportRequest(
        @NotNull UUID reportedUserId,
        @NotNull ReportReason reason,
        @Size(max = 1000) String description
) {
}
