package com.claseya.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the HttpOnly session cookie and exposes the shared cookie attributes used by the CSRF
 * cookie. The session token is never readable by JavaScript (SEC-002 / ADR-020).
 *
 * <p>{@code auth.cookie.secure} must be {@code false} only for local HTTP development; production
 * must keep it {@code true}. {@code auth.cookie.same-site} is {@code Lax} for a same-site SPA/API
 * deployment and must be {@code None} (with Secure) when the API lives on another site.
 */
@Component
public class AuthCookieFactory {

    public static final String SESSION_COOKIE = "CLASEYA_SESSION";

    private final boolean secure;
    private final String sameSite;

    public AuthCookieFactory(@Value("${auth.cookie.secure:true}") boolean secure,
                             @Value("${auth.cookie.same-site:Lax}") String sameSite) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /** Session cookie: no Max-Age, so it ends with the browser session; the JWT exp still caps it. */
    public ResponseCookie session(String token) {
        return base(SESSION_COOKIE, token).build();
    }

    public ResponseCookie clearedSession() {
        return base(SESSION_COOKIE, "").maxAge(0).build();
    }

    public boolean isSecure() {
        return secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/");
    }
}
