package com.claseya.teacher.repository;

import com.claseya.model.TeacherSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, UUID> {

    List<TeacherSubject> findByTeacher_Id(UUID teacherId);

    boolean existsByTeacher_IdAndCareerSubject_Id(UUID teacherId, UUID careerSubjectId);

    Optional<TeacherSubject> findByTeacher_IdAndCareerSubject_Id(UUID teacherId, UUID careerSubjectId);

    /**
     * Batch fetch of the whole subject graph (subject, career, academic unit,
     * university) for a set of teachers. Keeps public search/detail mapping to a
     * constant number of queries instead of N+1 lazy loads.
     */
    @Query("""
            select ts from TeacherSubject ts
            join fetch ts.careerSubject cs
            join fetch cs.subject
            join fetch cs.career c
            join fetch c.academicUnit au
            join fetch au.university
            where ts.teacher.id in :teacherIds
              and ts.active = true
              and cs.active = true
              and c.active = true
              and au.active = true
            """)
    List<TeacherSubject> findActiveWithDetailsByTeacherIds(@Param("teacherIds") Collection<UUID> teacherIds);
}
