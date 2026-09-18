package com.claseya.academic.repository;

import com.claseya.model.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UniversityRepository extends JpaRepository<University, UUID> {

    List<University> findByActiveTrueOrderByNameAsc();

    Optional<University> findByIdAndActiveTrue(UUID id);

    boolean existsBySlug(String slug);
}
