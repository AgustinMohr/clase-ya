package com.claseya.auth.controller;

import com.claseya.auth.dto.ActivateAccountRequest;
import com.claseya.auth.dto.CsrfResponse;
import com.claseya.auth.dto.ForgotPasswordRequest;
import com.claseya.auth.dto.IssuedSession;
import com.claseya.auth.dto.LoginRequest;
import com.claseya.auth.dto.LoginResponse;
import com.claseya.auth.dto.MessageLinkRequest;
import com.claseya.auth.dto.RegisterRequest;
import com.claseya.auth.dto.ResendActivationRequest;
import com.claseya.auth.dto.ResetPasswordRequest;
import com.claseya.auth.dto.SessionUserResponse;
import com.claseya.auth.service.AuthService;
import com.claseya.auth.service.MessageLinkAuthService;
import com.claseya.auth.service.PasswordResetService;
import com.claseya.auth.service.SessionService;
import com.claseya.security.AppUserDetails;
import com.claseya.security.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final MessageLinkAuthService messageLinkAuthService;
    private final PasswordResetService passwordResetService;
    private final SessionService sessionService;
    private final AuthCookieFactory cookieFactory;

    public AuthController(AuthService authService,
                          MessageLinkAuthService messageLinkAuthService,
                          PasswordResetService passwordResetService,
                          SessionService sessionService,
                          AuthCookieFactory cookieFactory) {
        this.authService = authService;
        this.messageLinkAuthService = messageLinkAuthService;
        this.passwordResetService = passwordResetService;
        this.sessionService = sessionService;
        this.cookieFactory = cookieFactory;
    }

    /**
     * SEC-04: always 202, with no account data. The caller cannot tell whether the email was new,
     * pending (a new link is sent) or already active.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        IssuedSession session = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.session(session.token()).toString());
        return new LoginResponse(session.user(), session.expiresIn());
    }

    /** Consumes the single-use link sent by email (AUTH-001). */
    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateAccountRequest request) {
        authService.activate(request.token());
        return ResponseEntity.noContent().build();
    }

    /** Always 202: the response must not reveal whether the email is registered (AUTH-001). */
    @PostMapping("/resend-activation")
    public ResponseEntity<Void> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        authService.resendActivation(request.email());
        return ResponseEntity.accepted().build();
    }

    /** Always 202: the response must not reveal whether the email is registered (AUTH-003). */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.request(request.email());
        return ResponseEntity.accepted().build();
    }

    /** Consumes the single-use reset token (AUTH-003). */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** Current session user. The SPA calls it on load because the JWT is not readable by JS. */
    @GetMapping("/me")
    public SessionUserResponse me() {
        return authService.me();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {
        // AUTH-004: logout is not just "delete the cookie"; older tokens stop working too.
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails user) {
            sessionService.invalidateAllSessions(user.getId());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearedSession().toString());
        return ResponseEntity.noContent().build();
    }

    /**
     * REVIEW2-002: exchanges the single-use link from the contact email for a normal session
     * cookie. Same generic error for expired, used, unknown or disabled accounts.
     */
    @PostMapping("/message-link")
    public LoginResponse messageLink(@Valid @RequestBody MessageLinkRequest request,
                                     HttpServletResponse response) {
        IssuedSession session = messageLinkAuthService.exchange(request.token());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.session(session.token()).toString());
        return new LoginResponse(session.user(), session.expiresIn());
    }

    /**
     * Hands the CSRF token to the SPA. The validating cookie is HttpOnly; the body token is only
     * readable by allowed CORS origins.
     */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }
}
