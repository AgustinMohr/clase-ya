package com.claseya.safety.service;

import com.claseya.common.dto.SearchResultPage;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.Report;
import com.claseya.model.User;
import com.claseya.model.enums.ReportStatus;
import com.claseya.safety.dto.AdminReportResponse;
import com.claseya.safety.dto.CreateReportRequest;
import com.claseya.safety.dto.ReportResponse;
import com.claseya.safety.dto.ResolveReportRequest;
import com.claseya.safety.repository.ReportRepository;
import com.claseya.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final List<ReportStatus> OPEN_STATUSES =
            List.of(ReportStatus.OPEN, ReportStatus.REVIEWING);

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReportResponse create(UUID reporterId, CreateReportRequest request) {
        if (reporterId.equals(request.reportedUserId())) {
            throw new BadRequestException("You cannot report yourself");
        }
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(ResourceNotFoundException::new);
        User reported = userRepository.findById(request.reportedUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Reported user not found"));
        if (reportRepository.existsByReporter_IdAndReportedUser_IdAndStatusIn(
                reporterId, reported.getId(), OPEN_STATUSES)) {
            throw new ConflictException("An open report already exists for this user");
        }

        Report report = new Report();
        report.setReporter(reporter);
        report.setReportedUser(reported);
        report.setReason(request.reason());
        report.setDescription(trimToNull(request.description()));
        report.setStatus(ReportStatus.OPEN);
        return ReportResponse.from(reportRepository.saveAndFlush(report));
    }

    @Transactional(readOnly = true)
    public SearchResultPage<AdminReportResponse> adminList(ReportStatus status, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        ReportStatus filter = status == null ? ReportStatus.OPEN : status;
        Pageable pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<Report> reports = reportRepository.findForAdminByStatus(filter, pageable);
        return SearchResultPage.of(
                reports.getContent().stream().map(AdminReportResponse::from).toList(),
                reports.getNumber(), reports.getSize(), reports.getTotalElements());
    }

    @Transactional
    public void resolve(UUID reportId, ResolveReportRequest request) {
        if (request.status() != ReportStatus.RESOLVED
                && request.status() != ReportStatus.REJECTED) {
            throw new BadRequestException("A report can only be resolved or rejected");
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!OPEN_STATUSES.contains(report.getStatus())) {
            throw new ConflictException("The report is already resolved");
        }
        report.setStatus(request.status());
        report.setResolvedAt(Instant.now());
        reportRepository.saveAndFlush(report);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
