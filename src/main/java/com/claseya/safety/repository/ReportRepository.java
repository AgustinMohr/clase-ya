package com.claseya.safety.repository;

import com.claseya.model.Report;
import com.claseya.model.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporter_IdAndReportedUser_IdAndStatusIn(UUID reporterId,
                                                              UUID reportedUserId,
                                                              Collection<ReportStatus> statuses);

    /** Admin queue: both people are loaded in the same query (no N+1). */
    @Query(value = """
            select r from Report r
            join fetch r.reporter
            join fetch r.reportedUser
            where r.status = :status
            """,
            countQuery = "select count(r) from Report r where r.status = :status")
    Page<Report> findForAdminByStatus(@Param("status") ReportStatus status, Pageable pageable);
}
