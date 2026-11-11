package com.claseya.auth.service;

import com.claseya.auth.repository.PasswordResetTokenRepository;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ServiceUnavailableException;
import com.claseya.common.util.Hashes;
import com.claseya.model.PasswordResetToken;
import com.claseya.model.User;
import com.claseya.model.enums.UserStatus;
import com.claseya.notification.service.EmailSender;
import com.claseya.notification.service.NotificationOutboxService;
import com.claseya.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * AUTH-003: single-use password reset by email.
 *
 * <p>The request endpoint always answers 202 and never reveals whether the email exists. Only an
 * ACTIVE account receives a new link; its previous outstanding tokens are invalidated. The raw
 * token only exists in the email/outbox payload; the database keeps its SHA-256 hash.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String INVALID_LINK = "The reset link is invalid or has expired";

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationOutboxService outbox;
    private final EmailSender emailSender;
    private final Duration tokenTtl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                NotificationOutboxService outbox,
                                EmailSender emailSender,
                                @Value("${app.auth.password-reset.token-ttl:PT30M}") Duration tokenTtl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.outbox = outbox;
        this.emailSender = emailSender;
        this.tokenTtl = tokenTtl;
    }

    /** Always succeeds for the caller; a missing email or transport failure never enumerates. */
    @Transactional
    public void request(String rawEmail) {
        if (!emailSender.isConfigured()) {
            throw new ServiceUnavailableException("Email delivery is not configured");
        }
        String email = normalize(rawEmail);
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(this::issue);
    }

    /** Consumes the token and replaces the password. All failures share one generic message. */
    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidLink();
        }
        PasswordResetToken token = tokenRepository
                .findByTokenHash(Hashes.sha256Hex(rawToken.trim()))
                .orElseThrow(PasswordResetService::invalidLink);

        Instant now = Instant.now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidLink();
        }
        if (tokenRepository.consume(token.getId(), now) == 0) {
            throw invalidLink();
        }

        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidLink();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void issue(User user) {
        Instant now = Instant.now();
        tokenRepository.findByUser_IdAndUsedAtIsNull(user.getId())
                .forEach(existing -> existing.setUsedAt(now));

        String rawToken = newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(Hashes.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(tokenTtl));
        tokenRepository.saveAndFlush(token);

        outbox.enqueuePasswordReset(user.getId(), user.getEmail(), token.getId(), rawToken);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static BadRequestException invalidLink() {
        return new BadRequestException(INVALID_LINK);
    }
}
