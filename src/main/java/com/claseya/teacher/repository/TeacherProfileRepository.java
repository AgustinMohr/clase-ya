package com.claseya.teacher.repository;

import com.claseya.model.TeacherProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID>,
        JpaSpecificationExecutor<TeacherProfile> {

    /** The public display name lives on the user; fetch it with the profile (ADR-028, option A). */
    @EntityGraph(attributePaths = "user")
    Optional<TeacherProfile> findByUser_Id(UUID userId);

    boolean existsByUser_Id(UUID userId);

    /**
     * PERF-03: the card assembler reads {@code teacher.user.name}, so the search page loads the user
     * in the same query instead of re-fetching every profile afterwards.
     */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<TeacherProfile> findAll(Specification<TeacherProfile> spec, Pageable pageable);

    /**
     * Loads the profile under a pessimistic write lock. Verification decisions serialize on this
     * row so that (a) concurrent decisions on the same credential can never be last-write-wins and
     * (b) the derived profile status (RF-14) stays consistent with its credentials.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TeacherProfile t where t.id = :id")
    Optional<TeacherProfile> findByIdForUpdate(@Param("id") UUID id);
}
