package com.claseya.notification.service;

/** Test double that records the last message handed to the transport. */
final class CapturingEmailSender implements EmailSender {

    private final boolean configured;
    String to;
    String subject;
    String text;

    CapturingEmailSender(boolean configured) {
        this.configured = configured;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void send(String toEmail, String subject, String text) {
        this.to = toEmail;
        this.subject = subject;
        this.text = text;
    }
}
