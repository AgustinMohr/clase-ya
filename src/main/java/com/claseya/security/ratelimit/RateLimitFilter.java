package com.claseya.security.ratelimit;

import com.claseya.common.exception.ApiError;
import com.claseya.security.AppUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Applies RATE-001 limits to authentication, contact, messaging and evidence-upload requests.
 * It runs after the JWT filter so user-scoped buckets can use the authenticated id.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Pattern MESSAGE_PATH = Pattern.compile("^/api/conversations/[^/]+/messages$");
    private static final Pattern UPLOAD_PATH =
            Pattern.compile("^/api/teachers/me/education/[^/]+/documents$");

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitProperties properties,
                           RateLimitService rateLimitService,
                           ClientIpResolver clientIpResolver,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitScope scope = scopeFor(request);
        if (scope == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = identityFor(scope, request);
        if (identity == null) {
            // Authentication/authorization will reject it; no bucket needed.
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision;
        try {
            RateLimitPolicy policy = properties.policyFor(scope).toRateLimitPolicy();
            decision = rateLimitService.consume(scope, identity, policy);
        } catch (RuntimeException ex) {
            // Fail-open: the limiter is defense in depth, not a single point of failure.
            log.error("Rate limit evaluation failed; allowing request", ex);
            filterChain.doFilter(request, response);
            return;
        }

        if (!decision.allowed()) {
            log.warn("Rate limit exceeded scope={} path={} fingerprint={}",
                    scope, request.getRequestURI(),
                    rateLimitService.fingerprint(scope, identity));
            writeTooManyRequests(request, response, decision.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitScope scopeFor(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (path) {
            case "/api/auth/login" -> RateLimitScope.LOGIN_IP;
            case "/api/auth/register" -> RateLimitScope.REGISTER_IP;
            case "/api/auth/google" -> RateLimitScope.GOOGLE_IP;
            case "/api/conversations" -> RateLimitScope.CONTACT_USER;
            default -> {
                if (MESSAGE_PATH.matcher(path).matches()) {
                    yield RateLimitScope.MESSAGE_USER;
                }
                if (UPLOAD_PATH.matcher(path).matches()) {
                    yield RateLimitScope.UPLOAD_USER;
                }
                yield null;
            }
        };
    }

    private String identityFor(RateLimitScope scope, HttpServletRequest request) {
        if (!scope.isUserScoped()) {
            return clientIpResolver.resolve(request);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserDetails user
                && user.isEnabled()) {
            return user.getId().toString();
        }
        return null;
    }

    private void writeTooManyRequests(HttpServletRequest request,
                                      HttpServletResponse response,
                                      long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getWriter(), ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                "Too many requests. Try again later.",
                request.getRequestURI()));
    }
}

