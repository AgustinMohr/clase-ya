package com.claseya.model.enums;

/** STOR-01 / STOR-002: antivirus result for verification evidence. */
public enum DocumentScanStatus {
    PENDING,
    CLEAN,
    INFECTED,
    /** Scanning is disabled in this deployment; the file was never checked. */
    UNSCANNED
}
