package com.claseya.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * RT-08: only the trusted edge (Vercel) may reach the API. Vercel sets {@code X-Origin-Secret} on
 * every proxied request ({@code frontend/vercel.json}); a request that reaches the backend
 * directly cannot carry it, so it can no longer spoof {@code X-Forwarded-For} to evade the
 * per-IP rate limits.
 *
 * <p>Disabled when {@code app.deployment.origin-secret} ({@code ORIGIN_SECRET}) is blank, so local
 * development, tests and a staged rollout keep working. Platform health probes
 * ({@code /actuator/health/**}) are exempt: Render calls them directly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OriginSecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Origin-Secret";
    private static final Logger log = LoggerFactory.getLogger(OriginSecretFilter.class);

    private final byte[] expected;

    public OriginSecretFilter(@Value("${app.deployment.origin-secret:}") String secret) {
        this.expected = secret == null || secret.isBlank()
                ? null
                : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (expected == null) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/actuator/health") || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        boolean valid = provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            log.warn("Request without a valid origin secret rejected path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
