package com.claseya.academic.repository;

import com.claseya.model.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByIdAndActiveTrue(UUID id);

    Optional<Subject> findByNormalizedName(String normalizedName);

    boolean existsBySlug(String slug);

    /** REVIEW2-001 (RF-2): the full catalog is never sent in one response. */
    Page<Subject> findByActiveTrue(Pageable pageable);

    /** REVIEW2-001 (RF-3): name search, paginated. */
    Page<Subject> findByActiveTrueAndNameContainingIgnoreCase(String query, Pageable pageable);

    /**
     * REVIEW2-001 (RF-1): most used active subjects, i.e. those with at least one active
     * {@code teacher_subjects} link owned by an ACTIVE user. One aggregated query, no N+1; the
     * tie-breaker is the name so the landing order is stable.
     */
    @Query(value = """
            select s.id, s.name, s.normalized_name, s.slug, s.description, s.active,
                   s.created_at, s.updated_at
            from subjects s
            join career_subjects cs on cs.subject_id = s.id and cs.active
            join teacher_subjects ts on ts.career_subject_id = cs.id and ts.active
            join teacher_profiles tp on tp.id = ts.teacher_id
            join users u on u.id = tp.user_id
            where s.active and u.status = 'ACTIVE'
            group by s.id
            order by count(distinct ts.teacher_id) desc, s.name asc
            limit :limit
            """, nativeQuery = true)
    List<Subject> findPopular(@Param("limit") int limit);
}
