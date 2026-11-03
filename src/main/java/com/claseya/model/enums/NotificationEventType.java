package com.claseya.model.enums;

/** Events the transactional outbox can deliver (REVIEW2-002). */
public enum NotificationEventType {
    /** A student contacted a teacher for the first time in a conversation. */
    CONTACT_REQUEST,
    /** A pending account must receive its single-use activation link (AUTH-001). */
    ACTIVATION
}
