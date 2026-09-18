package com.claseya.availability.repository;

import com.claseya.model.AvailabilityWindow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AvailabilityWindowRepository
        extends JpaRepository<AvailabilityWindow, UUID>,
        JpaSpecificationExecutor<AvailabilityWindow> {

    Page<AvailabilityWindow> findByTeacher_Id(UUID teacherId, Pageable pageable);

    Optional<AvailabilityWindow> findByIdAndTeacher_User_Id(UUID id, UUID userId);

    @Query("""
            select case when count(w) > 0 then true else false end
            from AvailabilityWindow w
            where w.teacher.id = :teacherId
              and w.dayOfWeek = :day
              and w.startMinutes < :end
              and w.endMinutes > :start
            """)
    boolean overlaps(@Param("teacherId") UUID teacherId,
                     @Param("day") int day,
                     @Param("start") int start,
                     @Param("end") int end);

    @Query("""
            select case when count(w) > 0 then true else false end
            from AvailabilityWindow w
            where w.teacher.id = :teacherId
              and w.dayOfWeek = :day
              and w.startMinutes < :end
              and w.endMinutes > :start
              and w.id <> :excludeId
            """)
    boolean overlapsExcluding(@Param("teacherId") UUID teacherId,
                              @Param("day") int day,
                              @Param("start") int start,
                              @Param("end") int end,
                              @Param("excludeId") UUID excludeId);
}
