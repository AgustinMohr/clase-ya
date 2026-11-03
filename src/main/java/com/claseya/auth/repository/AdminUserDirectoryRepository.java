package com.claseya.auth.repository;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.model.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for the admin directory (ADMIN-002).
 *
 * <p>One projection query for the whole page: the profile joins are left joins to 1:1 rows, so
 * there is no row duplication and no N+1. The LIKE pattern arrives already escaped; {@code !} is
 * the escape character so {@code %}, {@code _} and {@code !} cannot act as wildcards.
 */
public interface AdminUserDirectoryRepository extends Repository<User, UUID> {

    @Query(value = """
            select u.id as id, u.email as email, u.name as name, u.role as role,
                   u.status as status, u.createdAt as createdAt, u.lastLoginAt as lastLoginAt,
                   tp.verificationStatus as teacherVerificationStatus, sp.id as studentProfileId
            from User u
            left join TeacherProfile tp on tp.user = u
            left join StudentProfile sp on sp.user = u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.email) like :q escape '!'
                   or lower(u.name) like :q escape '!')
            order by u.createdAt desc, u.id asc
            """,
            countQuery = """
            select count(u)
            from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.email) like :q escape '!'
                   or lower(u.name) like :q escape '!')
            """)
    Page<AdminUserRow> search(@Param("role") UserRole role,
                              @Param("status") UserStatus status,
                              @Param("q") String likePattern,
                              Pageable pageable);

    /** Raw projection; the service turns it into the role-specific DTO. */
    interface AdminUserRow {

        UUID getId();

        String getEmail();

        String getName();

        UserRole getRole();

        UserStatus getStatus();

        Instant getCreatedAt();

        Instant getLastLoginAt();

        VerificationStatus getTeacherVerificationStatus();

        UUID getStudentProfileId();
    }
}
