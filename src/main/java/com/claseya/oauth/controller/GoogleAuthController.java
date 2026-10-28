package com.claseya.oauth.controller;

import com.claseya.auth.dto.IssuedSession;
import com.claseya.auth.dto.LoginResponse;
import com.claseya.oauth.dto.GoogleLoginRequest;
import com.claseya.oauth.service.GoogleAuthService;
import com.claseya.security.AuthCookieFactory;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;
    private final AuthCookieFactory cookieFactory;

    public GoogleAuthController(GoogleAuthService googleAuthService, AuthCookieFactory cookieFactory) {
        this.googleAuthService = googleAuthService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/google")
    public LoginResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                         HttpServletResponse response) {
        IssuedSession session = googleAuthService.loginWithGoogle(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.session(session.token()).toString());
        return new LoginResponse(session.user(), session.expiresIn());
    }
}
