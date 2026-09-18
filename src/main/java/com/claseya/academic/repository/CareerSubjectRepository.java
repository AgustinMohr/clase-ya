package com.claseya.academic.repository;

import com.claseya.model.CareerSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareerSubjectRepository extends JpaRepository<CareerSubject, UUID> {

    List<CareerSubject> findByCareer_IdAndActiveTrueOrderByYearAsc(UUID careerId);

    Optional<CareerSubject> findByIdAndActiveTrue(UUID id);

    boolean existsByCareer_IdAndSubject_Id(UUID careerId, UUID subjectId);
}
