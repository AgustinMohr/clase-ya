package com.claseya.common.exception;

/** A required external dependency (e.g. the mail transport) is not available. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
