package com.claseya.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stateless JWT authentication filter.
 *
 * <p>On a valid {@code Authorization: Bearer <token>} header it validates the token,
 * loads the account, and sets the {@code SecurityContext}. Invalid, expired, or
 * tampered tokens simply leave the request unauthenticated so Spring Security
 * handles access control. Disabled accounts are never authenticated.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                String userId = jwtService.extractUserId(token);
                AppUserDetails user = userDetailsService.loadUserById(UUID.fromString(userId));

                if (user.isEnabled() && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Invalid/expired token: leave unauthenticated, let Spring Security decide.
            }
        }

        filterChain.doFilter(request, response);
    }
}
