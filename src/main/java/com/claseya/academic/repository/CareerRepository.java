package com.claseya.academic.repository;

import com.claseya.model.Career;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareerRepository extends JpaRepository<Career, UUID> {

    List<Career> findByAcademicUnit_IdAndActiveTrueOrderByNameAsc(UUID academicUnitId);

    Optional<Career> findByIdAndActiveTrue(UUID id);

    boolean existsBySlug(String slug);

    boolean existsByAcademicUnit_IdAndCode(UUID academicUnitId, String code);

    boolean existsByIdAndAcademicUnit_University_Id(UUID id, UUID universityId);
}
