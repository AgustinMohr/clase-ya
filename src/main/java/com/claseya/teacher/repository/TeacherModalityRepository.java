package com.claseya.teacher.repository;

import com.claseya.model.TeacherModality;
import com.claseya.model.enums.TeachingModality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherModalityRepository extends JpaRepository<TeacherModality, UUID> {

    List<TeacherModality> findByTeacher_Id(UUID teacherId);

    List<TeacherModality> findByTeacher_IdIn(Collection<UUID> teacherIds);

    boolean existsByTeacher_IdAndModality(UUID teacherId, TeachingModality modality);

    Optional<TeacherModality> findByTeacher_IdAndModality(UUID teacherId, TeachingModality modality);
}
