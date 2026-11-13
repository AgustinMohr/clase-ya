package com.claseya.notification.service;

/**
 * Transport-agnostic email delivery (NOTIF-001).
 *
 * <p>Implementations are selected with {@code app.notification.email.transport}: SMTP for hosts
 * that allow outbound port 587, or an HTTPS API for hosts that block it (Render Free blocks
 * 25/465/587). The activation and contact senders compose the message and delegate here, so the
 * outbox worker never knows which transport is active.
 */
public interface EmailSender {

    /** True when the selected transport has everything it needs to attempt a delivery. */
    boolean isConfigured();

    /**
     * Sends a plain-text email.
     *
     * @throws PermanentNotificationException  when retrying cannot fix the failure
     * @throws NotificationDeliveryException   when the failure is transient
     */
    void send(String toEmail, String subject, String text);

    /**
     * Sends an email with an optional HTML alternative. The default keeps test doubles and
     * custom transports working with the plain-text contract.
     */
    default void send(String toEmail, EmailContent content) {
        send(toEmail, content.subject(), content.text());
    }
}
