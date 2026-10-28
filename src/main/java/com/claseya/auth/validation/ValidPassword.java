package com.claseya.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password policy for new accounts (SEC-04): at least 10 characters, at least one letter and one
 * digit, and at most 72 UTF-8 bytes (BCrypt silently truncates beyond that).
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.TYPE_USE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Password must be at least 10 characters, include letters and numbers, "
            + "and be at most 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
