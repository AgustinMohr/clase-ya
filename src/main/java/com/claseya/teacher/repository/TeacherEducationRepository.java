package com.claseya.teacher.repository;

import com.claseya.model.TeacherEducation;
import com.claseya.model.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherEducationRepository extends JpaRepository<TeacherEducation, UUID> {

    List<TeacherEducation> findByTeacher_IdOrderByStartYearDesc(UUID teacherId);

    Optional<TeacherEducation> findByIdAndTeacher_Id(UUID id, UUID teacherId);

    List<TeacherEducation> findByTeacher_IdAndVerificationStatus(UUID teacherId, VerificationStatus verificationStatus);

    /**
     * Review queue, paginated. Callers sort by {@code submittedAt} so the oldest presentation is
     * reviewed first; the join fetch avoids an N+1 while loading teacher + user for each row.
     */
    @Query(value = "select e from TeacherEducation e join fetch e.teacher t join fetch t.user "
            + "where e.verificationStatus = :status",
            countQuery = "select count(e) from TeacherEducation e where e.verificationStatus = :status")
    Page<TeacherEducation> pageByVerificationStatus(@Param("status") VerificationStatus status, Pageable pageable);
}
