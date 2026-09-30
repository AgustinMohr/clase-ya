package com.claseya.verification.repository;

import com.claseya.model.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {

    List<VerificationDocument> findByTeacherEducation_IdOrderByUploadedAtDesc(UUID teacherEducationId);

    List<VerificationDocument> findByTeacher_IdOrderByUploadedAtDesc(UUID teacherId);

    long countByTeacherEducation_Id(UUID teacherEducationId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from VerificationDocument d where d.teacherEducation.id = :educationId")
    long sumSizeBytesByEducation(@Param("educationId") UUID educationId);

    /** Batch document counts for a page of credentials — keeps the queue free of N+1 queries. */
    @Query("select d.teacherEducation.id, count(d) from VerificationDocument d "
            + "where d.teacherEducation.id in :educationIds group by d.teacherEducation.id")
    List<Object[]> countByEducationIds(@Param("educationIds") Collection<UUID> educationIds);
}
