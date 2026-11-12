package com.claseya.safety.controller;

import com.claseya.common.dto.SearchResultPage;
import com.claseya.model.enums.ReportStatus;
import com.claseya.safety.dto.AdminReportResponse;
import com.claseya.safety.dto.ResolveReportRequest;
import com.claseya.safety.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** REPORT-001: admin report queue. The route is ADMIN-only in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public SearchResultPage<AdminReportResponse> list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportService.adminList(status, page, size);
    }

    @PatchMapping("/{reportId}/resolution")
    public ResponseEntity<Void> resolve(@PathVariable UUID reportId,
                                        @Valid @RequestBody ResolveReportRequest request) {
        reportService.resolve(reportId, request);
        return ResponseEntity.noContent().build();
    }
}
