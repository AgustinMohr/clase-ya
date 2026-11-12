package com.claseya.auth.repository;

import com.claseya.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUser_IdAndUsedAtIsNull(UUID userId);

    @Modifying
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :now
             where t.id = :id
               and t.usedAt is null
            """)
    int consume(@Param("id") UUID id, @Param("now") Instant now);
}
