package com.claseya.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP. Forwarded headers are only trusted when explicitly enabled, because a
 * directly exposed app could otherwise be trivially spoofed.
 */
@Component
public class ClientIpResolver {

    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (properties.isTrustForwardedHeaders()) {
            String forwarded = firstValid(forwardedFor(request.getHeader("Forwarded")));
            if (forwarded != null) {
                return forwarded;
            }
            String xForwardedFor = firstValid(firstSegment(request.getHeader("X-Forwarded-For")));
            if (xForwardedFor != null) {
                return xForwardedFor;
            }
        }
        return request.getRemoteAddr();
    }

    private String forwardedFor(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String element : header.split(",")) {
            for (String part : element.split(";")) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                    return stripQuotes(trimmed.substring(4).trim());
                }
            }
        }
        return null;
    }

    private String firstSegment(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        return comma < 0 ? header : header.substring(0, comma);
    }

    private String firstValid(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = normalize(raw.trim());
        return normalized == null ? null : normalized;
    }

    private String normalize(String value) {
        String candidate = stripQuotes(value);
        if (candidate.startsWith("[")) {
            int closing = candidate.indexOf(']');
            if (closing > 0) {
                candidate = candidate.substring(1, closing);
            }
        } else if (candidate.matches("^(\\d{1,3}\\.){3}\\d{1,3}:\\d+$")) {
            candidate = candidate.substring(0, candidate.lastIndexOf(':'));
        }
        return isIpLiteral(candidate) ? candidate : null;
    }

    private boolean isIpLiteral(String value) {
        if (value == null || value.isBlank() || value.length() > 45) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            return value.matches("[0-9a-fA-F:.%]+");
        }
        if (!value.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")) {
            return false;
        }
        for (String octet : value.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
