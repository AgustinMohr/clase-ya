package com.claseya.auth.service;

import com.claseya.auth.dto.LoginRequest;
import com.claseya.auth.dto.LoginResponse;
import com.claseya.auth.dto.RegisterRequest;
import com.claseya.auth.dto.RegisterResponse;
import com.claseya.common.exception.EmailAlreadyExistsException;
import com.claseya.common.exception.InvalidCredentialsException;
import com.claseya.common.exception.InvalidRoleException;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.security.AppUserDetails;
import com.claseya.security.JwtService;
import com.claseya.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (request.role() == UserRole.ADMIN) {
            throw new InvalidRoleException("ADMIN cannot self-register");
        }

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        // No email-verification flow in this phase: accounts start PENDING and are
        // only able to authenticate once an admin flips them to ACTIVE.
        user.setStatus(UserStatus.PENDING);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Race-safety: rely on the unique constraint as the final guard.
            throw new EmailAlreadyExistsException();
        }

        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            // Generic message to avoid account enumeration.
            throw new InvalidCredentialsException();
        }

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();

        User managed = userRepository.findById(principal.getId())
                .orElseThrow(InvalidCredentialsException::new);
        managed.setLastLoginAt(Instant.now());
        userRepository.save(managed);

        String token = jwtService.generateToken(principal);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
