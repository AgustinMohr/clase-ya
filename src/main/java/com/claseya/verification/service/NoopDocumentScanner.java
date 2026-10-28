package com.claseya.verification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development/test mode: no antivirus service is required. */
@Component
@ConditionalOnProperty(name = "app.storage.scan.enabled", havingValue = "false", matchIfMissing = true)
public class NoopDocumentScanner implements DocumentScanner {

    @Override
    public void scan(byte[] content) {
        // Intentionally empty: enable app.storage.scan.enabled in production.
    }
}
