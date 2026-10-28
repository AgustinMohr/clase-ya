package com.claseya.verification.service;

/** STOR-01: scans uploaded evidence before it is stored. */
public interface DocumentScanner {

    /** No-op when scanning is disabled; throws when the file is infected or the scanner is down. */
    void scan(byte[] content);
}
