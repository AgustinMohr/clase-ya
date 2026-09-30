package com.claseya.verification.repository;

import com.claseya.model.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, UUID> {

    List<VerificationDocument> findByTeacherEducation_IdOrderByUploadedAtDesc(UUID teacherEducationId);

    long countByTeacherEducation_Id(UUID teacherEducationId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from VerificationDocument d where d.teacherEducation.id = :educationId")
    long sumSizeBytesByEducation(@Param("educationId") UUID educationId);
}
