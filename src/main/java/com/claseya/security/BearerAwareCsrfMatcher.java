package com.claseya.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * CSRF applies to cookie-authenticated unsafe methods. Requests carrying a Bearer token are not
 * vulnerable to CSRF (a cross-site page cannot set that header), so API clients keep working.
 */
public class BearerAwareCsrfMatcher implements RequestMatcher {

    @Override
    public boolean matches(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String method = request.getMethod();
        return !("GET".equals(method) || "HEAD".equals(method) || "TRACE".equals(method) || "OPTIONS".equals(method));
    }
}
