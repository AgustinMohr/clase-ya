package com.claseya.safety.repository;

import com.claseya.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    boolean existsByBlocker_IdAndBlocked_Id(UUID blockerId, UUID blockedId);

    /** One query answers "can these two users contact each other?" in either direction. */
    @Query("""
            select count(b) > 0 from UserBlock b
            where (b.blocker.id = :firstId and b.blocked.id = :secondId)
               or (b.blocker.id = :secondId and b.blocked.id = :firstId)
            """)
    boolean existsBetween(@Param("firstId") UUID firstId, @Param("secondId") UUID secondId);

    @Query("""
            select b from UserBlock b
            join fetch b.blocked
            where b.blocker.id = :blockerId
            order by b.createdAt desc, b.id
            """)
    List<UserBlock> findBlockedByBlockerId(@Param("blockerId") UUID blockerId);

    @Modifying
    @Query("delete from UserBlock b where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    int deletePair(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);
}
