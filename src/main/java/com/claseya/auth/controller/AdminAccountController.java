package com.claseya.auth.controller;

import com.claseya.auth.service.AccountActivationService;
import com.claseya.auth.service.AdminUserService;
import com.claseya.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ADMIN escape hatch for AUTH-001 (activate a pending account by hand) and account suspension
 * (ADMIN-002). The route is already ADMIN-only in {@code SecurityConfig}
 * ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminAccountController {

    private final AccountActivationService accountActivationService;
    private final AdminUserService adminUserService;
    private final CurrentUser currentUser;

    public AdminAccountController(AccountActivationService accountActivationService,
                                  AdminUserService adminUserService,
                                  CurrentUser currentUser) {
        this.accountActivationService = accountActivationService;
        this.adminUserService = adminUserService;
        this.currentUser = currentUser;
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID userId) {
        accountActivationService.activateManually(userId);
        return ResponseEntity.noContent().build();
    }

    /** ACTIVE → SUSPENDED. Anonymous and non-admin callers never reach this controller. */
    @PostMapping("/{userId}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable UUID userId) {
        adminUserService.suspend(currentUser.id(), userId);
        return ResponseEntity.noContent().build();
    }

    /** SUSPENDED → ACTIVE. */
    @PostMapping("/{userId}/restore")
    public ResponseEntity<Void> restore(@PathVariable UUID userId) {
        adminUserService.restore(currentUser.id(), userId);
        return ResponseEntity.noContent().build();
    }
}
