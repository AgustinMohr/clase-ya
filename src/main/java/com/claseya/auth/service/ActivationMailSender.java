package com.claseya.auth.service;

/** Delivers the single-use account-activation link (AUTH-001). */
public interface ActivationMailSender {

    void sendActivation(String toEmail, String activationUrl);
}
