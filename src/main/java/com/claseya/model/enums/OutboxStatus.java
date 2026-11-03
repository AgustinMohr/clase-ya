package com.claseya.model.enums;

/**
 * Lifecycle of a row in {@code notification_outbox} (REVIEW2-002).
 * PENDING/FAILED are claimable; SENDING holds a lease; SENT and DEAD are terminal.
 */
public enum OutboxStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    DEAD
}
