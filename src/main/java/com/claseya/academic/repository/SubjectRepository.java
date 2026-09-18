package com.claseya.academic.repository;

import com.claseya.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findByActiveTrueOrderByNameAsc();

    List<Subject> findByActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String query);

    Optional<Subject> findByIdAndActiveTrue(UUID id);

    Optional<Subject> findByNormalizedName(String normalizedName);

    boolean existsBySlug(String slug);
}
