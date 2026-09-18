package com.claseya.oauth.controller;

import com.claseya.auth.dto.LoginResponse;
import com.claseya.oauth.dto.GoogleLoginRequest;
import com.claseya.oauth.service.GoogleAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;

    public GoogleAuthController(GoogleAuthService googleAuthService) {
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/google")
    public LoginResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return googleAuthService.loginWithGoogle(request);
    }
}
