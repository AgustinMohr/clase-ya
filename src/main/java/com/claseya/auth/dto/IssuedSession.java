package com.claseya.auth.dto;

/**
 * Internal result of a successful authentication: the signed JWT is only used to build the
 * HttpOnly session cookie, never serialized to the client.
 */
public record IssuedSession(String token, SessionUserResponse user, long expiresIn) {
}
