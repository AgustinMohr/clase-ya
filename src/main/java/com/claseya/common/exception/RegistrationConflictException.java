package com.claseya.common.exception;

/**
 * A concurrent registration claimed the same email. It is answered exactly like a successful
 * registration (202) so the status code never reveals whether an account exists (SEC-04).
 */
public class RegistrationConflictException extends RuntimeException {

    public RegistrationConflictException() {
        super("Registration could not be completed");
    }
}
