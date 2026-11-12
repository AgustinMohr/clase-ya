package com.claseya.safety.dto;

import com.claseya.model.Report;
import com.claseya.model.enums.ReportReason;
import com.claseya.model.enums.ReportStatus;

import java.time.Instant;
import java.util.UUID;

/** Admin queue item: display names only, never emails. */
public record AdminReportResponse(
        UUID id,
        String reporterDisplayName,
        String reportedDisplayName,
        ReportReason reason,
        String description,
        ReportStatus status,
        Instant createdAt,
        Instant resolvedAt
) {

    public static AdminReportResponse from(Report report) {
        return new AdminReportResponse(
                report.getId(),
                report.getReporter().getName(),
                report.getReportedUser().getName(),
                report.getReason(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedAt());
    }
}
