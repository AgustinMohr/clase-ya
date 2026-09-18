package com.claseya.student.repository;

import com.claseya.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    Optional<StudentProfile> findByUser_Id(UUID userId);

    boolean existsByUser_Id(UUID userId);
}
