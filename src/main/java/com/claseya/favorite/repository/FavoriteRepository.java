package com.claseya.favorite.repository;

import com.claseya.model.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    Optional<Favorite> findByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);

    boolean existsByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);

    Page<Favorite> findByStudent_Id(UUID studentId, Pageable pageable);
}
