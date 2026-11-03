package com.claseya.student.repository;

import com.claseya.model.StudentProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    /** Contact/favorite responses read the student name; fetch it with the profile. */
    @EntityGraph(attributePaths = "user")
    Optional<StudentProfile> findByUser_Id(UUID userId);

    boolean existsByUser_Id(UUID userId);
}
