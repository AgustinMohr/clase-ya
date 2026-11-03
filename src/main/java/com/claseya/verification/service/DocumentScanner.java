package com.claseya.verification.service;

import com.claseya.model.enums.DocumentScanStatus;

/** STOR-01: scans uploaded evidence before it is stored. */
public interface DocumentScanner {

    /** No-op when scanning is disabled; throws when the file is infected or the scanner is down. */
    void scan(byte[] content);

    /**
     * Status to persist for a document that passed {@link #scan(byte[])} (STOR-002): {@code CLEAN}
     * when a real scan ran, {@code UNSCANNED} when scanning is disabled. Keeping this decision in
     * the scanner is what stops the service from recording a status that never happened.
     */
    DocumentScanStatus recordedStatus();
}
