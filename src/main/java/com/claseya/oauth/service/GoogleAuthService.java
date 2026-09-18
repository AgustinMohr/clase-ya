package com.claseya.oauth.service;

import com.claseya.auth.dto.LoginResponse;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ConflictException;
import com.claseya.common.exception.InvalidCredentialsException;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.oauth.dto.GoogleLoginRequest;
import com.claseya.security.AppUserDetails;
import com.claseya.security.JwtService;
import com.claseya.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public GoogleAuthService(GoogleIdTokenVerifier verifier,
                             UserRepository userRepository,
                             JwtService jwtService,
                             PasswordEncoder passwordEncoder) {
        this.verifier = verifier;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdTokenVerifier.GoogleClaims claims = verifier.verify(request.idToken());
        String email = normalize(claims.email());

        Optional<User> bySub = userRepository.findByGoogleSub(claims.sub());
        Optional<User> byEmail = userRepository.findByEmail(email);

        User user;
        if (bySub.isPresent()) {
            if (!bySub.get().getEmail().equals(email)) {
                throw new ConflictException("Google account email does not match the linked account");
            }
            user = bySub.get();
        } else if (byEmail.isPresent()) {
            user = byEmail.get();
            // Only ACTIVE accounts may be linked; never link/activate a disabled one.
            requireActive(user);
            if (user.getGoogleSub() != null) {
                throw new ConflictException("This email is already linked to another Google account");
            }
            user.setGoogleSub(claims.sub());
        } else {
            user = createFromGoogle(request, claims, email);
        }

        requireActive(user);
        return issueToken(user);
    }

    private User createFromGoogle(GoogleLoginRequest request,
                                  GoogleIdTokenVerifier.GoogleClaims claims,
                                  String email) {
        UserRole role = request.role();
        if (role == null) {
            throw new BadRequestException("role is required for a new account");
        }
        if (role == UserRole.ADMIN) {
            throw new AccessDeniedException("ADMIN cannot be registered through Google");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(claims.name() != null ? claims.name() : email);
        // Google already verified the email: the account starts ACTIVE.
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        user.setGoogleSub(claims.sub());
        // Unusable random BCrypt so a Google-only account can never log in by password.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Google account or email already exists");
        }
    }

    private void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
    }

    private LoginResponse issueToken(User user) {
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        String token = jwtService.generateToken(new AppUserDetails(user));
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
