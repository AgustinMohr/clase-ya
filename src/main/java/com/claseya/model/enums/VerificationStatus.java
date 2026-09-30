package com.claseya.model.enums;

/**
 * Verification state of a credential and the aggregate state of a teacher profile.
 * Only a human ADMIN decision moves an item into VERIFIED/REJECTED/MORE_INFO_REQUIRED.
 */
public enum VerificationStatus {
    PENDING,
    UNDER_REVIEW,
    VERIFIED,
    REJECTED,
    MORE_INFO_REQUIRED
}
