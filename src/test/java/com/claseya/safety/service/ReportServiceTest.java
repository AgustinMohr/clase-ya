package com.claseya.safety.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.model.Report;
import com.claseya.model.User;
import com.claseya.model.enums.ReportReason;
import com.claseya.model.enums.ReportStatus;
import com.claseya.safety.dto.CreateReportRequest;
import com.claseya.safety.dto.ResolveReportRequest;
import com.claseya.safety.repository.ReportRepository;
import com.claseya.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void rejectsSelfReportWithoutTouchingTheDatabase() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> reportService.create(userId,
                new CreateReportRequest(userId, ReportReason.SPAM, null)))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsAnOpenDuplicateForTheSamePair() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(user(reporterId)));
        when(userRepository.findById(reportedId)).thenReturn(Optional.of(user(reportedId)));
        when(reportRepository.existsByReporter_IdAndReportedUser_IdAndStatusIn(
                eq(reporterId), eq(reportedId), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> reportService.create(reporterId,
                new CreateReportRequest(reportedId, ReportReason.HARASSMENT, "Insultos")))
                .isInstanceOf(ConflictException.class);

        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsAnOpenReportAndTrimsTheDescription() {
        UUID reporterId = UUID.randomUUID();
        UUID reportedId = UUID.randomUUID();
        User reporter = user(reporterId);
        User reported = user(reportedId);
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(reportedId)).thenReturn(Optional.of(reported));
        when(reportRepository.existsByReporter_IdAndReportedUser_IdAndStatusIn(
                eq(reporterId), eq(reportedId), anyCollection())).thenReturn(false);
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var response = reportService.create(reporterId,
                new CreateReportRequest(reportedId, ReportReason.INAPPROPRIATE, "  texto  "));

        assertThat(response.status()).isEqualTo(ReportStatus.OPEN);
        assertThat(response.reason()).isEqualTo(ReportReason.INAPPROPRIATE);
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("texto");
        assertThat(captor.getValue().getReporter()).isSameAs(reporter);
        assertThat(captor.getValue().getReportedUser()).isSameAs(reported);
    }

    @Test
    void resolveOnlyAcceptsTerminalStatuses() {
        UUID reportId = UUID.randomUUID();

        assertThatThrownBy(() -> reportService.resolve(reportId,
                new ResolveReportRequest(ReportStatus.OPEN)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolveMarksTheReportAndItsTimestamp() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.OPEN);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        reportService.resolve(report.getId(), new ResolveReportRequest(ReportStatus.RESOLVED));

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveRejectsAnAlreadyResolvedReport() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.REJECTED);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.resolve(report.getId(),
                new ResolveReportRequest(ReportStatus.RESOLVED)))
                .isInstanceOf(ConflictException.class);
    }

    private static User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setName("User");
        return user;
    }
}
