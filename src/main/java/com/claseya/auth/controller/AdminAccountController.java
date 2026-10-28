package com.claseya.auth.controller;

import com.claseya.auth.service.AccountActivationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ADMIN escape hatch for AUTH-001: activates a pending account by hand when email delivery is not
 * available. It never marks the email as verified. The route is already ADMIN-only in
 * {@code SecurityConfig} ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminAccountController {

    private final AccountActivationService accountActivationService;

    public AdminAccountController(AccountActivationService accountActivationService) {
        this.accountActivationService = accountActivationService;
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID userId) {
        accountActivationService.activateManually(userId);
        return ResponseEntity.noContent().build();
    }
}
