package com.claseya.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated user's id from the {@code SecurityContext}.
 * Controllers must use this instead of trusting user-supplied ids.
 */
@Component
public class CurrentUser {

    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails user) {
            return user.getId();
        }
        throw new AccessDeniedException("Authenticated user required");
    }

    public AppUserDetails principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails user) {
            return user;
        }
        throw new AccessDeniedException("Authenticated user required");
    }
}
