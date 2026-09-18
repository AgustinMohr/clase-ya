package com.claseya.teacher.repository;

import com.claseya.model.TeacherEducation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherEducationRepository extends JpaRepository<TeacherEducation, UUID> {

    List<TeacherEducation> findByTeacher_IdOrderByStartYearDesc(UUID teacherId);

    Optional<TeacherEducation> findByIdAndTeacher_Id(UUID id, UUID teacherId);
}
