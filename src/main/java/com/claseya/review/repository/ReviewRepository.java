package com.claseya.review.repository;

import com.claseya.model.Review;
import com.claseya.model.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);

    boolean existsByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);

    @EntityGraph(attributePaths = {"student", "student.user"})
    Optional<Review> findByIdAndStudent_User_Id(UUID id, UUID studentUserId);

    @EntityGraph(attributePaths = {"student", "student.user"})
    Optional<Review> findByIdAndTeacher_User_Id(UUID id, UUID teacherUserId);

    /** Public listing: only authored reviews of one teacher, with the author loaded. */
    @Query(value = """
            select r from Review r
            join fetch r.student s
            join fetch s.user
            where r.teacher.id = :teacherId and r.status = :status
            """,
            countQuery = """
            select count(r) from Review r
            where r.teacher.id = :teacherId and r.status = :status
            """)
    Page<Review> findByTeacherAndStatusWithAuthor(@Param("teacherId") UUID teacherId,
                                                  @Param("status") ReviewStatus status,
                                                  Pageable pageable);

    /** Admin listing with both people loaded (no N+1). */
    @Query(value = """
            select r from Review r
            join fetch r.student s
            join fetch s.user
            join fetch r.teacher t
            join fetch t.user
            left join fetch r.moderatedBy
            where r.status = :status
            """,
            countQuery = """
            select count(r) from Review r
            where r.status = :status
            """)
    Page<Review> findForAdminByStatus(@Param("status") ReviewStatus status, Pageable pageable);

    @Query(value = """
            select r from Review r
            join fetch r.student s
            join fetch s.user
            join fetch r.teacher t
            join fetch t.user
            left join fetch r.moderatedBy
            """,
            countQuery = "select count(r) from Review r")
    Page<Review> findAllForAdmin(Pageable pageable);

    @Query("""
            select avg(r.rating) as average, count(r) as total
            from Review r
            where r.teacher.id = :teacherId and r.status = :status
            """)
    RatingAggregate aggregateForTeacher(@Param("teacherId") UUID teacherId,
                                        @Param("status") ReviewStatus status);

    interface RatingAggregate {

        Double getAverage();

        Long getTotal();
    }
}
