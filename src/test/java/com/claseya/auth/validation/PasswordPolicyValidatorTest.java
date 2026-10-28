package com.claseya.auth.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void acceptsCompliantPasswords() {
        assertThat(validator.isValid("Password123", null)).isTrue();
        assertThat(validator.isValid("claveSegura2026", null)).isTrue();
        assertThat(validator.isValid("a1".repeat(5), null)).isTrue(); // exactly 10 characters
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(validator.isValid("Password1", null)).isFalse(); // 9
        assertThat(validator.isValid("a1a1a1a1a", null)).isFalse(); // 9
    }

    @Test
    void rejectsPasswordsWithoutLettersOrDigits() {
        assertThat(validator.isValid("passwordonly", null)).isFalse();
        assertThat(validator.isValid("1234567890", null)).isFalse();
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimit() {
        String ascii72 = "a1".repeat(36); // 72 bytes: accepted
        String ascii73 = "a1".repeat(36) + "a"; // 73 bytes
        String accentHeavy = "á".repeat(37) + "a1"; // 76 UTF-8 bytes, 39 characters

        assertThat(validator.isValid(ascii72, null)).isTrue();
        assertThat(validator.isValid(ascii73, null)).isFalse();
        assertThat(validator.isValid(accentHeavy, null)).isFalse();
    }

    @Test
    void leavesBlankValuesToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }
}
