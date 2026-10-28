package com.claseya.security.ratelimit;

/** The abuse-protection scopes defined by RATE-001. */
public enum RateLimitScope {

    LOGIN_IP(false),
    LOGIN_ACCOUNT(false),
    REGISTER_IP(false),
    GOOGLE_IP(false),
    CONTACT_USER(true),
    MESSAGE_USER(true),
    UPLOAD_USER(true);

    private final boolean userScoped;

    RateLimitScope(boolean userScoped) {
        this.userScoped = userScoped;
    }

    /** True when the bucket key is the authenticated user id instead of the client IP. */
    public boolean isUserScoped() {
        return userScoped;
    }
}
