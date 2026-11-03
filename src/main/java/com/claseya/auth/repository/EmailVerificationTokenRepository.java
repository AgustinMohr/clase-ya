package com.claseya.auth.repository;

import com.claseya.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * SEC-02: single-use consumption at the database level. Two concurrent clicks can both read
     * {@code used_at IS NULL}; only one UPDATE matches, and its row count decides the winner.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update EmailVerificationToken t
               set t.usedAt = :now
             where t.id = :id
               and t.usedAt is null
               and t.expiresAt > :now
            """)
    int consume(@Param("id") UUID id, @Param("now") Instant now);

    List<EmailVerificationToken> findByUser_Id(UUID userId);

    List<EmailVerificationToken> findByUser_IdAndUsedAtIsNull(UUID userId);
}
