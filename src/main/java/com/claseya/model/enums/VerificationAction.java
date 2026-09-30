package com.claseya.model.enums;

/** The decision an admin takes (recorded in the audit). Distinct from the resulting state. */
public enum VerificationAction {
    VERIFIED,
    REJECTED,
    MORE_INFO_REQUIRED,
    REVOKED
}
