package com.claseya.favorite.repository;

import com.claseya.model.Favorite;
import com.claseya.model.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    Optional<Favorite> findByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);

    boolean existsByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);

    /**
     * Listing applies the same public visibility rule as teacher search (D1):
     * ACTIVE user + complete announcement (name, bio, active subject, modality, availability).
     * Keep these predicates in sync with {@code TeacherSpecifications.visible()}; the regression
     * test {@code FavoriteIntegrationTest} covers the state-change case.
     */
    @Query(value = """
            select f from Favorite f
            where f.student.id = :studentId
              and f.teacher.user.status = :activeStatus
              and f.teacher.user.name is not null
              and trim(f.teacher.user.name) <> ''
              and f.teacher.bio is not null
              and trim(f.teacher.bio) <> ''
              and exists (select 1 from TeacherSubject ts
                          where ts.teacher = f.teacher and ts.active = true)
              and exists (select 1 from TeacherModality tm where tm.teacher = f.teacher)
              and exists (select 1 from AvailabilityWindow aw where aw.teacher = f.teacher)
            """,
            countQuery = """
            select count(f) from Favorite f
            where f.student.id = :studentId
              and f.teacher.user.status = :activeStatus
              and f.teacher.user.name is not null
              and trim(f.teacher.user.name) <> ''
              and f.teacher.bio is not null
              and trim(f.teacher.bio) <> ''
              and exists (select 1 from TeacherSubject ts
                          where ts.teacher = f.teacher and ts.active = true)
              and exists (select 1 from TeacherModality tm where tm.teacher = f.teacher)
              and exists (select 1 from AvailabilityWindow aw where aw.teacher = f.teacher)
            """)
    Page<Favorite> findVisibleByStudent(@Param("studentId") UUID studentId,
                                        @Param("activeStatus") UserStatus activeStatus,
                                        Pageable pageable);
}
