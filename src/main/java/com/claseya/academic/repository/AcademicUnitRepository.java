package com.claseya.academic.repository;

import com.claseya.model.AcademicUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicUnitRepository extends JpaRepository<AcademicUnit, UUID> {

    List<AcademicUnit> findByUniversity_IdAndActiveTrueOrderByNameAsc(UUID universityId);

    Optional<AcademicUnit> findByIdAndActiveTrue(UUID id);

    boolean existsByUniversity_IdAndCode(UUID universityId, String code);

    boolean existsByUniversity_IdAndName(UUID universityId, String name);
}
