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

    /** SEARCH-003: accent-insensitive name search for the autocomplete. */
    @Query(value = """
            select * from subjects s
            where s.active = true
              and unaccent(lower(s.name)) like '%' || unaccent(lower(:query)) || '%'
            order by s.name asc, s.id asc
            """, countQuery = """
            select count(*) from subjects s
            where s.active = true
              and unaccent(lower(s.name)) like '%' || unaccent(lower(:query)) || '%'
            """, nativeQuery = true)
    Page<Subject> searchActiveByNameIgnoringAccents(@Param("query") String query, Pageable pageable);

    /**
     * REVIEW2-001 (RF-1): most used active subjects, i.e. those with at least one active
     * {@code teacher_subjects} link owned by an ACTIVE user. One aggregated query, no N+1; the
     * tie-breaker is the name so the landing order is stable.
     */
    @Query(value = """
            select s.id, s.name, s.normalized_name, s.slug, s.description, s.active,
                   s.created_at, s.updated_at
            from subjects s
            join (
                select ts.teacher_id as teacher_id, cs.subject_id as subject_id
                from teacher_subjects ts
                join career_subjects cs on cs.id = ts.career_subject_id
                where ts.active and cs.active
                union
                select ts.teacher_id, ts.subject_id
                from teacher_subjects ts
                where ts.active and ts.subject_id is not null
            ) links on links.subject_id = s.id
            join teacher_profiles tp on tp.id = links.teacher_id
            join users u on u.id = tp.user_id
            where s.active and u.status = 'ACTIVE'
            group by s.id
            order by count(distinct links.teacher_id) desc, s.name asc
            limit :limit
            """, nativeQuery = true)
    List<Subject> findPopular(@Param("limit") int limit);
}
