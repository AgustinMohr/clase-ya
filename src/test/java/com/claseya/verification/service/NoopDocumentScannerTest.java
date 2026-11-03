package com.claseya.verification.service;

import com.claseya.model.enums.DocumentScanStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** STOR-002: the debug scanner must not pretend the evidence was checked. */
class NoopDocumentScannerTest {

    @Test
    void acceptsEverythingAndRecordsUnscanned() {
        NoopDocumentScanner scanner = new NoopDocumentScanner();

        assertThatCode(() -> scanner.scan(new byte[]{1, 2, 3})).doesNotThrowAnyException();
        assertThat(scanner.recordedStatus()).isEqualTo(DocumentScanStatus.UNSCANNED);
    }
}
