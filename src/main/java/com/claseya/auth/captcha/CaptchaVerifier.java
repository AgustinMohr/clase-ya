package com.claseya.auth.captcha;

/** Verifies the anti-abuse challenge attached to a registration (SEC-04). */
public interface CaptchaVerifier {

    void verify(String token);
}
