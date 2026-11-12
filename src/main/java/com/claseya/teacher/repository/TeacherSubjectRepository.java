package com.claseya.teacher.repository;

import com.claseya.model.TeacherSubject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, UUID> {

    /** Own listing: decide between the career link and the direct subject without N+1. */
    @EntityGraph(attributePaths = {
            "careerSubject",
            "careerSubject.subject",
            "careerSubject.career",
            "subject"
    })
    List<TeacherSubject> findByTeacher_Id(UUID teacherId);

    boolean existsByTeacher_IdAndCareerSubject_Id(UUID teacherId, UUID careerSubjectId);

    boolean existsByTeacher_IdAndSubject_Id(UUID teacherId, UUID subjectId);

    boolean existsByTeacher_IdAndActiveTrue(UUID teacherId);

    Optional<TeacherSubject> findByTeacher_IdAndCareerSubject_Id(UUID teacherId, UUID careerSubjectId);

    Optional<TeacherSubject> findByIdAndTeacher_User_Id(UUID id, UUID userId);

    /**
     * Batch fetch of both subject shapes for public search/detail. Legacy rows resolve through
     * CareerSubject; direct rows resolve through Subject. A constant number of queries per page.
     */
    @Query("""
            select ts from TeacherSubject ts
            left join fetch ts.careerSubject cs
            left join fetch cs.subject csSubject
            left join fetch cs.career c
            left join fetch c.academicUnit au
            left join fetch au.university
            left join fetch ts.subject directSubject
            where ts.teacher.id in :teacherIds
              and ts.active = true
              and (
                (ts.careerSubject is not null and cs.active = true and c.active = true and au.active = true)
                or (ts.subject is not null and directSubject.active = true)
              )
            """)
    List<TeacherSubject> findActiveWithDetailsByTeacherIds(@Param("teacherIds") Collection<UUID> teacherIds);
}
