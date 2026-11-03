package com.claseya.notification.service;

/**
 * Delivery channel for the account-activation link (AUTH-001) once the outbox worker picks the
 * event up. Lives in the notification module so the worker never depends on {@code auth}.
 */
public interface ActivationLinkSender {

    /**
     * Whether the configured transport can deliver an activation email at all. Registration asks
     * this before creating the account (RF-6): without transport it answers 503 and rolls back
     * instead of leaving an account nobody can activate.
     */
    boolean isConfigured();

    /** Throws on failure so the outbox retries with backoff. */
    void send(String toEmail, String activationUrl);
}
