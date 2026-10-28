package com.claseya.auth.repository;

import com.claseya.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    List<EmailVerificationToken> findByUser_Id(UUID userId);

    List<EmailVerificationToken> findByUser_IdAndUsedAtIsNull(UUID userId);
}
