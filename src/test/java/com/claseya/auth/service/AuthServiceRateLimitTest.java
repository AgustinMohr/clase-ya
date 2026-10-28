package com.claseya.auth.service;

import com.claseya.auth.dto.IssuedSession;
import com.claseya.auth.dto.LoginRequest;
import com.claseya.common.exception.InvalidCredentialsException;
import com.claseya.common.exception.RateLimitExceededException;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.security.AppUserDetails;
import com.claseya.security.CurrentUser;
import com.claseya.security.JwtService;
import com.claseya.security.ratelimit.LoginAttemptService;
import com.claseya.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRateLimitTest {

    private static final String EMAIL = "ana@example.com";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private CurrentUser currentUser;
    @InjectMocks private AuthService authService;

    @Test
    void blockedAccount_isRejectedBeforeBcrypt() {
        when(loginAttemptService.isBlocked(EMAIL)).thenReturn(true);
        when(loginAttemptService.blockedException(EMAIL))
                .thenReturn(new RateLimitExceededException(60));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "Password123")))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void authenticationFailure_recordsTheFailure() {
        when(loginAttemptService.isBlocked(EMAIL)).thenReturn(false);
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure(EMAIL);
    }

    @Test
    void successfulLogin_resetsTheAccountCounter() {
        User user = activeUser();
        AppUserDetails principal = new AppUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        when(loginAttemptService.isBlocked(EMAIL)).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.generateToken(principal)).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        IssuedSession response = authService.login(new LoginRequest(EMAIL, "Password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().id()).isEqualTo(user.getId());
        verify(loginAttemptService).reset(EMAIL);
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    private User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setPasswordHash("hash");
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
