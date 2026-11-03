package com.claseya.notification.service;

/** Delivery channel for the outbox worker. Implementations must throw on failure (retryable). */
public interface ContactNotificationSender {

    void send(String toEmail, String teacherName, String studentName, String link);
}
