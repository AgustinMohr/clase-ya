package com.claseya.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/**
 * Enforces the SEC-04 password policy. Blank values are left to {@code @NotBlank} so the client
 * receives a single, clear message per problem.
 */
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_CHARACTERS = 10;
    private static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return true;
        }
        if (password.codePointCount(0, password.length()) < MIN_CHARACTERS) {
            return false;
        }
        // BCrypt only uses the first 72 bytes; validating the encoded size avoids silent truncation.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int index = 0; index < password.length(); ) {
            int codePoint = password.codePointAt(index);
            hasLetter |= Character.isLetter(codePoint);
            hasDigit |= Character.isDigit(codePoint);
            index += Character.charCount(codePoint);
        }
        return hasLetter && hasDigit;
    }
}
