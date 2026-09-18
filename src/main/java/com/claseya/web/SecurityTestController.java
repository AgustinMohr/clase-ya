package com.claseya.web;

import com.claseya.security.AppUserDetails;
import com.claseya.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Temporary endpoints to validate the security setup. They will be removed in a
 * later phase once real modules (profiles, bookings, messaging, ...) exist.
 */
@RestController
@RequestMapping("/api/test")
public class SecurityTestController {

    private final CurrentUser currentUser;

    public SecurityTestController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/public")
    public Map<String, String> publicEndpoint() {
        return Map.of("message", "Public endpoint. No authentication required.");
    }

    @GetMapping("/authenticated")
    public Map<String, Object> authenticated() {
        return whoAmI();
    }

    @GetMapping("/student")
    public Map<String, Object> student() {
        return whoAmI();
    }

    @GetMapping("/teacher")
    public Map<String, Object> teacher() {
        return whoAmI();
    }

    @GetMapping("/admin")
    public Map<String, Object> admin() {
        return whoAmI();
    }

    private Map<String, Object> whoAmI() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AppUserDetails user = (AppUserDetails) authentication.getPrincipal();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Authenticated endpoint. Access granted.");
        body.put("userId", user.getId());
        body.put("email", user.getEmail());
        body.put("role", user.getRole().name());
        return body;
    }
}
