package com.claseya.model.enums;

/**
 * How a credential was checked. It is recorded per decision; the MVP is human review and none
 * of these imply any automatic integration. `OTHER` requires a written detail.
 */
public enum VerificationMethod {
    INSTITUTION_CHECK,
    OFFICIAL_REGISTRY,
    DIGITAL_DOCUMENT_CHECK,
    DOCUMENT_ANALYSIS,
    OTHER
}
