package com.claseya.favorite.repository;

import com.claseya.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Favorites are read through a {@link org.springframework.data.jpa.domain.Specification} so the
 * public visibility rule (D1) is the canonical {@code TeacherSpecifications.visibleTeacher} instead
 * of a second copy of the predicates (ARCH-01). A duplicated rule is how PRIV-01 and BUG-03
 * happened.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, UUID>,
        JpaSpecificationExecutor<Favorite> {

    Optional<Favorite> findByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);

    boolean existsByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);
}
