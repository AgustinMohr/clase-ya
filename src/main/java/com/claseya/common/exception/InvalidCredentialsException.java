package com.claseya.common.exception;

/**
 * Thrown on any failed authentication attempt (bad credentials, unknown account,
 * or disabled account). Mapped to a generic 401 to avoid account enumeration.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
