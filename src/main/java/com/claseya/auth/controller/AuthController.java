package com.claseya.auth.controller;

import com.claseya.auth.dto.ActivateAccountRequest;
import com.claseya.auth.dto.CsrfResponse;
import com.claseya.auth.dto.IssuedSession;
import com.claseya.auth.dto.LoginRequest;
import com.claseya.auth.dto.LoginResponse;
import com.claseya.auth.dto.RegisterRequest;
import com.claseya.auth.dto.RegisterResponse;
import com.claseya.auth.dto.ResendActivationRequest;
import com.claseya.auth.dto.SessionUserResponse;
import com.claseya.auth.service.AuthService;
import com.claseya.security.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final AuthCookieFactory cookieFactory;

    public AuthController(AuthService authService, AuthCookieFactory cookieFactory) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
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

    /** Current session user. The SPA calls it on load because the JWT is not readable by JS. */
    @GetMapping("/me")
    public SessionUserResponse me() {
        return authService.me();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearedSession().toString());
        return ResponseEntity.noContent().build();
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
