package com.claseya.safety.dto;

import com.claseya.model.Report;
import com.claseya.model.enums.ReportReason;
import com.claseya.model.enums.ReportStatus;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        ReportStatus status,
        ReportReason reason,
        Instant createdAt
) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getStatus(),
                report.getReason(),
                report.getCreatedAt());
    }
}
