package com.claseya.auth.dto;

/**
 * Public session response. The access token is delivered in an HttpOnly cookie, so it is never
 * part of the JSON body (SEC-002).
 */
public record LoginResponse(SessionUserResponse user, long expiresIn) {
}
