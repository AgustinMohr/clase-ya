package com.claseya.notification.service;

/** A transient delivery failure: the outbox retries it with exponential backoff (RF-3). */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
