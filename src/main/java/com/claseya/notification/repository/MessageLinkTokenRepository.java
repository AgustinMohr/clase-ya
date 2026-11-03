package com.claseya.notification.repository;

import com.claseya.model.MessageLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MessageLinkTokenRepository extends JpaRepository<MessageLinkToken, UUID> {

    Optional<MessageLinkToken> findByTokenHash(String tokenHash);

    /**
     * Single-use guarantee: only the exchange that flips {@code used_at} from null wins, even if
     * two clicks arrive at the same time.
     */
    @Modifying
    @Query("update MessageLinkToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
