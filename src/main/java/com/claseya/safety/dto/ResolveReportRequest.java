package com.claseya.safety.dto;

import com.claseya.model.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record ResolveReportRequest(@NotNull ReportStatus status) {
}
