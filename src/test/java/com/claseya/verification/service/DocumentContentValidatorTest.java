package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.PayloadTooLargeException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** STOR-002: evidence is PDF-only, validated by content, with a header-safe filename. */
class DocumentContentValidatorTest {

    private final DocumentContentValidator validator = new DocumentContentValidator();

    private static byte[] pdf() {
        return "%PDF-1.7\ncontenido\n".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsAPdfAndDerivesItsMetadata() {
        DocumentContentValidator.ValidatedDocument document =
                validator.validate(pdf(), "Diploma UTN.pdf");

        assertThat(document.contentType()).isEqualTo("application/pdf");
        assertThat(document.filename()).isEqualTo("Diploma UTN.pdf");
        assertThat(document.sha256()).hasSize(64);
        assertThat(document.sizeBytes()).isEqualTo(pdf().length);
    }

    @Test
    void rejectsAnImage() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

        assertThatThrownBy(() -> validator.validate(png, "foto.png"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only PDF");
        assertThatThrownBy(() -> validator.validate(jpeg, "foto.jpg"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only PDF");
    }

    @Test
    void doesNotTrustTheExtension() {
        assertThatThrownBy(() -> validator.validate("hola".getBytes(StandardCharsets.UTF_8), "mentira.pdf"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsEmptyAndOversizedContent() {
        assertThatThrownBy(() -> validator.validate(new byte[0], "vacio.pdf"))
                .isInstanceOf(BadRequestException.class);

        byte[] oversized = new byte[(int) DocumentContentValidator.MAX_SIZE_BYTES + 1];
        System.arraycopy(new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}, 0, oversized, 0, 5);
        assertThatThrownBy(() -> validator.validate(oversized, "grande.pdf"))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void sanitizesTheFilenameForTheContentDispositionHeader() {
        String filename = validator.validate(pdf(), "..\\evil\"><script>\r\n.pdf").filename();

        assertThat(filename).doesNotContain("\"", "<", ">", "\r", "\n");
        assertThat(filename).startsWith("evil");
    }

    @Test
    void fallsBackToADefaultFilename() {
        assertThat(validator.validate(pdf(), "   ").filename()).isEqualTo("document.pdf");
        assertThat(validator.validate(pdf(), null).filename()).isEqualTo("document.pdf");
    }
}
