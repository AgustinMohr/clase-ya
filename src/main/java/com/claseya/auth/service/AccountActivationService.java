package com.claseya.auth.service;

import com.claseya.auth.repository.EmailVerificationTokenRepository;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.EmailVerificationToken;
import com.claseya.model.User;
import com.claseya.model.enums.UserStatus;
import com.claseya.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * AUTH-001: issues, validates and consumes single-use email-activation tokens.
 *
 * <p>The raw token only exists in memory and in the emailed link; the database keeps its SHA-256
 * digest. Resending invalidates every outstanding token of that account.
 */
@Service
public class AccountActivationService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String INVALID_LINK = "The activation link is invalid or has expired";

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ActivationMailSender mailSender;
    private final String baseUrl;
    private final Duration tokenTtl;

    public AccountActivationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    ActivationMailSender mailSender,
                                    @Value("${app.auth.activation.base-url:http://localhost:5173}") String baseUrl,
                                    @Value("${app.auth.activation.token-ttl:PT24H}") Duration tokenTtl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.tokenTtl = tokenTtl;
    }

    /** Creates a fresh token for a pending account and sends the link. Joins the caller transaction. */
    @Transactional
    public void issueAndSend(User user) {
        Instant now = Instant.now();
        // Invalidate outstanding tokens through the persistence context (not a bulk update) so a
        // caller that later reads them in the same transaction cannot see a stale used_at.
        tokenRepository.findByUser_IdAndUsedAtIsNull(user.getId())
                .forEach(existing -> existing.setUsedAt(now));

        String rawToken = newToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(now.plus(tokenTtl));
        tokenRepository.save(token);

        mailSender.sendActivation(user.getEmail(), activationLink(rawToken));
    }

    /** Consumes a token and activates the account. All failure modes share one generic message. */
    @Transactional
    public void activate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidLink();
        }
        EmailVerificationToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken.trim()))
                .orElseThrow(AccountActivationService::invalidLink);

        Instant now = Instant.now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidLink();
        }

        User user = token.getUser();
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerifiedAt(now);
            userRepository.save(user);
        } else if (user.getStatus() == UserStatus.ACTIVE) {
            // Already active (e.g. an admin activated it manually): the click still proves the email.
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(now);
                userRepository.save(user);
            }
        } else {
            throw new BadRequestException("This account cannot be activated");
        }

        token.setUsedAt(now);
        tokenRepository.save(token);
    }

    /**
     * Anti-enumeration: the caller always answers 202. Only a PENDING account receives a new link.
     */
    @Transactional
    public void resend(String rawEmail) {
        String email = normalize(rawEmail);
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.PENDING)
                .ifPresent(this::issueAndSend);
    }

    /** Operational escape hatch for ADMIN when email delivery is broken. Does not verify the email. */
    @Transactional
    public void activateManually(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(ResourceNotFoundException::new);
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        if (user.getStatus() != UserStatus.PENDING) {
            throw new ConflictException("Only a pending account can be activated manually");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    private String activationLink(String rawToken) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return base + "/activar?token=" + rawToken;
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static BadRequestException invalidLink() {
        return new BadRequestException(INVALID_LINK);
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
