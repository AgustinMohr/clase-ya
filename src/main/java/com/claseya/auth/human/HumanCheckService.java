package com.claseya.auth.human;

import com.claseya.auth.captcha.CaptchaVerifier;
import com.claseya.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class HumanCheckService {

    private final CaptchaVerifier captchaVerifier;
    private final JwtService jwtService;
    private final boolean required;
    private final Duration ttl;

    public HumanCheckService(CaptchaVerifier captchaVerifier,
                             JwtService jwtService,
                             @Value("${app.auth.captcha.enabled:false}") boolean required,
                             @Value("${app.human-check.ttl:PT720H}") Duration ttl) {
        this.captchaVerifier = captchaVerifier;
        this.jwtService = jwtService;
        this.required = required;
        this.ttl = ttl;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isVerified(String cookie) {
        return !required || (cookie != null && !cookie.isBlank() && jwtService.isHumanCheckToken(cookie));
    }

    public String verify(String token) {
        captchaVerifier.verify(token);
        return jwtService.generateHumanCheckToken(ttl);
    }

    public Duration ttl() {
        return ttl;
    }
}
