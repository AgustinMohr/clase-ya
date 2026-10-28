package com.claseya.auth.dto;

/** CSRF token handed to the SPA; the cookie that validates it stays HttpOnly. */
public record CsrfResponse(String token) {
}
