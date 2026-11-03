package com.claseya.auth.controller;

import com.claseya.auth.dto.AdminUserSummaryResponse;
import com.claseya.auth.service.AdminUserService;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.common.dto.SearchResultPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user directory (ADMIN-002). The route is already ADMIN-only in {@code SecurityConfig}
 * ({@code /api/admin/**}); the controller never decides who is allowed in.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserDirectoryController {

    private final AdminUserService adminUserService;

    public AdminUserDirectoryController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public SearchResultPage<AdminUserSummaryResponse> list(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.list(role, status, query, page, size);
    }
}
