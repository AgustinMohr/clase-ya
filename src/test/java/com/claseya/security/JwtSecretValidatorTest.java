package com.claseya.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit coverage of the JWT secret rules (validation logic, no Spring context). */
class JwtSecretValidatorTest {

    private static final String VALID = "a-strong-and-unique-secret-with-more-than-32-bytes";

    @Test
    void validSecret_isAccepted() {
        assertDoesNotThrow(() -> JwtSecretValidator.validate(VALID, List.of()));
    }

    @Test
    void exactly32Bytes_isAccepted() {
        assertDoesNotThrow(() -> JwtSecretValidator.validate("0123456789abcdef0123456789abcdef", List.of()));
    }

    @Test
    void defaultSecret_isRejectedOutsideLocal() {
        assertThrows(IllegalStateException.class,
                () -> JwtSecretValidator.validate(JwtSecretValidator.DEV_DEFAULT_SECRET, List.of()));
    }

    @Test
    void shortSecret_isRejected() {
        assertThrows(IllegalStateException.class,
                () -> JwtSecretValidator.validate("too-short", List.of()));
    }

    @Test
    void blankOrNullSecret_isRejected() {
        assertThrows(IllegalStateException.class, () -> JwtSecretValidator.validate("   ", List.of()));
        assertThrows(IllegalStateException.class, () -> JwtSecretValidator.validate(null, List.of()));
    }

    @Test
    void localProfile_keepsTheDefaultAndShortSecretsWorking() {
        assertDoesNotThrow(() -> JwtSecretValidator.validate(JwtSecretValidator.DEV_DEFAULT_SECRET, List.of("local")));
        assertDoesNotThrow(() -> JwtSecretValidator.validate("short", List.of("local")));
    }
}
