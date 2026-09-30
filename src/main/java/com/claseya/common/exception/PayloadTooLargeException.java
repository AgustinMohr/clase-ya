package com.claseya.common.exception;

/**
 * Thrown when an uploaded document exceeds the allowed size. Mapped to 413.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
