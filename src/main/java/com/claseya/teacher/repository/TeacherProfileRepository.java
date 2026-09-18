package com.claseya.teacher.repository;

import com.claseya.model.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID>,
        JpaSpecificationExecutor<TeacherProfile> {

    Optional<TeacherProfile> findByUser_Id(UUID userId);

    boolean existsByUser_Id(UUID userId);

    @Query("select distinct t from TeacherProfile t join fetch t.user where t.id in :ids")
    List<TeacherProfile> findWithUserByIds(@Param("ids") Collection<UUID> ids);
}
