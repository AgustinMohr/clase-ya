package com.claseya.notification.service;

/**
 * A delivery failure that retrying cannot fix (no transport configured, recipient gone).
 * The worker moves the row to DEAD instead of burning five attempts.
 */
public class PermanentNotificationException extends RuntimeException {

    public PermanentNotificationException(String message) {
        super(message);
    }
}
