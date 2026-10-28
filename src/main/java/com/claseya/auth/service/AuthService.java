package com.claseya.auth.service;

import com.claseya.auth.dto.IssuedSession;
import com.claseya.auth.dto.LoginRequest;
import com.claseya.auth.dto.RegisterRequest;
import com.claseya.auth.dto.SessionUserResponse;
import com.claseya.common.exception.InvalidCredentialsException;
import com.claseya.common.exception.InvalidRoleException;
import com.claseya.common.exception.RegistrationConflictException;
import com.claseya.common.exception.ResourceNotFoundException;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.security.AppUserDetails;
import com.claseya.security.CurrentUser;
import com.claseya.security.JwtService;
import com.claseya.security.ratelimit.LoginAttemptService;
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
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final CurrentUser currentUser;
    private final AccountActivationService accountActivationService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       LoginAttemptService loginAttemptService,
                       CurrentUser currentUser,
                       AccountActivationService accountActivationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.currentUser = currentUser;
        this.accountActivationService = accountActivationService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (request.role() == UserRole.ADMIN) {
            throw new InvalidRoleException("ADMIN cannot self-register");
        }

        // SEC-04: the response is always 202, whether the email is new, pending or already active.
        // An existing pending account only gets a new activation link; the password is never
        // changed here because the caller may not own the email.
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            existing.filter(user -> user.getStatus() == UserStatus.PENDING)
                    .ifPresent(accountActivationService::issueAndSend);
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        // Accounts start PENDING and become ACTIVE through the activation link (AUTH-001) or an
        // explicit ADMIN action.
        user.setStatus(UserStatus.PENDING);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent registration for the same email: the request that won owns the flow.
            // The handler answers 202 as well, so the status never reveals existence.
            throw new RegistrationConflictException();
        }

        // AUTH-001: the account stays PENDING until the owner clicks the emailed link. The send
        // happens inside this transaction on purpose: if there is no mail transport the account is
        // rolled back instead of leaving an account that the owner cannot activate.
        accountActivationService.issueAndSend(user);
    }

    /** Consumes a single-use activation token (AUTH-001). */
    @Transactional
    public void activate(String token) {
        accountActivationService.activate(token);
    }

    /** Always succeeds from the caller's point of view; never reveals whether the email exists. */
    @Transactional
    public void resendActivation(String email) {
        accountActivationService.resend(email);
    }

    @Transactional
    public IssuedSession login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        // RATE-001: block before BCrypt when the account bucket is exhausted. The counter is
        // keyed by email for every account (existing or not), so a 429 never reveals existence.
        if (loginAttemptService.isBlocked(email)) {
            throw loginAttemptService.blockedException(email);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(email);
            // Generic message to avoid account enumeration.
            throw new InvalidCredentialsException();
        }

        loginAttemptService.reset(email);
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();

        User managed = userRepository.findById(principal.getId())
                .orElseThrow(InvalidCredentialsException::new);
        managed.setLastLoginAt(Instant.now());
        userRepository.save(managed);

        String token = jwtService.generateToken(principal);
        return new IssuedSession(token, SessionUserResponse.from(managed), jwtService.getExpirationSeconds());
    }

    /** Current session user for the SPA. Identity always comes from the SecurityContext. */
    @Transactional(readOnly = true)
    public SessionUserResponse me() {
        User user = userRepository.findById(currentUser.id())
                .orElseThrow(ResourceNotFoundException::new);
        return SessionUserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
